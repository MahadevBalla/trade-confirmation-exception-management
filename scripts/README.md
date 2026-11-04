# TCEM Developer Tooling Scripts (`scripts/`)

This directory provides CLI helper scripts for local development, environment provisioning, and automated testing.

---

## Script Inventory

| Script | Command | Purpose |
|---|---|---|
| **Smoke Test** | `./scripts/smoke-test.sh` | Verifies health of all active services, Actuator endpoints, and gateway routes. Use `--all` to assert all 5 microservices are UP. |
| **Pipeline Test** | `./scripts/pipeline-test.sh` | End-to-end checks against a running full stack: confirmation, missing economics, manual review approval, DLQ replay and cancellation. Run by CI. Needs `jq`, and briefly deletes the `validation-idempotency` database. |
| **Kafka Init** | `./scripts/init-kafka.sh` | Idempotently provisions all 10 Kafka topics with 3 partitions and replication factor 1. Commands run inside the Kafka container; `INIT_KAFKA_BOOTSTRAP_SERVER` overrides the broker address as seen from there (default `localhost:9092`). |
| **CouchDB Init** | `./scripts/init-couchdb.sh` | Idempotently creates system and application CouchDB databases with Basic Auth. |
| **Dev Up** | `./scripts/dev-up.sh` | Boots infra, initializes Kafka & CouchDB, builds & starts services, and runs smoke checks. Supports `--infra-only` and `--all`, and passes the mode to the smoke test, so `--all` asserts all 5 services. |
| **Dev Down** | `./scripts/dev-down.sh` | Stops containers. Pass `--reset` (or `-v`) to wipe volume data. |
| **Seed Data** | `./scripts/seed-data.sh` | Triggers synthetic trade generation or loads the bundled 500-trade seed dataset (`sample-trades.json`). Pass `--generate <N>` to generate N trades. |

---

## Quick Usage Examples

### 1. Daily Development (Infra Only + Local IDE)
```bash
./scripts/dev-up.sh --infra-only
```
Kafka (`localhost:9092`), CouchDB (`localhost:5984`), and Kafka UI (`http://localhost:8080`) will start, and topics & databases are automatically created. You can now run Spring Boot apps directly from IntelliJ or CLI.

### 2. Full Stack (Containerized Verification)
```bash
./scripts/dev-up.sh
```
Starts infra, init containers, `ingestion-service`, and `frontend` (Nginx Gateway on port `3000`), then runs the smoke test suite.

### 3. Generate Synthetic Trade Data
```bash
./scripts/seed-data.sh                  # Loads 500-trade dataset
./scripts/seed-data.sh --generate 50    # Generates 50 new random trades
```

### 4. Verify System Health
```bash
./scripts/smoke-test.sh
```

### 5. Tear Down & Clean
```bash
./scripts/dev-down.sh          # Stops containers, preserves data
./scripts/dev-down.sh --reset  # Stops containers, wipes CouchDB and Kafka volumes
```
