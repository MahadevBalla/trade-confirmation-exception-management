#!/usr/bin/env bash
# ==============================================================================
# scripts/init-kafka.sh
# Idempotently provisions all 10 TCEM Kafka topics with 3 partitions each.
# ==============================================================================
set -euo pipefail

# Address of the broker as seen from INSIDE the kafka container (commands run via docker compose exec).
BOOTSTRAP_SERVER="${INIT_KAFKA_BOOTSTRAP_SERVER:-localhost:9092}"
TOPICS=(
  "trade.raw"
  "trade.accepted"
  "trade.enriched"
  "stp.validated"
  "confirmation.generated"
  "exception.created"
  "exception.classified"
  "workitem.linked"
  "manual.review.required"
  "deadletter.trade"
)

echo "=== [init-kafka] Checking Kafka broker at $BOOTSTRAP_SERVER ==="

# Check broker connectivity via docker compose exec
if ! docker compose exec -T kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "$BOOTSTRAP_SERVER" > /dev/null 2>&1; then
  echo "Error: Kafka broker is not reachable. Ensure Kafka is running: docker compose up -d kafka"
  exit 1
fi

echo "=== [init-kafka] Creating topics (3 partitions, replication factor 1) ==="
for topic in "${TOPICS[@]}"; do
  echo -n "  Provisioning topic: $topic ... "
  docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions 3 \
    --replication-factor 1 > /dev/null 2>&1
  echo "DONE"
done

echo ""
echo "=== [init-kafka] Verified Topics on Broker ==="
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list
