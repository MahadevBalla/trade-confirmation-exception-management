#!/usr/bin/env bash
# ==============================================================================
# scripts/pipeline-test.sh
# End-to-end checks of the event pipeline against a running full stack.
# Fails if events stop flowing between services, which unit tests cannot see.
# Usage (stack must be up, for example ./scripts/dev-up.sh --all):
#   ./scripts/pipeline-test.sh
# Temporarily deletes the validation-idempotency database to cause a real failure.
# ==============================================================================
set -uo pipefail

INGESTION_URL="${INGESTION_URL:-http://localhost:8081}"
VALIDATION_URL="${VALIDATION_URL:-http://localhost:8083}"
EXCEPTION_URL="${EXCEPTION_URL:-http://localhost:8084}"
WORKITEM_URL="${WORKITEM_URL:-http://localhost:8085}"
COUCHDB_HOST_URL="${COUCHDB_HOST_URL:-http://localhost:5984}"
COUCHDB_AUTH="${COUCHDB_USER:-admin}:${COUCHDB_PASSWORD:-password}"
TIMEOUT_SECONDS="${PIPELINE_TIMEOUT_SECONDS:-60}"
AUTH_OPS="ops:${TCEM_OPS_PASSWORD:-ops-demo}"
AUTH_SUPERVISOR="supervisor:${TCEM_SUPERVISOR_PASSWORD:-supervisor-demo}"
AUTH_ADMIN="admin:${TCEM_ADMIN_PASSWORD:-admin-demo}"

command -v jq > /dev/null || { echo "jq is required"; exit 2; }

