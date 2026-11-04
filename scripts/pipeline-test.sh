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
  curl -fsS -X POST "$INGESTION_URL/trades/generate/scenario?scenario=$1&sequence=$2&publishToKafka=$3" | jq -c .tradePayload
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
all_items_cancelled() { # every item was cancelled, and there was at least one
  curl -fsS "$WORKITEM_URL/workitems?tradeId=$1&size=100" | jq -e '(.content | length > 0) and all(.content[]; .status == "CANCELLED")'
}
dlq_status() { curl -fsS "$WORKITEM_URL/dlq/$1" | jq -e --arg s "$2" '.dlqStatus == $s'; }
pending_dlq_entry() { curl -fsS "$WORKITEM_URL/dlq?status=PENDING_REPLAY&size=200" | jq -er --arg t "$1" '[.content[] | select(.tradeId == $t)][0]._id'; }
restore_db() { curl -s -u "$COUCHDB_AUTH" -X PUT "$COUCHDB_HOST_URL/validation-idempotency" > /dev/null; }

trap restore_db EXIT

# Unique ids per run. Generated counterparties cycle every 4 sequence numbers: base+1 is STP-eligible and
# base+3 is not, so the outcomes below are deterministic.
BASE=$(( ($(date +%s) % 200000) * 4 ))

echo "1. Valid trade flows through to a confirmation"
TRADE=$(generate VALID_STANDARD_STP $((BASE + 1)) true | jq -r .trade.tradeId)
wait_until "$TRADE is confirmed" confirmation_exists "$TRADE"
[ "$(open_item_count "$TRADE")" = 0 ] && pass "$TRADE has no open work items" || fail "$TRADE has no open work items"

echo "2. Missing economics reach STP and raise an exception and a work item"
MISSING=$(generate MISSING_ECONOMIC_STRIKE $((BASE + 5)) true | jq -r .trade.tradeId)
wait_until "$MISSING raises MISSING_STRIKE" has_exception "$MISSING" MISSING_STRIKE
wait_until "$MISSING has an open work item" has_open_item "$MISSING"
held=$(curl -s -o /dev/null -w '%{http_code}' -u "$COUCHDB_AUTH" "$COUCHDB_HOST_URL/confirmations/held-trade-$MISSING")
[ "$held" = 200 ] && pass "$MISSING went through enrichment and STP (held by validation-service)" || fail "$MISSING went through enrichment and STP (held by validation-service)"

echo "3. Approving a manual review generates the confirmation"
REVIEW=$(generate BUSINESS_RULE_NON_STP $((BASE + 3)) true | jq -r .trade.tradeId)
wait_until "$REVIEW has an open work item after failing STP" has_open_item "$REVIEW"
if confirmation_exists "$REVIEW"; then fail "$REVIEW is unconfirmed before approval"; else pass "$REVIEW is unconfirmed before approval"; fi
WORK_ITEM=$(curl -fsS -X POST "$WORKITEM_URL/workitems" -H 'Content-Type: application/json' \
  -d "{\"tradeId\":\"$REVIEW\",\"type\":\"MANUAL_REVIEW\",\"title\":\"Pipeline test\",\"description\":\"Pipeline test\",\"ownerQueue\":\"OPERATIONS_REVIEW\",\"priority\":\"MEDIUM\"}" | jq -r .workItemId)
curl -fsS -o /dev/null -X POST "$WORKITEM_URL/workitems/$WORK_ITEM/review" -H 'Content-Type: application/json' \
  -d '{"decision":"APPROVE","notes":"Pipeline test","reviewer":"ci"}' || fail "review request for $WORK_ITEM"
wait_until "$REVIEW is confirmed after approval" confirmation_exists "$REVIEW"

echo "4. A real failure can be replayed from the DLQ"
curl -fsS -o /dev/null -u "$COUCHDB_AUTH" -X DELETE "$COUCHDB_HOST_URL/validation-idempotency" || fail "delete validation-idempotency"
FAILED=$(generate VALID_STANDARD_STP $((BASE + 9)) true | jq -r .trade.tradeId)
wait_until "$FAILED is dead-lettered, keyed by trade id" pending_dlq_entry "$FAILED"
ENTRY=$(pending_dlq_entry "$FAILED")
restore_db
if [ -n "$ENTRY" ]; then
  curl -fsS -o /dev/null -X POST "$WORKITEM_URL/dlq/$ENTRY/replay" || fail "replay request for $ENTRY"
  wait_until "$ENTRY reaches REPLAY_SUCCEEDED" dlq_status "$ENTRY" REPLAY_SUCCEEDED
fi

echo "5. Cancelling a trade cancels its open work items"
CANCEL_MESSAGE=$(generate MISSING_ECONOMIC_STRIKE $((BASE + 5)) false \
  | jq -c --arg id "MSG-CANCEL-$BASE" '.messageHeader.eventType = "CANCEL_TRADE" | .messageHeader.messageId = $id')
publish_raw "$MISSING" "$CANCEL_MESSAGE" || fail "publish CANCEL_TRADE for $MISSING"
wait_until "$MISSING has all its work items cancelled" all_items_cancelled "$MISSING"
if confirmation_exists "$MISSING"; then fail "$MISSING was not confirmed by the cancel"; else pass "$MISSING was not confirmed by the cancel"; fi

echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "PIPELINE TEST PASSED ($PASS checks)"
  exit 0
fi
echo "PIPELINE TEST FAILED ($FAIL failed, $PASS passed)"
exit 1
