# Trade Confirmation Exception Management Platform

Capstone project: an event-driven platform that consumes synthetic
post-trade messages, enriches and validates them, generates confirmations
where possible, captures and classifies exceptions where not, links them to
mock work items, and gives operations users a dashboard-driven view. No
UI-based trade entry - all trade intake is event/message-driven.

Stack: **Apache Kafka** (event streaming) · **Apache CouchDB** (document
store) · **Java / Spring Boot** (microservices) · **React + TypeScript**
(operations UI) · **Docker Compose** (local orchestration).

> **Status:** All 5 backend microservices, shared library (`common-lib`), and React Operations Dashboard are **100% implemented, tested, and verified** across Docker Compose and CI pipelines. 
> See [`docs/project-status.md`](docs/project-status.md) for detailed service verification, [`docs/reports/capstone_spec_compliance_report.md`](docs/reports/capstone_spec_compliance_report.md) for Capstone specification compliance & NFR assessment, [`docs/reports/code_consistency_report.md`](docs/reports/code_consistency_report.md) for architectural audit notes, and [`docs/reports/live_demo_presentation_guide.md`](docs/reports/live_demo_presentation_guide.md) for the live demonstration script.

## Repo layout

```
services/
  pom.xml                 # Maven reactor (parent) - aggregates all backend modules
  common-lib/             # Shared FpML DTOs, Kafka event contracts, fingerprinting, constants
  ingestion-service/      # FR1-FR3: intake, schema validation, raw persistence (port 8081)
  enrichment-service/     # FR4: reference-data enrichment, LEI & template resolution (port 8082)
  validation-service/     # FR5-FR6: STP rules engine + confirmation generation (port 8083)
  exception-service/      # FR7-FR9: exception capture, classification, recommendation (port 8084)
  workitem-service/       # FR10, FR12: work-item tracker, audit trail, replay/DLQ (port 8085)
frontend/                 # FR11: React 19 + TypeScript operations dashboard (port 3000)
docs/
  architecture.md         # Event flow diagram + service boundaries
  kafka-topics.md         # Full topic list and event schemas
  devops-guide.md         # Docker orchestration & container provisioning
  project-status.md       # Service implementation status and verification
  reports/                # Code consistency audit & live demo guide
docker-compose.yml         # Kafka (KRaft), Kafka UI, CouchDB, all 5 services + frontend
.github/                   # CI workflows (backend reactor, frontend lint/build, smoke test)
```

All microservices run as containerized Spring Boot applications connecting over the internal Docker network `tcem-network`. The frontend container serves the React application and reverse-proxies REST endpoints via Nginx.

## Prerequisites

- Docker + Docker Compose
- JDK 21 (only needed if running a service outside Docker, e.g. from an IDE)
- Node 20+ (only needed for frontend dev outside Docker)

## Running locally

Copy the env template first:

```bash
cp .env.example .env
```

**Infra only** (recommended while a service has no real code yet - run the
Spring Boot app from your IDE instead, pointed at these containers):

```bash
# 1. Start core infrastructure
docker compose up -d kafka kafka-ui couchdb

# 2. Provision Kafka topics (10 topics, 3 partitions) and CouchDB databases
docker compose up kafka-init couchdb-init
```

- Kafka broker: `localhost:9092`
- Kafka UI: http://localhost:8080
- CouchDB: http://localhost:5984 (Fauxton UI at `/_utils`), credentials from `.env`

**Everything, containerized:**

```bash
docker compose up -d --build
```

| Service              | Host port |
|-----------------------|-----------|
| ingestion-service      | 8081 |
| enrichment-service     | 8082 |
| validation-service     | 8083 |
| exception-service      | 8084 |
| workitem-service       | 8085 |
| frontend               | 3000 |
| Kafka UI               | 8080 |
| CouchDB                | 5984 |

## Backend development

`common-lib` is a shared dependency, not a runnable app - install it (and
everything else) to your local Maven repo once, then run whichever service
you're working on from inside its own directory:

```bash
cd services
mvn clean install -DskipTests    # builds all modules, publishes common-lib to ~/.m2
cd ingestion-service              # or enrichment-service / validation-service / etc.
mvn spring-boot:run
```

