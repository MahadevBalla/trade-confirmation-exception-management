#!/usr/bin/env bash
# ==============================================================================
# scripts/dev-up.sh
# One-command developer bootstrap:
# Usage:
#   ./scripts/dev-up.sh              # Boots infra + ingestion + frontend (default dev stack)
#   ./scripts/dev-up.sh --all        # Boots all 5 backend microservices + frontend
#   ./scripts/dev-up.sh --infra-only # Boots Kafka, CouchDB, Kafka UI, runs initializers
# ==============================================================================
set -euo pipefail

MODE="${1:-}"

echo "=== [dev-up] Starting TCEM Local Development Stack ==="

# 1. Ensure .env exists
if [ ! -f .env ]; then
  echo "Notice: .env not found, creating from .env.example..."
  cp .env.example .env
fi

# 2. Start infrastructure
echo "=== [dev-up] Starting Kafka broker, CouchDB, and Kafka UI ==="
docker compose up -d kafka couchdb kafka-ui

# 3. Run initializers
echo "=== [dev-up] Running topic and database initializers ==="
docker compose up kafka-init couchdb-init

if [ "$MODE" = "--infra-only" ]; then
  echo "=== [dev-up] Infrastructure ready! Run your microservices from your IDE. ==="
  exit 0
fi

# 4. Start services
if [ "$MODE" = "--all" ]; then
  echo "=== [dev-up] Building and starting ALL services containerized ==="
  docker compose up -d --build
else
  echo "=== [dev-up] Building and starting Ingestion Service and Frontend Gateway ==="
  docker compose up -d --build ingestion-service frontend
fi

# 5. Run smoke test
echo "=== [dev-up] Running smoke test verification ==="
./scripts/smoke-test.sh "$MODE"
