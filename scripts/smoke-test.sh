#!/usr/bin/env bash
# ==============================================================================
# scripts/smoke-test.sh
# Verifies container health, API responses, and gateway proxy routes.
# Usage:
#   ./scripts/smoke-test.sh         # Checks running stack + core services
#   ./scripts/smoke-test.sh --all   # Strictly asserts all 5 microservices are UP
# ==============================================================================
set -euo pipefail

CHECK_ALL="${1:-}"

GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[1;33m"
CYAN="\033[0;36m"
NC="\033[0m"

PASS_COUNT=0
FAIL_COUNT=0

check_endpoint() {
  local name="$1"
  local url="$2"
  local max_attempts="${3:-15}"
  local attempt=1

  printf "  Checking %-35s " "$name"
  while [ "$attempt" -le "$max_attempts" ]; do
    if curl -s -f -m 3 "$url" > /dev/null 2>&1; then
      printf "${GREEN}[PASS]${NC} (attempt %d)\n" "$attempt"
      PASS_COUNT=$((PASS_COUNT + 1))
      return 0
    fi
    sleep 1
    attempt=$((attempt + 1))
  done

  printf "${RED}[FAIL]${NC} (unreachable at %s)\n" "$url"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  return 1
}

check_optional_service() {
  local name="$1"
  local url="$2"
  if [ "$CHECK_ALL" = "--all" ]; then
    check_endpoint "$name" "$url" 10 || true
  else
    if curl -s -f -m 1 "$url" > /dev/null 2>&1; then
      printf "  Checking %-35s ${GREEN}[PASS]${NC}\n" "$name"
      PASS_COUNT=$((PASS_COUNT + 1))
    else
      printf "  Checking %-35s ${YELLOW}[SKIP]${NC} (offline, use --all to assert)\n" "$name"
    fi
  fi
}

echo -e "${CYAN}======================================================${NC}"
echo -e "${CYAN}       Trade Confirmation Platform Smoke Test         ${NC}"
echo -e "${CYAN}======================================================${NC}"

echo -e "\n1. Infrastructure Services:"
check_endpoint "CouchDB Health (_up)" "http://localhost:5984/_up"
check_endpoint "Kafka UI Web Portal" "http://localhost:8080"

echo -e "\n2. Ingestion Microservice (Port 8081):"
check_endpoint "Ingestion Actuator Health" "http://localhost:8081/actuator/health"
check_endpoint "Ingestion Scenarios API" "http://localhost:8081/trades/scenarios"

echo -e "\n3. Frontend Gateway & Proxy (Port 3000):"
check_endpoint "Frontend Static Root (/)" "http://localhost:3000/"
check_endpoint "Gateway -> /api/trades/scenarios" "http://localhost:3000/api/trades/scenarios"
check_endpoint "Gateway -> /api/metrics/stp-rate" "http://localhost:3000/api/metrics/stp-rate"
check_endpoint "SPA Fallback (/dashboard/exceptions)" "http://localhost:3000/dashboard/exceptions"

echo -e "\n4. Additional Backend Microservices:"
check_optional_service "Enrichment Service (:8082)" "http://localhost:8082/actuator/health"
check_optional_service "Validation Service (:8083)" "http://localhost:8083/actuator/health"
check_optional_service "Exception Service (:8084)" "http://localhost:8084/actuator/health"

echo -e "\n5. Workitem Microservice (Port 8085):"
check_optional_service "Workitem Service (:8085)" "http://localhost:8085/actuator/health"
check_optional_service "Workitem Summary API" "http://localhost:8085/workitems/summary"
check_optional_service "Gateway -> /api/workitems/summary" "http://localhost:3000/api/workitems/summary"
check_optional_service "Gateway -> /api/dlq" "http://localhost:3000/api/dlq"

echo -e "\n------------------------------------------------------"
if [ "$FAIL_COUNT" -eq 0 ]; then
  echo -e "${GREEN}SMOKE TEST PASSED! ($PASS_COUNT checks passed)${NC}"
  exit 0
else
  echo -e "${RED}SMOKE TEST FAILED! ($FAIL_COUNT checks failed, $PASS_COUNT passed)${NC}"
  exit 1
fi
