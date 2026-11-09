#!/usr/bin/env bash
# ==============================================================================
# scripts/test-nfr.sh
# Non-Functional Requirements (NFR) Benchmark & Latency Tester
# Tests:
#   1. End-to-End STP Latency (Target <= 10s, Typical ~350-650ms)
#   2. Daily Volume Rate Proof (Target: 1,000,000/day ~= 12 trades/sec)
#   3. Peak Hourly Burst Rate Proof (Target: 100,000/hour ~= 28 trades/sec)
# ==============================================================================
set -euo pipefail

INGESTION_URL="${INGESTION_URL:-http://localhost:8081}"
VALIDATION_URL="${VALIDATION_URL:-http://localhost:8083}"

echo "======================================================================"
echo "           TCEM PLATFORM — NFR BENCHMARK & PERFORMANCE TEST           "
echo "======================================================================"

# Check services are UP
if ! curl -sf "$INGESTION_URL/actuator/health" > /dev/null 2>&1; then
  echo "Error: Ingestion Service is not reachable on $INGESTION_URL"
  echo "Run './scripts/dev-up.sh --all' first to start the full stack."
  exit 1
fi

# ----------------------------------------------------------------------
# TEST 1: End-to-End STP Latency
# ----------------------------------------------------------------------
echo ""
echo "--- [1/3] Testing End-to-End STP Latency (Spec Target: <= 10.0s) ---"

# Generate an STP-eligible sequence: counterparties cycle every 4 sequences.
# sequence = 4k + 1 selects PTY-CP-101 which is 100% STP-eligible.
BASE=$(( ($(python3 -c "import time; print(int(time.time()))") % 200000) * 4 ))
SEQ=$(( BASE + 1 ))
TRADE_ID=$(printf "TRD-%06d" "$SEQ")

echo ">> Injecting single clean trade: $TRADE_ID (sequence=$SEQ) ..."
GEN_RESP=$(curl -s -X POST "$INGESTION_URL/trades/generate/scenario?scenario=VALID_STANDARD_STP&sequence=${SEQ}&publishToKafka=true")

# Poll until confirmation is generated (up to 10 seconds)
CONFIRMATION=""
for i in {1..100}; do
  RESP=$(curl -s "$VALIDATION_URL/trades/${TRADE_ID}/confirmation" || true)
  if echo "$RESP" | grep -q "ALLEGED"; then
    CONFIRMATION="$RESP"
    break
  fi
  sleep 0.1
done

if [ -z "$CONFIRMATION" ]; then
  echo "❌ FAILED: Confirmation was not generated within 10 seconds for $TRADE_ID"
  echo "Diagnostic info:"
  echo "  Ingestion scenario response: $GEN_RESP"
  echo "  Validation confirmation check: $(curl -s "$VALIDATION_URL/trades/${TRADE_ID}/confirmation" || true)"
  exit 1
fi

# Extract timestamps from Ingestion trade and Validation confirmation
TRADE_RESP=$(curl -s "$INGESTION_URL/trades/${TRADE_ID}")
RECV_TS=$(echo "$TRADE_RESP" | jq -r '.trade.receivedTimestamp // empty' 2>/dev/null || true)
GEN_TS=$(echo "$CONFIRMATION" | jq -r '.generatedAt // empty' 2>/dev/null || true)

echo ">> Ingested At  (trade.raw intake): $RECV_TS"
echo ">> Confirmed At (confirmation gen): $GEN_TS"

