# Trade Confirmation Exception Management Platform

Capstone project: an event-driven platform that consumes synthetic
post-trade messages, enriches and validates them, generates confirmations
where possible, captures and classifies exceptions where not, links them to
mock work items, and gives operations users a dashboard-driven view. No
UI-based trade entry - all trade intake is event/message-driven.

Stack: **Apache Kafka** (event streaming) · **Apache CouchDB** (document
store) · **Java / Spring Boot** (microservices) · **React + TypeScript**
(operations UI) · **Docker Compose** (local orchestration).

> Status: in progress. `ingestion-service` and `workitem-service` are
> implemented and tested; `enrichment-service`, `validation-service` and
> `exception-service` are still runnable stubs. See
> [`docs/project-status.md`](docs/project-status.md) for per-service progress,
> and `CONTRIBUTING.md` before opening a PR.

## Repo layout

```
services/
  pom.xml                 # Maven reactor (parent) - aggregates all backend modules
  common-lib/             # Shared DTOs / Kafka event contracts (empty for now - see file)
  ingestion-service/      # FR1-FR3: intake, schema validation, raw persistence
  enrichment-service/     # FR4: mock reference-data enrichment
  validation-service/     # FR5-FR6: STP rules engine + confirmation generation
  exception-service/      # FR7-FR9: exception capture, classification, recommendation
  workitem-service/       # FR10, FR12: mock work-item tracker, audit trail, replay/DLQ
frontend/                 # FR11: React + TypeScript operations dashboard
docs/
  architecture.md         # Event flow diagram + service boundaries
  kafka-topics.md         # Full topic list and open decisions
  team-roles.md           # Role mapping + evaluation weighting
docker-compose.yml         # Kafka (KRaft), Kafka UI, CouchDB, all services
.github/                   # CI workflows (backend, frontend, e2e smoke test) + PR template
```

Services that are not implemented yet are plain Spring Boot apps that start and
answer `/actuator/health`. Fill in controllers, Kafka listeners/producers, and
CouchDB access as tasks get assigned.

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


### Workitem Service REST APIs (Port 8085)

The `workitem-service` consumes `deadletter.trade`, `exception.classified`, `manual.review.required`, `confirmation.generated` and the pipeline progress topics, and exposes the work-item tracker, manual review queue and DLQ/replay controls (FR10, FR12). Flows, rules and contracts are in [`services/workitem-service/README.md`](services/workitem-service/README.md).

| Endpoint | Method | Description |
|---|---|---|
| `/workitems?status=&type=&ownerQueue=&priority=&tradeId=&page=&size=` | GET | Paginated work items, newest first. |
| `/workitems/summary` | GET | Counts by status/type, open counts by queue/priority, aged open items. |
| `/workitems/{id}` | GET | One work item. |
| `/workitems` | POST | Create a work item (`201`; `400` on invalid values). |
| `/workitems/{id}` | PATCH | Update status, priority, owner queue or notes (`409` on terminal items). |
| `/workitems/{id}/review` | POST | `APPROVE` or `REJECT` a `MANUAL_REVIEW` item; reject sends the trade to the DLQ. |
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
- [`docs/phase-4-5-implementation-plan.md`](docs/phase-4-5-implementation-plan.md) - Implementation plan for Phase 4 (Nginx Gateway) & Phase 5 (CI/CD)
- [`docs/architecture.md`](docs/architecture.md) - event flow + service boundaries
- [`docs/kafka-topics.md`](docs/kafka-topics.md) - topics, keys, open decisions
- [`docs/project-status.md`](docs/project-status.md) - per-service progress and what remains
- [`services/workitem-service/README.md`](services/workitem-service/README.md) - work-item, manual review and DLQ/replay flows, rules and contracts
- [`docs/team-roles.md`](docs/team-roles.md) - roles, evaluation weighting, suggested task split
- [`CONTRIBUTING.md`](CONTRIBUTING.md) - branching, PRs, definition of done
