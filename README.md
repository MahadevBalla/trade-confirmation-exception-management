# Trade Confirmation Exception Management Platform

Capstone project: an event-driven platform that consumes synthetic
post-trade messages, enriches and validates them, generates confirmations
where possible, captures and classifies exceptions where not, links them to
mock work items, and gives operations users a dashboard-driven view. No
UI-based trade entry - all trade intake is event/message-driven.

Stack: **Apache Kafka** (event streaming) · **Apache CouchDB** (document
store) · **Java / Spring Boot** (microservices) · **React + TypeScript**
(operations UI) · **Docker Compose** (local orchestration).

> Status: repo skeleton only. Directories, build files, and empty-but-runnable
> apps are set up; no feature logic is implemented yet. See `docs/` for the
> architecture and task breakdown, and `CONTRIBUTING.md` before opening a PR.

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
.github/                   # CI workflows + PR template
```

Each backend service is a normal Spring Boot app right now - it starts and
answers `/actuator/health`, nothing else. Fill in controllers, Kafka
listeners/producers, and CouchDB access as tasks get assigned.

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
docker compose up -d kafka kafka-ui couchdb
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

## Frontend development

```bash
cd frontend
npm install
npm run dev      # Vite dev server on :5173
```

## Docs

- [`docs/architecture.md`](docs/architecture.md) - event flow + service boundaries
- [`docs/kafka-topics.md`](docs/kafka-topics.md) - topics, keys, open decisions
- [`docs/team-roles.md`](docs/team-roles.md) - roles, evaluation weighting, suggested task split
- [`CONTRIBUTING.md`](CONTRIBUTING.md) - branching, PRs, definition of done
