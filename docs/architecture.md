# Architecture Overview

Reference: capstone spec Section 5 (Architecture overview) and Section 8 (MVP scope).

## Flow (MVP)

```txt
Producer/Seed Loader --> [trade.raw] --> Ingestion Service --> CouchDB (raw + audit)
                                              |
                                              v
                                       [trade.accepted]
                                              |
                                              v
                                       Enrichment Service --(mock reference data)
                                              |
                                              v
                                       [trade.enriched]
                                              |
                                              v
                                  Validation Service (STP rules engine)
                                        |                  |
                                   OK --+--> Confirmation   +-- Fail --> Exception Service
                                        |    Generator                        |
                                        v                                     v
                              [confirmation.generated]          [exception.created]
                                                                              |
                                                                              v
                                                          Classification & Recommendation
                                                                              |
                                                                              v
                                                                  [exception.classified]
                                                                              |
                                                                              v
                                                              Work Item Linker (mock tracker)
                                                                              |
                                                                              v
                                                                     [workitem.linked]

Cross-cutting: Dead-letter Queue <-> Replay Controls <-> Audit Trail <-> Observability
Frontend (React + TS) reads outcomes via API/dashboards - it never creates trades.
```

See `docs/kafka-topics.md` for the full topic list.

## Service boundaries (initial split)

| Service | Owns | Spec refs |
| --- | --- | --- |
| `ingestion-service` | Producer/seed loader, inbound schema validation, raw persistence | FR1, FR2, FR3 |
| `enrichment-service` | Mock reference-data lookups (counterparty, instrument, template) | FR4 |
| `validation-service` | STP rules engine, confirmation generation | FR5, FR6 |
| `exception-service` | Exception capture, classification, recommendation | FR7, FR8, FR9 |
| `workitem-service` | Mock work-item tracker, audit trail, replay/DLQ handling | FR10, FR12 |
| `frontend` | Dashboards: lifecycle, exceptions, aged items, root-cause, replay | FR11 |
| `common-lib` | Shared FpML-inspired JSON DTOs, Kafka event contracts, constants | Section 10 |

This split is a starting point, not a contract - revisit it once the team has looked at the FpML-inspired JSON model together. If two services turn out to be trivially thin, merging them is fine; the evaluation rubric cares about clear service boundaries and event contracts, not service count.

## Implementation status

All 5 backend microservices, the shared core library, and the frontend operations cockpit are **100% implemented, tested, and integrated**:
- **`ingestion-service` (FR1-FR3):** Raw intake, FpML schema validation, idempotency filter, and raw persistence.
- **`enrichment-service` (FR4):** Reference data lookups, in-flight party LEI/name enrichment, and template resolution.
- **`validation-service` (FR5-FR6):** STP business rules engine, SHA-256 confirmation fingerprinting, and confirmation document generation.
- **`exception-service` (FR7-FR9):** Exception capture, taxonomy classification, SLA calculation, and recommendation engine.
- **`workitem-service` (FR10-FR12):** Work item tracker, manual review queue, immutable audit trail, and dead-letter queue (DLQ) replay.
- **`frontend` (FR11):** React 19 + TypeScript operations cockpit, topology visualizer, and diagnostics console.

Per-service progress and verification details are tracked in [`docs/project-status.md`](project-status.md) and [`docs/reports/code_consistency_report.md`](reports/code_consistency_report.md).