Re-run `mvn clean install -DskipTests` from `services/` whenever `common-lib`
changes, so every service picks up the update. You don't need to reinstall
just because you changed code inside a single service - `mvn spring-boot:run`
from that service's own directory rebuilds it directly.

```bash
mvn -f services/pom.xml verify   # build + test everything (used by CI too)
```

### Ingestion Service REST APIs & Operations (Port 8081)

The `ingestion-service` exposes comprehensive REST endpoints for synthetic trade generation, seed loading, operations querying, and STP metrics:

| Endpoint | Method | Description |
|---|---|---|
| `/trades/generate?count={N}&publishToKafka={true\|false}` | POST | Generates `N` synthetic trades across all 10 scenario types (default `publishToKafka=false` for instant offline preview). |
| `/trades/generate/scenario?scenario={TYPE}&sequence={N}&publishToKafka={true\|false}` | POST | Generates a single trade for a specific scenario type (e.g. `MISSING_ECONOMIC_STRIKE`). |
| `/trades/seed?publishToKafka={true\|false}` | POST | Loads pre-packaged 500-trade seed dataset from `sample-trades.json`. |
| `/trades/scenarios` | GET | Lists all 10 available synthetic trade test scenarios. |
| `/trades/{id}` | GET | Returns trade details and complete CouchDB audit trail (FR3). |
| `/trades?status={STATUS}&page={P}&size={S}` | GET | Returns paginated list of trades with optional status filter (`ACCEPTED`, `SCHEMA_INVALID`, `RECEIVED`). |
| `/trades/{id}/audit` | GET | Returns chronological audit log records from CouchDB `audit-events`. |
| `/trades/metrics/summary` | GET | Returns aggregate ingestion counters (received, accepted, rejected, duplicates, DLQ, acceptance rate). |
| `/metrics/stp-rate` | GET | Returns Straight-Through Processing (STP) rate for React Operations UI (FR11). |
| `/actuator/health` | GET | Spring Boot Actuator health check (includes custom CouchDB health indicator). |

Through the gateway, these are mapped under `/api/ingestion/...`. Full documentation in [`services/ingestion-service/README.md`](services/ingestion-service/README.md).

### Enrichment Service (Port 8082)

The `enrichment-service` consumes `trade.accepted`, enriches trades against reference datasets (`counterparties.json`, `instruments.json`, `templates.json`, `counterparty-products.json`), and publishes to `trade.enriched` (FR4).
Full documentation in [`services/enrichment-service/README.md`](services/enrichment-service/README.md).

### Validation Service REST APIs (Port 8083)

The `validation-service` runs the STP rules engine, generates plain-text ISDA confirmation documents, calculates SHA-256 fingerprints to suppress duplicate confirmations on amendments, and confirms held trades approved via manual review (FR5, FR6).

| Endpoint | Method | Description |
|---|---|---|
| `/confirmations/{id}` | GET | Returns a single confirmation document by `confirmationId` (404 if not found). |
| `/trades/{tradeId}/confirmations` | GET | Returns all confirmation versions for a trade, ordered newest-first. |
| `/trades/{tradeId}/confirmation` | GET | Returns the latest active confirmation document for a trade. |
| `/metrics/confirmation-count` | GET | Returns total count of persisted confirmations for operations dashboards. |
| `/actuator/health` | GET | Spring Boot Actuator health check (includes CouchDB connectivity). |

Through the gateway, these are mapped under `/api/validation/...`. Full documentation in [`services/validation-service/README.md`](services/validation-service/README.md).

### Exception Service REST APIs (Port 8084)

The `exception-service` consumes `exception.created`, classifies root causes across standard taxonomies, calculates confidence scores and dynamic SLA deadlines (+4h to +7d), and produces actionable recommendations (FR7, FR8, FR9).

