# Kafka Topics

From the capstone spec's architecture diagram (Section 5) and MVP scope (Section 8). Minimum three topics required; the spec explicitly encourages more if useful - this list is the superset mentioned in the diagram. Trim or extend once the team finalizes the event flow.

| Topic | Produced by | Consumed by | Notes |
| --- | --- | --- | --- |
| `trade.raw` | Producer / seed loader | ingestion-service | Raw FpML-inspired JSON as received |
| `trade.accepted` | ingestion-service | enrichment-service; workitem-service (replay outcome) | Passed schema validation (FR2) |
| `trade.enriched` | enrichment-service | validation-service; workitem-service (replay outcome) | After reference-data enrichment (FR4) |
| `stp.validated` | validation-service | (fan-out: confirmation path); workitem-service (replay outcome) | STP decision made (FR5) |
| `confirmation.generated` | validation-service | workitem-service (audit, closes the trade's open work items) | Confirmation output produced (FR6); payload `ConfirmationEvent` |
| `exception.created` | validation-service | exception-service; workitem-service (replay outcome) | Raised when STP fails (FR7) |
| `exception.classified` | exception-service | workitem-service | Theme/sub-theme + recommendation attached (FR8, FR9); payload `ExceptionEvent` |
| `workitem.linked` | workitem-service | (audit / UI) | Work item created, linked or updated (FR10); payload `WorkItemEvent` |
| `manual.review.required` | exception-service | workitem-service (manual review queue) | Routed to manual review queue; payload `ExceptionEvent` (same envelope as `exception.classified`) |
| `deadletter.trade` | any service (on failure); workitem-service (rejected manual reviews) | workitem-service (DLQ store + replay) | Irrecoverable messages with diagnostic context (FR12); payload `DeadLetterEvent`, key = tradeId |

workitem-service watches the "replay outcome" topics only to learn whether a replayed message got past the stage it failed in. It reacts only to records marked as replays (`messageHeader.eventType=REPLAY`, `replayCount > 0`, or the `tcem.replayCount` header), so producers on those topics should preserve the incoming `messageHeader` (or at least its `eventType`/`replayCount`) and key records by tradeId.

## Decisions still open for the team

- Exact key strategy (likely `tradeId` for ordering/partitioning per trade).
- Number of partitions per topic (tie to the partitioning-strategy stretch goal).
- Consumer group naming convention (suggest one group per service, e.g. `enrichment-service-group`).
- Whether `confirmation.generated` and `exception.created` should be two separate topics or a single `stp.outcome` topic with a status field - spec allows either; pick one before wiring producers/consumers to avoid churn later.

Record the final decision here once made, with a one-line rationale.
