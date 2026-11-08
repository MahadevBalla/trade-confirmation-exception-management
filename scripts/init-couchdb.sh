#!/usr/bin/env bash
# ==============================================================================
# scripts/init-couchdb.sh
# Idempotently provisions system and application databases in CouchDB.
# ==============================================================================
set -euo pipefail

COUCHDB_USER="${COUCHDB_USER:-admin}"
COUCHDB_PASSWORD="${COUCHDB_PASSWORD:-password}"
COUCHDB_URL="${COUCHDB_URL:-http://localhost:5984}"
AUTH="$COUCHDB_USER:$COUCHDB_PASSWORD"

SYSTEM_DBS=(
  "_users"
  "_replicator"
  "_global_changes"
)

APP_DBS=(
  "trades"
  "confirmations"
  "exceptions"
  "work-items"
  "audit-events"
  "stp-metrics"
  "dlq-entries"
  "enrichment-idempotency"
  "validation-idempotency"
)

echo "=== [init-couchdb] Verifying CouchDB at $COUCHDB_URL ==="
if ! curl -s -f -u "$AUTH" "$COUCHDB_URL/_up" > /dev/null 2>&1; then
  echo "Error: CouchDB is not reachable at $COUCHDB_URL. Ensure CouchDB is running: docker compose up -d couchdb"
  exit 1
fi

echo "--- System Databases ---"
for db in "${SYSTEM_DBS[@]}"; do
  status=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" -X PUT "$COUCHDB_URL/$db")
  if [ "$status" = "201" ]; then
    echo "  [CREATED] $db"
  elif [ "$status" = "412" ]; then
    echo "  [EXISTS]  $db"
  else
    echo "  [WARN]    $db responded with HTTP $status"
  fi
done

echo "--- Application Databases ---"
for db in "${APP_DBS[@]}"; do
  status=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" -X PUT "$COUCHDB_URL/$db")
  if [ "$status" = "201" ]; then
    echo "  [CREATED] $db"
  elif [ "$status" = "412" ]; then
    echo "  [EXISTS]  $db"
  else
    echo "  [WARN]    $db responded with HTTP $status"
  fi
done

echo ""
echo "--- Exception count view ---"
VIEW_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -u "$AUTH" "$COUCHDB_URL/exceptions/_design/exception_counts")

if [ "$VIEW_STATUS" = "404" ]; then
  VIEW_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -u "$AUTH" \
    -X PUT "$COUCHDB_URL/exceptions/_design/exception_counts" \
    -H "Content-Type: application/json" \
    -d '{"_id":"_design/exception_counts","views":{"total":{"map":"function(doc) { if (doc.docType === \"EXCEPTION\") { emit(null, 1); } }","reduce":"_count"}}}')

  case "$VIEW_STATUS" in
    201|202) echo "  [CREATED] exception_counts/total" ;;
    *)
      echo "  [ERROR] Failed to create exception count view: HTTP $VIEW_STATUS"
      exit 1
      ;;
  esac
elif [ "$VIEW_STATUS" = "200" ]; then
  echo "  [EXISTS] exception_counts/total"
else
  echo "  [ERROR] Could not check exception count view: HTTP $VIEW_STATUS"
  exit 1
fi

echo "=== [init-couchdb] Verified Databases in CouchDB ==="
curl -s -u "$AUTH" "$COUCHDB_URL/_all_dbs" | jq . 2>/dev/null || curl -s -u "$AUTH" "$COUCHDB_URL/_all_dbs"
echo ""