| Endpoint | Method | Description |
|---|---|---|
| `/exceptions/{id}` | GET | Returns a single exception detail document and its complete CouchDB audit trail. |
| `/exceptions?tradeId=&status=&category=&page=&size=` | GET | Returns a paginated list of exceptions with multi-field filtering. |
| `/exceptions/{id}/audit` | GET | Returns chronological audit trail for a specific exception ID. |
| `/exceptions/metrics/summary` | GET | Returns in-memory operational metrics (total received, classified, manual review, duplicates, DLQ). |
| `/metrics/exception-rate` | GET | Returns classification rate percentage for the executive dashboard. |
| `/actuator/health` | GET | Spring Boot Actuator health check (includes CouchDB connectivity). |

Through the gateway, these are mapped under `/api/exception/...`. Full documentation in [`services/exception-service/README.md`](services/exception-service/README.md).

### Workitem Service REST APIs (Port 8085)

The `workitem-service` consumes `deadletter.trade`, `exception.classified`, `manual.review.required`, `confirmation.generated` and the pipeline progress topics, and exposes the work-item tracker, manual review queue and DLQ/replay controls (FR10, FR12). Flows, rules and contracts are in [`services/workitem-service/README.md`](services/workitem-service/README.md).

| Endpoint | Method | Description |
|---|---|---|
| `/workitems?status=&type=&ownerQueue=&priority=&tradeId=&page=&size=` | GET | Paginated work items, newest first. |
| `/workitems/summary` | GET | Counts by status/type, open counts by queue/priority, aged open items. |
| `/workitems/{id}` | GET | One work item. |
| `/workitems` | POST | Create a work item (`201`; `400` on invalid values). |
| `/workitems/{id}` | PATCH | Update status, priority, owner queue or notes (`409` on terminal items). |
| `/workitems/{id}/review` | POST | `APPROVE` or `REJECT` a `MANUAL_REVIEW` item. Approve makes `validation-service` generate the confirmation; reject sends the trade to the DLQ. |
| `/dlq?status={STATUS}&page={P}&size={S}` | GET | Dead-letter entries (`PENDING_REPLAY`, `REPLAYING`, `REPLAY_SUCCEEDED`, `PERMANENTLY_FAILED`). |
| `/dlq/{id}` | GET | One DLQ entry, including its failure history. |
| `/dlq/{id}/replay` | POST | Republish the original message to the topic it failed on (max 3 attempts). |
| `/actuator/health` | GET | Health check (includes CouchDB connectivity). |

Through the gateway these are `/api/workitems/...` and `/api/dlq/...`.

## Frontend development

```bash
cd frontend
npm install
npm run dev      # Vite dev server on :5173
```

## Docs

- [`docs/devops-guide.md`](docs/devops-guide.md) - Docker orchestration, provisioning, container security & workflow guide
- [`docs/architecture.md`](docs/architecture.md) - Event flow + service boundaries
- [`docs/kafka-topics.md`](docs/kafka-topics.md) - Topics, keys, and event payload contracts
- [`docs/project-status.md`](docs/project-status.md) - Per-service progress, verification, and implementation status
- [`services/ingestion-service/README.md`](services/ingestion-service/README.md) - Ingestion pipeline, synthetic generator, CouchDB audit, and REST APIs
- [`services/enrichment-service/README.md`](services/enrichment-service/README.md) - Reference data enrichment, LEI mapping, and template resolution
- [`services/validation-service/README.md`](services/validation-service/README.md) - STP rules engine, confirmation generation, SHA-256 fingerprinting, and held trades
- [`services/exception-service/README.md`](services/exception-service/README.md) - Exception classification, dynamic SLAs (+4h to +7d), and recommendations
- [`services/workitem-service/README.md`](services/workitem-service/README.md) - Work-item, manual review and DLQ/replay flows, rules and contracts
- [`docs/reports/capstone_spec_compliance_report.md`](docs/reports/capstone_spec_compliance_report.md) - Capstone specification 1-to-1 compliance, NFR assessment & production roadmap
- [`docs/reports/code_consistency_report.md`](docs/reports/code_consistency_report.md) - Multi-phase architectural audit & remediation notes
- [`docs/reports/live_demo_presentation_guide.md`](docs/reports/live_demo_presentation_guide.md) - Master live demonstration presentation guide and script
- [`CONTRIBUTING.md`](CONTRIBUTING.md) - Branching, PRs, definition of done
