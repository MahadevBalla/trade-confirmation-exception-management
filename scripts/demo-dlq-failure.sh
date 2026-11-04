#!/usr/bin/env bash
# ==============================================================================
# scripts/demo-dlq-failure.sh
# Creates a real, replayable entry in the Dead-Letter Queue, for demonstrating replay.
# It removes the database validation-service records processed messages in, sends a
# trade so that validation fails on it, then restores the database. The entry then
# replays successfully. Requires a running stack, and leaves it healthy.
# Usage: ./scripts/demo-dlq-failure.sh
# ==============================================================================
set -euo pipefail

INGESTION_URL="${INGESTION_URL:-http://localhost:8081}"
WORKITEM_URL="${WORKITEM_URL:-http://localhost:8085}"
COUCHDB_HOST_URL="${COUCHDB_HOST_URL:-http://localhost:5984}"
COUCHDB_AUTH="${COUCHDB_USER:-admin}:${COUCHDB_PASSWORD:-password}"
AUTH_OPS="ops:${TCEM_OPS_PASSWORD:-ops-demo}"

command -v jq > /dev/null || { echo "jq is required"; exit 2; }

restore_db() { curl -s -u "$COUCHDB_AUTH" -X PUT "$COUCHDB_HOST_URL/validation-idempotency" > /dev/null; }
trap restore_db EXIT

curl -fsS -o /dev/null -u "$COUCHDB_AUTH" -X DELETE "$COUCHDB_HOST_URL/validation-idempotency"

SEQUENCE=$(( 1000 + $(date +%s) % 900000 ))
TRADE=$(curl -fsS -u "$AUTH_OPS" -X POST "$INGESTION_URL/trades/generate/scenario?scenario=VALID_STANDARD_STP&sequence=$SEQUENCE&publishToKafka=true" | jq -r .tradePayload.trade.tradeId)
echo "Published $TRADE; waiting for it to reach the Dead-Letter Queue..."

for _ in $(seq 1 30); do
  ENTRY=$(curl -fsS "$WORKITEM_URL/dlq?status=PENDING_REPLAY&size=100" | jq -r --arg t "$TRADE" '[.content[] | select(.tradeId == $t)][0]._id // empty')
  if [ -n "$ENTRY" ]; then
    restore_db
    echo "Ready: $ENTRY for $TRADE can now be replayed from the Dead-Letter Queue screen."
    exit 0
  fi
  sleep 1
done
echo "$TRADE did not reach the Dead-Letter Queue within 30 seconds." >&2
exit 1
