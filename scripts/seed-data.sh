#!/usr/bin/env bash
# ==============================================================================
# scripts/seed-data.sh
# Triggers synthetic trade generation and seed loading into the ingestion service.
# Usage:
#   ./scripts/seed-data.sh               # Loads bundled 500-trade seed dataset
#   ./scripts/seed-data.sh --generate 50 # Generates 50 new synthetic trades
#   ./scripts/seed-data.sh --force       # Loads the seed again, even though it adds only duplicate exceptions
# ==============================================================================
set -euo pipefail

INGESTION_URL="${INGESTION_URL:-http://localhost:8081}"
AUTH_OPS="ops:${TCEM_OPS_PASSWORD:-ops-demo}"

post() { # <path>: prints the response, and fails on an error
  local body status
  body=$(curl -s -u "$AUTH_OPS" -w '\n%{http_code}' -X POST "$INGESTION_URL$1")
  status=${body##*$'\n'}
  body=${body%$'\n'*}
  if [ "$status" = 409 ]; then
    echo "$body" | jq -r .error 2>/dev/null || echo "$body"
    echo "To start again: ./scripts/dev-down.sh --reset, then ./scripts/dev-up.sh --all"
    return 1
  fi
  if [ "$status" != 200 ]; then
    echo "Request failed with HTTP $status: $body" >&2
    return 1
  fi
  echo "$body" | jq . 2>/dev/null || echo "$body"
}

case "${1:-}" in
  --generate)
    COUNT="${2:-20}"
    echo "=== [seed-data] Generating $COUNT new synthetic trades (publishing to Kafka) ==="
    post "/trades/generate?count=$COUNT&publishToKafka=true"
    ;;
  --force)
    echo "=== [seed-data] Loading the seed dataset again, as requested. This raises a duplicate exception for every message ==="
    post "/trades/seed?publishToKafka=true&force=true"
    ;;
  *)
    echo "=== [seed-data] Loading bundled 500-trade seed dataset (sample-trades.json) ==="
    post "/trades/seed?publishToKafka=true"
    ;;
esac
