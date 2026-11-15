# Architecture Overview

Reference: Capstone Specification Section 5 (Architecture Overview), Section 8 (MVP Scope), and Section 9 (Stretch Goals).

## 1. End-to-End Event Streaming & Data Flow

```txt
Producer / Seed Loader ──► [trade.raw] ──► Ingestion Service ──► CouchDB (trades, audit-events)
                                                │
                                                ▼
                                         [trade.accepted]
                                                │
                                                ▼
                                         Enrichment Service ──► CouchDB (enrichment-idempotency)
                                                │               (in-flight reference data lookups)
                                                ▼
                                         [trade.enriched]
                                                │
                                                ▼
                                   Validation Service (STP Rules Engine)
                                         │                           │
                            STP OK ──────┴─────► [stp.validated] ◄───┴────── STP Fail
                               │                                                │
                               ▼                                                ▼
                     [confirmation.generated]                          [exception.created]
                               │                                                │
                     CouchDB (confirmations)                                    ▼
                               │                                       Exception Service
                               │                               (Taxonomy, Confidence & SLAs)
                               │                                         │              │
                               │                                High Conf│              │Low Conf / Dispute
                               │                                         ▼              ▼
                               │                             [exception.classified]  [manual.review.required]
                               │                                         │              │
                               │                                         ▼              ▼
                               └───────────────────────────────► ┌──────────────────────────────────────┐
                                                                 │ Workitem Service & DLQ Replay Engine │
                                                                 │ - Work Item Tracker: workitem.linked │
                                                                 │ - 4-Eyes Manual Review Console       │
                                                                 │   ├─ APPROVE ──► Validation Confirms │
                                                                 │   └─ REJECT  ──► deadletter.trade    │
                                                                 │ - Controlled DLQ Replay ──► Repartition│
                                                                 │ - CouchDB: work-items, dlq-entries   │
                                                                 └──────────────────────────────────────┘

Cross-Cutting Infrastructure:
- Nginx API Gateway: Reverse-proxies /api/* routes to respective Spring Boot ports (8081-8085).
- Operations Frontend: React 19 + TypeScript cockpit (read-only monitoring, manual review & DLQ console).
- Kafka Broker (KRaft mode): 10 topics, 3 partitions per topic.
- CouchDB: 9 document databases for domain persistence, idempotency, and audit trails.
```

See [`docs/kafka-topics.md`](kafka-topics.md) for the full topic list and payload contracts.

## 2. Service Boundaries & Functional Mapping

| Service | Port | Primary Responsibilities | Key Spec Refs |
| --- | --- | --- | --- |
| **`ingestion-service`** | `8081` | Synthetic trade generator (10 scenarios), FpML schema validation, raw payload persistence, CouchDB idempotency deduplication | FR1, FR2, FR3 |
| **`enrichment-service`** | `8082` | Reference data joins (LEI, counterparty legal name, country, instrument, template resolution, STP eligibility) | FR4 |
| **`validation-service`** | `8083` | STP business rules engine, monospace ISDA confirmation generation, SHA-256 fingerprint deduplication, held trade confirmation | FR5, FR6 |
| **`exception-service`** | `8084` | Exception taxonomy classification, confidence scoring, dynamic SLA calculation (+4h to +7d), recommendation engine | FR7, FR8, FR9 |
| **`workitem-service`** | `8085` | Work item lifecycle tracking (`WI-XXXXX`), 4-eyes manual review approvals, immutable audit trail, DLQ store & controlled replay | FR10, FR12 |
| **`frontend`** | `3000` | Operations cockpit: real-time metrics, live event stream ticker, exceptions queue, review console, DLQ manager, service topology | FR11 |
| **`common-lib`** | — | Shared FpML DTOs, Kafka event contracts, topic constants, SHA-256 fingerprinting utility, schema router | Section 10 |

## 3. Persistent CouchDB Databases

The platform provisions and utilizes 9 dedicated CouchDB databases:

| Database | Maintained by | Purpose |
| --- | --- | --- |
| `trades` | `ingestion-service` | Raw inbound trade payloads, normalized trade documents, and processing statuses |
| `audit-events` | `ingestion`, `exception`, `workitem` | Chronological, immutable lifecycle audit trail (MiFID II / CFTC compliance) |
| `confirmations` | `validation-service` | Generated confirmation documents (`CONF-` records, plain-text bodies, fingerprints) and held trades |
| `exceptions` | `exception-service` | Classified exception documents, taxonomy details, recommendations, and SLA tracking |
| `work-items` | `workitem-service` | Actionable operator remediation tickets (`WI-` records, owner queues, statuses) |
| `dlq-entries` | `workitem-service` | Dead-letter payloads with full diagnostic stack traces, retry counts, and replay states |
| `stp-metrics` | `ingestion-service` | Historical aggregations and throughput snapshots |
| `enrichment-idempotency` | `enrichment-service` | Durable idempotency store preventing duplicate enrichment on network retries |
| `validation-idempotency` | `validation-service` | Durable idempotency store preventing duplicate exceptions or confirmations |

## 4. Implementation Status

All 5 backend microservices, the shared core library, and the frontend operations cockpit are **100% implemented, tested, and integrated**:
- **`ingestion-service` (FR1-FR3):** Raw intake, FpML schema validation, idempotency filter, and raw persistence (99 passing tests).
- **`enrichment-service` (FR4):** Reference data lookups, in-flight party LEI/name enrichment, and template resolution (16 passing tests).
- **`validation-service` (FR5-FR6):** STP business rules engine, SHA-256 confirmation fingerprinting, and confirmation document generation (145 passing tests).
- **`exception-service` (FR7-FR9):** Exception capture, taxonomy classification, SLA calculation, and recommendation engine (134 passing tests).
- **`workitem-service` (FR10-FR12):** Work item tracker, manual review queue, immutable audit trail, and dead-letter queue (DLQ) replay (84 passing tests).
- **`common-lib`:** Shared contracts, fingerprinting, and routing utility (34 passing tests).
- **`frontend` (FR11):** React 19 + TypeScript operations cockpit, dual Live/Demo modes, topology visualizer, and diagnostics console.

Per-service verification and architectural audit records are tracked in [`docs/project-status.md`](project-status.md) and [`docs/reports/code_consistency_report.md`](reports/code_consistency_report.md).