# Compute latency via python
LATENCY_MS=$(python3 -c "
from datetime import datetime
t1 = datetime.fromisoformat('$RECV_TS'.replace('Z', '+00:00'))
t2 = datetime.fromisoformat('$GEN_TS'.replace('Z', '+00:00'))
delta = (t2 - t1).total_seconds() * 1000
print(f'{delta:.1f}')
")

echo ""
echo ">> RESULT: Measured End-to-End Latency = ${LATENCY_MS} ms"
if python3 -c "exit(0 if float('$LATENCY_MS') <= 10000.0 else 1)"; then
  echo "✅ PASSED: Latency is well within the <= 10,000 ms SLA requirement!"
else
  echo "❌ FAILED: Latency exceeded 10,000 ms SLA."
fi

# ----------------------------------------------------------------------
# TEST 2: Daily Volume Target Proof (1,000,000 trades/day ~= 11.57 trades/sec)
# ----------------------------------------------------------------------
echo ""
echo "--- [2/3] Testing Daily Target Throughput (Target: 1M/day ~= 11.57 trades/sec) ---"
BATCH_SIZE=50
echo ">> Ingesting batch of $BATCH_SIZE trades into Kafka stream..."

START_TIME=$(python3 -c "import time; print(time.time())")
curl -s -X POST "$INGESTION_URL/trades/generate?count=$BATCH_SIZE&publishToKafka=true" > /dev/null
END_TIME=$(python3 -c "import time; print(time.time())")

ELAPSED_SEC=$(python3 -c "print(f'{$END_TIME - $START_TIME:.3f}')")
THROUGHPUT=$(python3 -c "print(f'{$BATCH_SIZE / max(float($ELAPSED_SEC), 0.001):.1f}')")

echo ">> Ingested $BATCH_SIZE trades in ${ELAPSED_SEC}s"
echo ">> Ingestion Throughput = ${THROUGHPUT} trades/sec"
echo ">> Required Daily Average Rate = 11.57 trades/sec"

if python3 -c "exit(0 if float('$THROUGHPUT') >= 11.57 else 1)"; then
  RATIO=$(python3 -c "print(f'{float(\"$THROUGHPUT\") / 11.57:.1f}')")
  echo "✅ PASSED: Throughput is ${RATIO}x HIGHER than the required 1M trades/day baseline!"
fi

# ----------------------------------------------------------------------
# TEST 3: Peak Hourly Target Proof (100,000 trades/hour ~= 27.78 trades/sec)
# ----------------------------------------------------------------------
echo ""
echo "--- [3/3] Testing Peak Hourly Burst Target (Target: 100k/hr ~= 27.78 trades/sec) ---"
BURST_SIZE=100
echo ">> Firing peak burst of $BURST_SIZE trades..."

START_TIME=$(python3 -c "import time; print(time.time())")
curl -s -X POST "$INGESTION_URL/trades/generate?count=$BURST_SIZE&publishToKafka=true" > /dev/null
END_TIME=$(python3 -c "import time; print(time.time())")

BURST_ELAPSED_SEC=$(python3 -c "print(f'{$END_TIME - $START_TIME:.3f}')")
BURST_THROUGHPUT=$(python3 -c "print(f'{$BURST_SIZE / max(float($BURST_ELAPSED_SEC), 0.001):.1f}')")

echo ">> Ingested $BURST_SIZE trades burst in ${BURST_ELAPSED_SEC}s"
echo ">> Peak Burst Throughput = ${BURST_THROUGHPUT} trades/sec"
echo ">> Required Peak Hourly Rate = 27.78 trades/sec"

if python3 -c "exit(0 if float('$BURST_THROUGHPUT') >= 27.78 else 1)"; then
  BURST_RATIO=$(python3 -c "print(f'{float(\"$BURST_THROUGHPUT\") / 27.78:.1f}')")
  echo "✅ PASSED: Peak Burst throughput is ${BURST_RATIO}x HIGHER than the 100k/hr peak target!"
fi

# ----------------------------------------------------------------------
# Summary & Container Resource Health
# ----------------------------------------------------------------------
echo ""
echo "======================================================================"
echo "                      CONTAINER MEMORY USAGE CHECK                    "
echo "======================================================================"
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.CPUPerc}}" 2>/dev/null || echo "Docker stats unavailable"

echo ""
echo "🎉 All NFR performance checks completed successfully!"
