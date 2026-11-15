# Trade Confirmation & Exception Management (TCEM) Platform

> **Enterprise-Grade Event-Driven Post-Trade Confirmation, Reconciliation & Exception Remediation Engine**  
> Built with **Java 21 / Spring Boot 3.3** · **Apache Kafka 3.8 (KRaft)** · **Apache CouchDB 3.3** · **React 19 / TypeScript 5.8** · **Docker Compose**

---

## 1. Executive Summary

In high-volume Over-The-Counter (OTC) derivatives and financial markets, unconfirmed transactions create critical operational, credit, and settlement risks under strict regulatory frameworks (e.g., T+1 settlement mandates, MiFID II, and CFTC compliance). When trade details contain economic discrepancies, invalid reference data, or template mismatches, they drop out of Straight-Through Processing (STP) and require rapid, auditable investigation.

The **Trade Confirmation & Exception Management (TCEM) Platform** is a distributed, event-driven post-trade processing engine that:
1. **Intakes Asynchronous Trade Events:** Consumes high-throughput trade feeds via Kafka without manual UI entry forms.
2. **Enforces Edge Idempotency & Validation:** Validates FpML-inspired JSON envelopes, ISO conventions, and economic terms while filtering duplicates in $O(1)$ time.
3. **Enriches Static Reference Data:** Resolves Legal Entity Identifiers (LEIs), legal names, and eligible master confirmation templates in-flight.
4. **Executes STP Decisioning & Fingerprinting:** Automates plain-text ISDA confirmation notices while utilizing SHA-256 fingerprinting to eliminate redundant legal notices on unchanged amendments.
5. **Classifies & Prioritizes Exceptions:** Employs deterministic taxonomy rules, confidence scoring, dynamic regulatory SLA countdowns (+4h to +7d), and operator action recommendations.
6. **Orchestrates Remediation & Self-Healing:** Powers 4-eyes manual review approvals (automatically confirming held trades upon sign-off) and controlled Dead-Letter Queue (DLQ) replays with strict retry bounds.
7. **Maintains Immutable Auditability:** Records chronological status transitions across all services in CouchDB for regulatory compliance.

---

## 2. Architecture & Service Inventory

```
[Trade Feeds / Execution Venues]
               │
               ▼ (trade.raw)
     ┌───────────────────┐
     │ Ingestion Service │ ──► CouchDB: trades, audit-events
     └───────────────────┘
               │
               ▼ (trade.accepted)
     ┌────────────────────┐
     │ Enrichment Service │ ──► CouchDB: enrichment-idempotency
     └────────────────────┘     (In-flight LEI, Counterparty & Template Joins)
               │
               ▼ (trade.enriched)
     ┌────────────────────┐
     │ Validation Service │ ──► CouchDB: confirmations, validation-idempotency
     └────────────────────┘     (STP Rules, Monospace Confirmations, Fingerprints)
          │          │
 (STP OK) │          │ (STP Fail)
          ▼          ▼
[confirmation.   [exception.created]
  generated]         │
          │          ▼
          │    ┌───────────────────┐
          │    │ Exception Service │ ──► CouchDB: exceptions, audit-events
          │    └───────────────────┘     (Taxonomy, Dynamic SLAs, Recommendations)
          │          │          │
          │ (Conf >= 0.6)       │ (Conf < 0.6 / Dispute)
          │          ▼          ▼
          │    [exception.   [manual.review.
          │     classified]    required]
          │          │          │
          ▼          ▼          ▼
     ┌─────────────────────────────────────────────────────────────┐
     │             Workitem Service & DLQ Replay Engine            │
     │  - Remediation Tracker: workitem.linked                     │
     │  - 4-Eyes Manual Review (Approve -> Confirm, Reject -> DLQ) │
     │  - Controlled DLQ Replay (POST /dlq/{id}/replay)            │
     │  - CouchDB: work-items, dlq-entries, audit-events           │
     └─────────────────────────────────────────────────────────────┘
```

