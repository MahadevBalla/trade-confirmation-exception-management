#!/usr/bin/env bash
# ==============================================================================
# scripts/dev-down.sh
# One-command developer teardown.
# Usage:
#   ./scripts/dev-down.sh          # Stops and removes containers (preserves database volumes)
#   ./scripts/dev-down.sh --reset  # Stops containers AND removes database volumes
# ==============================================================================
set -euo pipefail

FLAG="${1:-}"

if [ "$FLAG" = "--reset" ] || [ "$FLAG" = "-v" ]; then
  echo "=== [dev-down] Stopping containers and removing CouchDB volumes... ==="
  docker compose down -v
  echo "=== [dev-down] Environment reset to pristine state. ==="
else
  echo "=== [dev-down] Stopping containers (persisting database data)... ==="
  docker compose down
  echo "=== [dev-down] Containers stopped. (Run with --reset to wipe volumes) ==="
fi
