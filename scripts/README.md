# TCEM Platform — Automation & Developer Tooling Scripts (`scripts/`)

> **Environment Provisioning, Synthetic Seeding, Smoke Testing & NFR Performance Benchmarking Suite**

---

## 1. Script Inventory

| Script | Command | Purpose |
|---|---|---|
| **Smoke Test** | `./scripts/smoke-test.sh` | Verifies health of active services, Actuator endpoints, and gateway routes. Use `--all` to assert all 5 microservices are UP. |
| **Pipeline Test** | `./scripts/pipeline-test.sh` | End-to-end checks against a running full stack: confirmation, missing economics, manual review approval, DLQ replay, and cancellation. |
| **Kafka Init** | `./scripts/init-kafka.sh` | Idempotently provisions all 10 Kafka topics with 3 partitions and replication factor 1. |
| **CouchDB Init** | `./scripts/init-couchdb.sh` | Idempotently creates system and application CouchDB databases with Basic Auth credentials. |
| **Dev Up** | `./scripts/dev-up.sh` | Boots infrastructure, initializes Kafka & CouchDB, builds & starts services, and runs smoke checks (`--infra-only` or `--all`). |
| **Dev Down** | `./scripts/dev-down.sh` | Stops containers. Pass `--reset` (or `-v`) to wipe persistent volume storage. |
| **Seed Data** | `./scripts/seed-data.sh` | Triggers synthetic trade generation or loads the bundled 500-trade seed dataset (`sample-trades.json`). Pass `--generate <N>` to generate N trades. |
| **NFR Benchmark** | `./scripts/test-nfr.sh` | Automated benchmark verifying end-to-end STP latency ($\le 10$s target), daily volume rate (1M/day), and peak hourly burst (100k/hr). |
| **Benchmark Suite** | `python3 scripts/benchmark_suite.py` | Multi-sample latency statistical profiler (Min, p50, Mean, p95, Max) and multi-scenario high-volume load generator. |
| **Stress Test Suite** | `python3 scripts/stress_test.py` | High-volume chunked stress test and sustained rate pacing engine (1M trades/day & 100k/hr NFR verification). |

---

## 2. Quick Usage Examples

### 1. Daily Development (Infrastructure Only + Local IDE)
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