### Component Port Mapping

| Service / Container | Internal Port | Host Port | Technology Stack | Core Responsibilities |
|---|---|---|---|---|
| **`tcem-ingestion-service`** | `8081` | `8081` | Spring Boot 3.3 / Java 21 | Trade intake, schema validation, raw persistence, CouchDB idempotency |
| **`tcem-enrichment-service`** | `8082` | `8082` | Spring Boot 3.3 / Java 21 | Reference data joins, party LEI resolution, template matching |
| **`tcem-validation-service`** | `8083` | `8083` | Spring Boot 3.3 / Java 21 | STP business rules, confirmation generation, SHA-256 fingerprinting |
| **`tcem-exception-service`** | `8084` | `8084` | Spring Boot 3.3 / Java 21 | Root-cause taxonomy classification, confidence scoring, dynamic SLAs |
| **`tcem-workitem-service`** | `8085` | `8085` | Spring Boot 3.3 / Java 21 | Work item tracking, 4-eyes manual review approvals, DLQ controlled replay |
| **`tcem-frontend`** | `80` | `3000` | React 19 / TypeScript / Nginx | Operations cockpit, real-time KPI metrics, review console, DLQ manager |
| **`tcem-kafka`** | `19092` | `9092` | Apache Kafka 3.8 (KRaft) | Event streaming broker (10 topics, 3 partitions per topic) |
| **`tcem-couchdb`** | `5984` | `5984` | Apache CouchDB 3.3 | Multi-database NoSQL document store (Mango indexed queries) |
| **`tcem-kafka-ui`** | `8080` | `8080` | Provectus Kafka UI | Visual stream inspection, topic lag, and partition monitoring |

---

## 3. Repository Layout

```
services/
  pom.xml                 # Parent Maven reactor compiling all backend modules
  common-lib/             # Shared domain DTOs, Kafka event contracts, fingerprinting, constants
  ingestion-service/      # Inbound stream processing, schema validation, raw audit store (Port 8081)
  enrichment-service/     # Reference data cache, LEI mapping, template resolution (Port 8082)
  validation-service/     # STP rules engine, monospace ISDA generator, held trade pattern (Port 8083)
  exception-service/      # Root-cause taxonomy engine, SLA countdowns, recommendations (Port 8084)
  workitem-service/       # Work-item tracker, 4-eyes review, audit ledger, DLQ replay (Port 8085)
frontend/                 # React 19 + TypeScript operations cockpit and Nginx reverse proxy (Port 3000)
scripts/                  # CLI tooling: bootstrap, smoke tests, seeding, and NFR benchmarks
docs/
  architecture.md         # Detailed event flow, service boundaries, and CouchDB schemas
  kafka-topics.md         # 10 topic specifications, partition keys, and payload contracts
  devops-guide.md         # Container orchestration, security hardening, and JVM ergonomics
  project-status.md       # Technical audit, test verification, and service readiness records
  reports/                # NFR benchmark results, architectural consistency audit, and demo scripts
docker-compose.yml         # Unified local container orchestration
```

---

## 4. Quickstart & Local Deployment

### Prerequisites
* **Docker Engine 24+** & **Docker Compose v2**
* **JDK 21** *(optional, only for running services directly in an IDE)*
* **Node.js 20+** *(optional, only for standalone frontend development)*

### 1. Initialize Environment
```bash
cp .env.example .env
```

### 2. Start Full Stack (Containerized)
Bootstrap the entire ecosystem with automated health checks, topic provisioning, and database initializers:
```bash
./scripts/dev-up.sh --all
```
*(Or use standard Docker Compose: `docker compose up -d --build`)*

