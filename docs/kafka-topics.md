# Kafka Topics

From the capstone spec's architecture diagram (Section 5) and MVP scope (Section 8). Minimum three topics required; the spec explicitly encourages more if useful - this list is the superset mentioned in the diagram. Trim or extend once the team finalizes the event flow.

| Topic | Produced by | Consumed by | Notes |
| --- | --- | --- | --- |
| `trade.raw` | Producer / seed loader | ingestion-service | Raw FpML-inspired JSON as received |
| `trade.accepted` | ingestion-service | enrichment-service | Passed schema validation (FR2) |
| `trade.enriched` | enrichment-service | validation-service | After reference-data enrichment (FR4) |
| `stp.validated` | validation-service | (fan-out: confirmation path) | STP decision made (FR5) |
| `confirmation.generated` | validation-service | workitem-service (audit) | Confirmation output produced (FR6) |
| `exception.created` | validation-service | exception-service | Raised when STP fails (FR7) |
| `exception.classified` | exception-service | workitem-service | Theme/sub-theme + recommendation attached (FR8, FR9) |
| `workitem.linked` | workitem-service | (audit / UI) | Work item created or linked (FR10) |
| `manual.review.required` | exception-service | (UI / workitem-service) | Routed to manual review queue |
| `deadletter.trade` | any service (on failure) | workitem-service (audit) / replay tool | Irrecoverable messages with diagnostic context (FR12) |

## Decisions still open for the team

- Exact key strategy (likely `tradeId` for ordering/partitioning per trade).
- Number of partitions per topic (tie to the partitioning-strategy stretch goal).
- Consumer group naming convention (suggest one group per service, e.g. `enrichment-service-group`).
- Whether `confirmation.generated` and `exception.created` should be two separate topics or a single `stp.outcome` topic with a status field - spec allows either; pick one before wiring producers/consumers to avoid churn later.

Record the final decision here once made, with a one-line rationale.
