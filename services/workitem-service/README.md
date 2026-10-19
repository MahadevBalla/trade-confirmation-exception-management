# workitem-service

Mock work-item tracker, manual review queue, audit trail and DLQ/replay handling (FR10, FR12). Port `8085`.

## Flows

```
exception.classified ───► ExceptionLinkingService ──► work item (create or link)
manual.review.required ─► ExceptionLinkingService ──► MANUAL_REVIEW work item ─► POST /workitems/{id}/review
                                                                                   ├ APPROVE → DONE
                                                                                   └ REJECT  → DONE + deadletter.trade
deadletter.trade ───────► DlqService ──► dlq-entries + DATA_FIX work item
                            └ trade has a REPLAYING entry? → merged into it (ReplayOutcomeService)
POST /dlq/{id}/replay ──► ReplayService ──► original topic (REPLAY message)
trade.accepted, trade.enriched, stp.validated,
exception.created, exception.classified ──► ReplayOutcomeService ──► REPLAY_SUCCEEDED + work item DONE
confirmation.generated ─► ConfirmationService ──► audit CONFIRMED + trade's open work items DONE
```

Every work-item create, link and update is written to `work-items`, audited in `audit-events` (same
`AUDIT_EVENT` shape as ingestion-service, so `GET /trades/{id}/audit` on ingestion shows both services'
entries) and published as a `WorkItemEvent` on `workitem.linked`.

## Rules

**Exception → work item** (`WorkItemRules`, owner-queue fallbacks mirror `frontend/src/services/recommendationEngine.ts`):

| Category | Type | Default queue |
| --- | --- | --- |
| MISSING_ECONOMIC_DATA | DATA_FIX | DATA_REMEDIATION |
| MISSING_STATIC_REFERENCE_DATA | DATA_FIX | REFERENCE_DATA |
| TEMPLATE_ISSUE | TEMPLATE_FIX | TEMPLATE_MAINTENANCE |
| BUSINESS_RULE_FAILURE | RULE_UPDATE | OPERATIONS_REVIEW |
| TECHNICAL_PROCESSING_FAILURE | ENHANCEMENT | TECHNOLOGY_SUPPORT |
| COUNTERPARTY_MATCHING_DISPUTE | MANUAL_REVIEW | OPERATIONS_REVIEW |
| LEGAL_DOCUMENTATION_GAP | MANUAL_REVIEW | SPECIALIST_REVIEW |
| FALSE_POSITIVE_CONFIRMABLE_EVENT | RULE_UPDATE | RULES_OWNER |

- The queue is the recommendation's `ownerQueue` when it is a known queue, else the default above.
- Priority = `operationalImpact` when it is LOW/MEDIUM/HIGH/CRITICAL, else HIGH/LOW from `resolutionDifficulty`, else MEDIUM.
- `classificationConfidence < workitem.low-confidence-threshold` (0.6) → MANUAL_REVIEW in OPERATIONS_REVIEW, so a person confirms the classification.
- **Link vs create**: an exception joins an OPEN/IN_PROGRESS item of the same type for the same trade (`linkedExceptionIds`, higher priority kept); otherwise a new item is created. Each (exception, type) pair is handled once.

**Work-item status**: `OPEN ↔ IN_PROGRESS → DONE | CANCELLED`. DONE and CANCELLED are terminal; `resolvedAt` is set on entry.

**DLQ entries**: replayable → `PENDING_REPLAY` + MEDIUM DATA_FIX item; not replayable or already at the limit → `PERMANENTLY_FAILED` + HIGH item. Owner queue `workitem.dlq-owner-queue` (TECHNOLOGY_SUPPORT).

**Replay**:
1. `POST /dlq/{id}/replay` marks the entry `REPLAYING` (count + 1) *before* publishing, then republishes `originalPayload` to `failedOnTopic` with `messageHeader.eventType=REPLAY`, the new `replayCount`, and the `tcem.replayCount` / `tcem.eventType` Kafka headers. The idempotency key includes the event type, so the REPLAY is not suppressed as a duplicate. A failed publish restores the previous state.
2. **Success**: a replay-marked record for the trade appears on a topic downstream of the failed one (`trade.raw` → `trade.accepted`/`exception.created`, `trade.accepted` → `trade.enriched`/`exception.created`, …). The entry becomes `REPLAY_SUCCEEDED` and its work item DONE.
3. **Failure**: a new dead letter for a trade whose entry is `REPLAYING` is merged into that entry (back to `PENDING_REPLAY`, count kept, appended to `failureHistory`) instead of creating a second entry. A `DLQ_ALIAS` marker under the new dead letter's id keeps redelivery idempotent and makes `GET /dlq/{thatId}` resolve to the merged entry.
4. When the failure is not replayable, or the count has reached `workitem.max-replay-attempts` (3), the entry becomes `PERMANENTLY_FAILED` and its existing work item is escalated to HIGH.