* **Operations Cockpit:** [http://localhost:3000](http://localhost:3000)
* **Kafka UI:** [http://localhost:8080](http://localhost:8080)
* **CouchDB Fauxton:** [http://localhost:5984/_utils](http://localhost:5984/_utils) *(Credentials: `admin` / `password`)*

### 3. Verify System Health
Run the automated smoke test suite verifying all 5 services, database endpoints, and gateway routes:
```bash
./scripts/smoke-test.sh --all
```

### 4. Inject Test Data
```bash
./scripts/seed-data.sh                  # Loads bundled 500-trade seed dataset
./scripts/seed-data.sh --generate 50    # Generates 50 new multi-scenario synthetic trades
```

### 5. Tear Down Stack
```bash
./scripts/dev-down.sh          # Stops containers, preserves database volumes
./scripts/dev-down.sh --reset  # Stops containers, wipes CouchDB and Kafka volumes for clean reset
```

---

## 5. Microservice REST API Reference

All backend microservices expose REST APIs accessible directly on their individual host ports or unified through the frontend Nginx reverse proxy gateway under `/api/*`.

### Ingestion Service (Port 8081 / Gateway: `/api/ingestion/*`)
*Full documentation:* [`services/ingestion-service/README.md`](services/ingestion-service/README.md)

| Endpoint | Method | Description |
|---|---|---|
| `/trades/generate?count={N}&publishToKafka={bool}` | POST | Generates $N$ synthetic trades across 10 deterministic test scenarios. |
| `/trades/generate/scenario?scenario={TYPE}&sequence={N}&publishToKafka={bool}` | POST | Generates a single trade for an exact scenario (e.g. `VALID_STANDARD_STP`). |
| `/trades/seed?publishToKafka={bool}` | POST | Ingests pre-packaged 500-trade reference dataset. |
| `/trades/scenarios` | GET | Lists all available synthetic trade test scenarios. |
| `/trades/{id}` | GET | Returns trade details, normalized economics, and complete CouchDB audit trail. |
| `/trades?status={STATUS}&page={P}&size={S}` | GET | Paginated trade query with optional status filtering (`ACCEPTED`, `SCHEMA_INVALID`). |
| `/trades/{id}/audit` | GET | Chronological audit log records from CouchDB `audit-events`. |
| `/trades/metrics/summary` | GET | Ingestion counters (received, accepted, rejected, duplicates, acceptance rate). |
| `/metrics/stp-rate` | GET | Straight-Through Processing (STP) rate metrics for operations dashboards. |
| `/actuator/health` | GET | Service liveness and CouchDB connection health check. |

### Enrichment Service (Port 8082 / Gateway: `/api/enrichment/*`)
*Full documentation:* [`services/enrichment-service/README.md`](services/enrichment-service/README.md)

| Endpoint | Method | Description |
|---|---|---|
| `/actuator/health` | GET | Service liveness and CouchDB idempotency store health check. |
| `/actuator/info` | GET | Service metadata, application name, and runtime environment. |

### Validation Service (Port 8083 / Gateway: `/api/validation/*`)
*Full documentation:* [`services/validation-service/README.md`](services/validation-service/README.md)

| Endpoint | Method | Description |
|---|---|---|
| `/confirmations/{id}` | GET | Returns a single confirmation document by `confirmationId` (404 if absent). |
| `/trades/{tradeId}/confirmations` | GET | Returns all confirmation versions for a trade, ordered newest-first. |
| `/trades/{tradeId}/confirmation` | GET | Returns the latest active confirmation notice for a trade. |
| `/metrics/confirmation-count` | GET | Returns total count of persisted legal confirmations. |
| `/actuator/health` | GET | Service liveness and CouchDB connection health check. |

### Exception Service (Port 8084 / Gateway: `/api/exception/*`)
*Full documentation:* [`services/exception-service/README.md`](services/exception-service/README.md)

| Endpoint | Method | Description |
|---|---|---|
| `/exceptions/{id}` | GET | Returns a single classified exception detail document and its audit history. |
| `/exceptions?tradeId=&status=&category=&page=&size=` | GET | Paginated exception query with multi-attribute filtering. |
| `/exceptions/{id}/audit` | GET | Chronological audit trail for a specific exception ID. |
| `/exceptions/metrics/summary` | GET | Operational exception counters (received, classified, manual review, duplicates). |
| `/metrics/exception-rate` | GET | Classification rate percentage for dashboard widgets. |
| `/actuator/health` | GET | Service liveness and CouchDB connection health check. |

### Workitem Service (Port 8085 / Gateway: `/api/workitems/*`, `/api/dlq/*`)
*Full documentation:* [`services/workitem-service/README.md`](services/workitem-service/README.md)

| Endpoint | Method | Description |
|---|---|---|
| `/workitems?status=&type=&ownerQueue=&priority=&tradeId=&page=&size=` | GET | Paginated work items query, newest-first. |
| `/workitems/summary` | GET | Aggregations by status, queue, priority, and aged open items (>24h). |
| `/workitems/{id}` | GET | Returns a single remediation work item. |
| `/workitems` | POST | Creates a new manual remediation work item ticket. |
| `/workitems/{id}` | PATCH | Updates ticket status, priority, owner queue, or operator notes. |
| `/workitems/{id}/review` | POST | 4-Eyes Review: `APPROVE` (triggers confirmation) or `REJECT` (routes to DLQ). |
| `/dlq?status={STATUS}&page={P}&size={S}` | GET | Paginated dead-letter entries (`PENDING_REPLAY`, `REPLAYING`, `REPLAY_SUCCEEDED`, `PERMANENTLY_FAILED`). |
| `/dlq/{id}` | GET | Returns a single dead-letter record with full failure history and diagnostic traces. |
| `/dlq/{id}/replay` | POST | Republishes failed payload to upstream topic with incremented retry count. |
| `/actuator/health` | GET | Service liveness and CouchDB connection health check. |

---

## 6. Developer Guidelines

### Backend Reactor Build & Test
Build all 6 submodules (`common-lib` + 5 microservices) from the root reactor:
```bash
cd services
mvn clean install -DskipTests    # Compiles modules and publishes common-lib locally
mvn verify                       # Runs full automated test suite (512 passing tests)
```

### Running a Single Service Locally
Start infrastructure containers first:
```bash
./scripts/dev-up.sh --infra-only
```
Then run any target service directly from its module directory:
```bash
cd services/ingestion-service    # or enrichment-service / validation-service / etc.
mvn spring-boot:run
```

### Performance & Latency Benchmarks
Execute the automated NFR performance and latency test suite:
```bash
./scripts/test-nfr.sh                                  # Measures end-to-end latency & throughput
python3 scripts/benchmark_suite.py --mode latency      # p50, p90, p95 statistical latency profile
python3 scripts/benchmark_suite.py --mode load --count 500  # High-volume multi-scenario burst test
```

---

## 7. Documentation Index

- [`docs/architecture.md`](docs/architecture.md) — Comprehensive event streaming architecture, service boundaries, and CouchDB schemas
- [`docs/kafka-topics.md`](docs/kafka-topics.md) — Complete 10-topic specification, partition key strategies, and event schemas
- [`docs/devops-guide.md`](docs/devops-guide.md) — Docker orchestration, non-root security, provisioning, and container-aware JVM ergonomics
- [`docs/project-status.md`](docs/project-status.md) — Multi-service implementation details, test coverage (512 tests), and audit status
- [`docs/reports/capstone_spec_compliance_report.md`](docs/reports/capstone_spec_compliance_report.md) — 1-to-1 specification compliance matrix, NFR assessment, and enterprise production roadmap
- [`docs/reports/code_consistency_report.md`](docs/reports/code_consistency_report.md) — Multi-phase architectural consistency audit and resolution records
- [`docs/reports/live_demo_presentation_guide.md`](docs/reports/live_demo_presentation_guide.md) — Master screen-shared demonstration script and speaking flow
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — Branching models, commit conventions, and pull request definitions of done
