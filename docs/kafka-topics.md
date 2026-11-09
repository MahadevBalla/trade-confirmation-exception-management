# Kafka Topics

From the capstone spec's architecture diagram (Section 5) and MVP scope (Section 8). Minimum three topics required; the spec explicitly encourages more if useful - this list is the superset mentioned in the diagram. Trim or extend once the team finalizes the event flow.

| Topic | Produced by | Consumed by | Notes |
| --- | --- | --- | --- |
| `trade.raw` | Producer / seed loader | ingestion-service | Raw FpML-inspired JSON as received |
| `trade.accepted` | ingestion-service | enrichment-service; workitem-service (replay outcome) | Passed schema validation (FR2) |
| `trade.enriched` | enrichment-service | validation-service; workitem-service (replay outcome) | After reference-data enrichment (FR4) |
| `stp.validated` | validation-service | (fan-out: confirmation path); workitem-service (replay outcome; cancels open work items when `cancelled=true`) | STP decision made (FR5), or a cancelled outcome for `CANCEL_TRADE` |
| `confirmation.generated` | validation-service | workitem-service (audit, closes the trade's open work items) | Confirmation output produced (FR6); payload `ConfirmationEvent` |
| `exception.created` | validation-service | exception-service; workitem-service (replay outcome) | Raised when STP fails (FR7) |
| `exception.classified` | exception-service | workitem-service (work items; replay outcome) | Theme/sub-theme + recommendation attached (FR8, FR9); payload `ExceptionEvent` |
| `workitem.linked` | workitem-service | validation-service (confirms approved manual reviews); audit / UI | Work item created, linked or updated (FR10); payload `WorkItemEvent`, whose `workItem.reviewDecision` is `APPROVED` or `REJECTED` after a manual review |
| `manual.review.required` | exception-service | workitem-service (manual review queue; replay outcome) | Routed to manual review queue; payload `ExceptionEvent` (same envelope as `exception.classified`) |
| `deadletter.trade` | any service (on failure); workitem-service (rejected manual reviews) | workitem-service (DLQ store + replay) | Irrecoverable messages with diagnostic context (FR12); payload `DeadLetterEvent`, key = tradeId (`workitem-service` stores the key as the trade id, so any other key breaks replay tracking) |

workitem-service watches the "replay outcome" topics only to learn whether a replayed message got past the stage it failed in. It reacts only to records marked as replays (`messageHeader.eventType=REPLAY`, `replayCount > 0`, or the `tcem.replayCount` header), so producers on those topics should preserve the incoming `messageHeader` (or at least its `eventType`/`replayCount`) and key records by tradeId.

A replayed message has `eventType=REPLAY` and keeps its original `messageId`, so every replay attempt has the same `IdempotencyKey`. Consumers therefore skip their duplicate check for `REPLAY` messages. `ingestion-service`, `enrichment-service`, `validation-service` and `exception-service` all do this, and they keep `eventType` and `replayCount` on the events they publish.

Consumers bind their input topic through the `TopicNames` constants in `common-lib` (there are no `*.input-topic` property overrides), so a topic rename is a single code change and a consumer can never drift from what its producers publish to.

## Finalized Architectural Decisions

- **Partition Key Strategy:** Keyed by `tradeId` across all 10 topics to guarantee strict per-trade causal ordering across partitions.
- **Partition Count:** Exactly **3 partitions per topic** pre-provisioned via `tcem-kafka-init` (single replication factor for local KRaft cluster; enables horizontal consumer scaling).
- **Consumer Group Naming:** Standardized per-service groups: `ingestion-service-group`, `enrichment-service-group`, `validation-service-group`, `exception-service-group`, and `workitem-service-group` (with isolated dedicated listener groups for manual review and progress tracking).
- **Topic Separation:** `confirmation.generated` and `exception.created` are separate topics (rather than a unified outcome topic) to maintain clean service boundaries and prevent domain event pollution.
- **Replay & Idempotency Strategy:** Replayed records preserve original headers with `eventType=REPLAY` and `replayCount` incremented; downstream consumers (`ingestion`, `enrichment`, `validation`, `exception`) skip duplicate checks on `REPLAY`.
- **Cancellation Event Cascading:** `CANCEL_TRADE` publishes an `StpValidatedEvent` on `stp.validated` with `cancelled=true`, which cascades to cancel open work items in `workitem-service` and emits audit logs.