**Confirmation**: `confirmation.generated` is audited (CONFIRMED) and the trade's open work items are closed as DONE.

## REST API

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/workitems?status=&type=&ownerQueue=&priority=&tradeId=&page=&size=` | newest first |
| GET | `/workitems/summary` | counts by status and type, open counts by queue and priority, `agedOpen` (older than `workitem.aged-hours`) |
| GET | `/workitems/{id}` | `404` if unknown |
| POST | `/workitems` | `{tradeId, type, title, description, ownerQueue, priority, linkedExceptionId}`; `201`, `400` on invalid values |
| PATCH | `/workitems/{id}` | `{status, priority, ownerQueue, notes, actor}`, any subset; `409` on terminal items or invalid transitions |
| POST | `/workitems/{id}/review` | `{decision: APPROVE\|REJECT, notes, reviewer}`; MANUAL_REVIEW items only (`409` otherwise); notes required to reject |
| GET | `/dlq?status=&page=&size=` | `status`: `PENDING_REPLAY`, `REPLAYING`, `REPLAY_SUCCEEDED`, `PERMANENTLY_FAILED` |
| GET | `/dlq/{id}` | follows merge aliases; `404` if unknown |
| POST | `/dlq/{id}/replay` | `404` unknown; `409` not replayable / already replaying / already succeeded / limit reached; `502` publish failed |
| GET | `/actuator/health` | includes CouchDB connectivity |

Through the gateway these are `/api/workitems/...` and `/api/dlq/...`.

## Contracts with other services

- `exception.classified` and `manual.review.required`: `ExceptionEvent` (`{messageHeader, exception}`), keyed by tradeId.
- `confirmation.generated`: `ConfirmationEvent`; the trade id is taken from `confirmation.tradeId`, falling back to the key.
- Replay outcome detection needs downstream producers to key by tradeId and keep the incoming `messageHeader` (at least `eventType`/`replayCount`) on their output. See `docs/kafka-topics.md`.
- Approving a manual review only closes the item; generating the confirmation stays with validation-service, which can react to the DONE item on `workitem.linked`.

## Configuration

| Property | Env var | Default |
| --- | --- | --- |
| `workitem.max-replay-attempts` | `WORKITEM_MAX_REPLAY_ATTEMPTS` | `3` |
| `workitem.dlq-owner-queue` | `WORKITEM_DLQ_OWNER_QUEUE` | `TECHNOLOGY_SUPPORT` |
| `workitem.low-confidence-threshold` | `WORKITEM_LOW_CONFIDENCE_THRESHOLD` | `0.6` |
| `workitem.aged-hours` | `WORKITEM_AGED_HOURS` | `24` |
| `couchdb.url` / `username` / `password` | `COUCHDB_URL` / `COUCHDB_USER` / `COUCHDB_PASSWORD` | `http://localhost:5984` / `admin` / `password` |
| `spring.kafka.consumer.group-id` | `SPRING_KAFKA_CONSUMER_GROUP_ID` | `workitem-service-group` (plus `-exceptions`, `-manual-review`, `-progress` for the other listeners) |

## Run and test

```bash
mvn -f services/pom.xml -pl common-lib,workitem-service -am verify   # no broker or CouchDB needed
docker compose up -d kafka kafka-ui couchdb kafka-init couchdb-init
docker compose up -d --build ingestion-service workitem-service
curl -s localhost:8085/workitems/summary | jq
```

Tests run the real service graph (`TestWiring`) on `InMemoryCouchDbOperations` (a copy of ingestion-service's
test double) with a mocked `KafkaOperations`.

## Known limitations

- An entry stays `REPLAYING` until a downstream success or failure is seen; there is no timeout yet.
- Work items are linked per trade and type; systemic issues across trades (e.g. one missing template behind many trades) are not grouped.
- `GET /workitems/summary` scans up to 1000 work items.