PASS=0
FAIL=0
pass() { echo "  [PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "  [FAIL] $1"; FAIL=$((FAIL + 1)); }

# wait_until <description> <command>: passes as soon as the command succeeds.
wait_until() {
  local description="$1"; shift
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if "$@" > /dev/null 2>&1; then pass "$description"; return 0; fi
    sleep 1
  done
  fail "$description (not within ${TIMEOUT_SECONDS}s)"
  return 1
}

generate() { # <scenario> <sequence> <publish>: prints the generated message
  curl -fsS -u "$AUTH_OPS" -X POST "$INGESTION_URL/trades/generate/scenario?scenario=$1&sequence=$2&publishToKafka=$3" | jq -c .tradePayload
}
publish_raw() { # <key> <json>
  printf '%s\t%s\n' "$1" "$2" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 --topic trade.raw --property parse.key=true --property "key.separator=$(printf '\t')"
}
confirmation_exists() { curl -fs -o /dev/null "$VALIDATION_URL/trades/$1/confirmation"; }
has_exception() { # <trade> <subTheme>
  curl -fsS "$EXCEPTION_URL/exceptions?tradeId=$1" | jq -e --arg s "$2" '[.exceptions[] | select(.subTheme == $s)] | length > 0'
}
open_item_count() { curl -fsS "$WORKITEM_URL/workitems?tradeId=$1&size=100" | jq '[.content[] | select(.status == "OPEN" or .status == "IN_PROGRESS")] | length'; }
has_open_item() { [ "$(open_item_count "$1")" -gt 0 ]; }
trade_status() { # <trade> <status>
  curl -fsS "$INGESTION_URL/trades/$1" | jq -e --arg s "$2" '.trade.processingStatus == $s'
}
trade_is_enriched() { curl -fsS "$INGESTION_URL/trades/$1" | jq -e '[.trade.canonicalTrade.parties[].legalEntityName // empty] | length > 0'; }
open_review_item() { curl -fsS "$WORKITEM_URL/workitems?tradeId=$1&type=MANUAL_REVIEW&status=OPEN" | jq -er '.content[0].workItemId'; }
all_items_cancelled() { # every item was cancelled, and there was at least one
  curl -fsS "$WORKITEM_URL/workitems?tradeId=$1&size=100" | jq -e '(.content | length > 0) and all(.content[]; .status == "CANCELLED")'
}
dlq_status() { curl -fsS "$WORKITEM_URL/dlq/$1" | jq -e --arg s "$2" '.dlqStatus == $s'; }
pending_dlq_entry() { curl -fsS "$WORKITEM_URL/dlq?status=PENDING_REPLAY&size=200" | jq -er --arg t "$1" '[.content[] | select(.tradeId == $t)][0]._id'; }
restore_db() { curl -s -u "$COUCHDB_AUTH" -X PUT "$COUCHDB_HOST_URL/validation-idempotency" > /dev/null; }

trap restore_db EXIT

# Unique ids per run.
BASE=$(( ($(date +%s) % 200000) * 4 ))

echo "1. Valid trade flows through to a confirmation"
TRADE=$(generate VALID_STANDARD_STP $((BASE + 1)) true | jq -r .trade.tradeId)
wait_until "$TRADE is confirmed" confirmation_exists "$TRADE"
[ "$(open_item_count "$TRADE")" = 0 ] && pass "$TRADE has no open work items" || fail "$TRADE has no open work items"
wait_until "$TRADE shows CONFIRMED in the read model" trade_status "$TRADE" CONFIRMED
wait_until "$TRADE carries enriched party data" trade_is_enriched "$TRADE"

echo "2. Missing economics reach STP and raise an exception and a work item"
MISSING=$(generate MISSING_ECONOMIC_STRIKE $((BASE + 5)) true | jq -r .trade.tradeId)
wait_until "$MISSING raises MISSING_STRIKE" has_exception "$MISSING" MISSING_STRIKE
wait_until "$MISSING has an open work item" has_open_item "$MISSING"
held=$(curl -s -o /dev/null -w '%{http_code}' -u "$COUCHDB_AUTH" "$COUCHDB_HOST_URL/confirmations/held-trade-$MISSING")
[ "$held" = 200 ] && pass "$MISSING went through enrichment and STP (held by validation-service)" || fail "$MISSING went through enrichment and STP (held by validation-service)"

echo "3. Approving a manual review generates the confirmation"
REVIEW=$(generate BUSINESS_RULE_NON_STP $((BASE + 3)) true | jq -r .trade.tradeId)
wait_until "$REVIEW reaches the manual review queue on its own" open_review_item "$REVIEW"
wait_until "$REVIEW shows MANUAL_REVIEW in the read model" trade_status "$REVIEW" MANUAL_REVIEW
if confirmation_exists "$REVIEW"; then fail "$REVIEW is unconfirmed before approval"; else pass "$REVIEW is unconfirmed before approval"; fi
WORK_ITEM=$(open_review_item "$REVIEW")
curl -fsS -o /dev/null -u "$AUTH_SUPERVISOR" -X POST "$WORKITEM_URL/workitems/$WORK_ITEM/review" -H 'Content-Type: application/json' \
  -d '{"decision":"APPROVE","notes":"Pipeline test","reviewer":"ci"}' || fail "review request for $WORK_ITEM"
wait_until "$REVIEW is confirmed after approval" confirmation_exists "$REVIEW"
wait_until "$REVIEW shows CONFIRMED in the read model" trade_status "$REVIEW" CONFIRMED

echo "4. A real failure can be replayed from the DLQ"
curl -fsS -o /dev/null -u "$COUCHDB_AUTH" -X DELETE "$COUCHDB_HOST_URL/validation-idempotency" || fail "delete validation-idempotency"
FAILED=$(generate VALID_STANDARD_STP $((BASE + 9)) true | jq -r .trade.tradeId)
wait_until "$FAILED is dead-lettered, keyed by trade id" pending_dlq_entry "$FAILED"
ENTRY=$(pending_dlq_entry "$FAILED")
restore_db
if [ -n "$ENTRY" ]; then
  curl -fsS -o /dev/null -u "$AUTH_ADMIN" -X POST "$WORKITEM_URL/dlq/$ENTRY/replay" || fail "replay request for $ENTRY"
  wait_until "$ENTRY reaches REPLAY_SUCCEEDED" dlq_status "$ENTRY" REPLAY_SUCCEEDED
fi

echo "5. Cancelling a trade cancels its open work items"
CANCEL_MESSAGE=$(generate MISSING_ECONOMIC_STRIKE $((BASE + 5)) false \
  | jq -c --arg id "MSG-CANCEL-$BASE" '.messageHeader.eventType = "CANCEL_TRADE" | .messageHeader.messageId = $id')
publish_raw "$MISSING" "$CANCEL_MESSAGE" || fail "publish CANCEL_TRADE for $MISSING"
wait_until "$MISSING has all its work items cancelled" all_items_cancelled "$MISSING"
if confirmation_exists "$MISSING"; then fail "$MISSING was not confirmed by the cancel"; else pass "$MISSING was not confirmed by the cancel"; fi
wait_until "$MISSING shows CANCELLED in the read model" trade_status "$MISSING" CANCELLED

echo "6. Changes need the right role"
status_of() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
[ "$(status_of -X POST "$WORKITEM_URL/dlq/x/replay")" = 401 ] && pass "replay without signing in is refused" || fail "replay without signing in is refused"
[ "$(status_of -u "$AUTH_OPS" -X POST "$WORKITEM_URL/workitems/x/review" -H 'Content-Type: application/json' -d '{}')" = 403 ] && pass "ops cannot decide a review" || fail "ops cannot decide a review"
[ "$(status_of -u "$AUTH_SUPERVISOR" -X POST "$WORKITEM_URL/dlq/x/replay")" = 403 ] && pass "supervisor cannot replay" || fail "supervisor cannot replay"
reviewer_recorded() { curl -fsS "$WORKITEM_URL/workitems/$WORK_ITEM" | jq -e '.reviewedBy == "supervisor"'; }
wait_until "the review records the signed-in user" reviewer_recorded

echo "7. The audit trail spans services"
audit_has_trade() { curl -fsS "$INGESTION_URL/audit?size=200" | jq -e --arg t "$1" '[.content[] | select(.tradeId == $t) | .sourceService] | unique | length >= 2'; }
wait_until "$MISSING appears in the global audit trail from at least two services" audit_has_trade "$MISSING"

echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "PIPELINE TEST PASSED ($PASS checks)"
  exit 0
fi
echo "PIPELINE TEST FAILED ($FAIL failed, $PASS passed)"
exit 1
