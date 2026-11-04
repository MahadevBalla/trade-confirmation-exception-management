#!/usr/bin/env bash
# ==============================================================================
# scripts/seed-data.sh
# Triggers synthetic trade generation and seed loading into the ingestion service.
# Usage:
#   ./scripts/seed-data.sh               # Loads bundled 500-trade seed dataset
#   ./scripts/seed-data.sh --generate 50 # Generates 50 random synthetic trades
# ==============================================================================
set -euo pipefail

INGESTION_URL="${INGESTION_URL:-http://localhost:8081}"
AUTH_OPS="ops:${TCEM_OPS_PASSWORD:-ops-demo}"

if [ "${1:-}" = "--generate" ]; then
  COUNT="${2:-20}"
  echo "=== [seed-data] Generating $COUNT synthetic trades (publishing to Kafka) ==="
  curl -s -u "$AUTH_OPS" -X POST "$INGESTION_URL/trades/generate?count=$COUNT&publishToKafka=true" | jq . 2>/dev/null || curl -s -u "$AUTH_OPS" -X POST "$INGESTION_URL/trades/generate?count=$COUNT&publishToKafka=true"
else
  echo "=== [seed-data] Loading bundled 500-trade seed dataset (sample-trades.json) ==="
  curl -s -u "$AUTH_OPS" -X POST "$INGESTION_URL/trades/seed?publishToKafka=true" | jq . 2>/dev/null || curl -s -u "$AUTH_OPS" -X POST "$INGESTION_URL/trades/seed?publishToKafka=true"
fi
echo ""
