# Workitem Service (`workitem-service`)

> **Remediation Work-Item Tracking, 4-Eyes Manual Review Queue, Audit Trail & Dead-Letter Replay Engine for the TCEM Platform**  
> **Service Port:** `8085` · **Runtime:** Java 21 / Spring Boot 3.3 · **Storage:** Apache CouchDB 3.3 · **Streaming:** Apache Kafka 3.8

---

## 1. Overview & Purpose

The **`workitem-service`** serves as the operational remediation and exception lifecycle backbone of the TCEM platform:

1. **Remediation Work-Item Management:** Automatically generates and links trackable operational remediation tickets (`WI-40001` format) when exceptions are classified by `exception-service`.
2. **Four-Eyes Manual Review Queue:** Provides a secure workflow for low-confidence classifications, held trades, and counterparty disputes. When a supervisor signs off (`APPROVE`), it triggers `validation-service` to immediately generate the legal confirmation.
3. **Dead-Letter Queue (DLQ) & Replay Engine:** Captures malformed payloads, unhandled pipeline crashes, and processing failures from `deadletter.trade`. Manages bounded, idempotent replays back into upstream Kafka topics.
4. **Cross-Service Audit Ledger:** Persists chronological, immutable lifecycle audit records in CouchDB `audit-events` using the shared schema, enabling seamless timeline queries from ingestion to settlement.

---

## 2. Event Flows & Lifecycle

```
exception.classified ───► ExceptionLinkingService ──► Work Item (Create or Link)
manual.review.required ─► ExceptionLinkingService ──► MANUAL_REVIEW Work Item ──► POST /workitems/{id}/review
                                                                                    ├ APPROVE → Status DONE → validation-service confirms trade
                                                                                    └ REJECT  → Status DONE → Emits to deadletter.trade
deadletter.trade ───────► DlqService ───────────────► CouchDB dlq-entries + DATA_FIX Work Item
                            └ Replaying Entry? ─────► Merges into existing entry (ReplayOutcomeService)
POST /dlq/{id}/replay ──► ReplayService ────────────► Original Kafka Topic (REPLAY message)
trade.accepted, trade.enriched, stp.validated,
exception.created, exception.classified,
manual.review.required ──► ReplayOutcomeService ──► REPLAY_SUCCEEDED + Work Item DONE
stp.validated (cancelled=true) ─► CancellationService ──► Audit CANCELLED + Open Work Items CANCELLED
confirmation.generated ──► ConfirmationService ─────► Audit CONFIRMED + Open Work Items DONE
```

Every work-item creation, linkage, and status change is written to CouchDB `work-items`, recorded in `audit-events`, and published as a `WorkItemEvent` on `workitem.linked`.

---

## 3. Business Rules & Queue Mapping

### Exception to Work-Item Mapping (`WorkItemRules`)

When an exception arrives, it is mapped to a designated operational work queue and priority based on its taxonomy:

| Exception Category | Work-Item Type | Default Queue |
|---|---|---|
| `MISSING_ECONOMIC_DATA` | `DATA_FIX` | `DATA_REMEDIATION` |
| `MISSING_STATIC_REFERENCE_DATA` | `DATA_FIX` | `REFERENCE_DATA` |
| `TEMPLATE_ISSUE` | `TEMPLATE_FIX` | `TEMPLATE_MAINTENANCE` |
| `BUSINESS_RULE_FAILURE` | `RULE_UPDATE` | `OPERATIONS_REVIEW` |
| `TECHNICAL_PROCESSING_FAILURE` | `ENHANCEMENT` | `TECHNOLOGY_SUPPORT` |
| `COUNTERPARTY_MATCHING_DISPUTE` | `MANUAL_REVIEW` | `OPERATIONS_REVIEW` |
| `LEGAL_DOCUMENTATION_GAP` | `MANUAL_REVIEW` | `SPECIALIST_REVIEW` |
| `FALSE_POSITIVE_CONFIRMABLE_EVENT` | `RULE_UPDATE` | `RULES_OWNER` |

- **Queue Assignment:** If the upstream recommendation specifies a recognized `ownerQueue`, that queue takes precedence; otherwise, the taxonomy default above is assigned.
- **Priority Resolution:** Inherits `operationalImpact` (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`); falls back to `resolutionDifficulty` or defaults to `MEDIUM`.
- **Low-Confidence Gating:** If `classificationConfidence < 0.6`, the issue is routed to `MANUAL_REVIEW` in `OPERATIONS_REVIEW` so a human specialist validates the root cause before data modifications take place.
- **Link vs. Create:** If an active (`OPEN` or `IN_PROGRESS`) ticket of the same type already exists for the trade, the new exception is appended to `linkedExceptionIds` (retaining the higher priority). Otherwise, a new work item is provisioned.

### Lifecycle Status State Machine
$$\text{OPEN} \longleftrightarrow \text{IN\_PROGRESS} \longrightarrow \mathbf{\text{DONE}} \quad | \quad \mathbf{\text{CANCELLED}}$$
- `DONE` and `CANCELLED` are terminal states. Upon transition to a terminal state, `resolvedAt` is stamped and the ticket is locked.

---

## 4. Dead-Letter Queue (DLQ) & Controlled Replay Engine

When unparseable or unhandled payloads arrive on `deadletter.trade`:
1. **Entry Provisioning:** Replayable errors enter `PENDING_REPLAY` status with an associated `DATA_FIX` work item. Non-replayable errors (or those exceeding maximum retries) are marked `PERMANENTLY_FAILED` and escalated to `HIGH` priority.
2. **Replay Triggering (`POST /dlq/{id}/replay`):**
   - Atomically updates status to `REPLAYING` and increments `replayCount` *before* publishing to prevent duplicate inflight replays.
   - Republishes the payload to the original topic (`failedOnTopic`) with `messageHeader.eventType = REPLAY` and tracking headers (`tcem.replayCount`, `tcem.eventType`).
   - The composite idempotency key incorporates the event type, ensuring downstream services process the replayed event rather than discarding it as a duplicate.
3. **Success Detection:** When downstream services process the replayed trade and emit progress events (e.g. `trade.accepted`, `trade.enriched`, `confirmation.generated`), `ReplayOutcomeService` transitions the DLQ entry to `REPLAY_SUCCEEDED` and marks its associated work item as `DONE`.
4. **Failure Merging & Retry Limits:** If a replayed trade fails again, the new dead-letter event is automatically merged into the existing DLQ history rather than creating duplicate entries. Once `workitem.max-replay-attempts` (default: 3) is reached, the entry transitions to `PERMANENTLY_FAILED`.

---

## 5. Operations REST APIs (Port 8085)

The service exposes comprehensive REST management endpoints:

| Method | Path | Description |
|---|---|---|
| `GET` | `/workitems?status=&type=&ownerQueue=&priority=&tradeId=&page=&size=` | Paginated query of work items, ordered newest-first. |
| `GET` | `/workitems/summary` | Summary counters by status, type, priority, and aged open items (>24h). |
| `GET` | `/workitems/{id}` | Retrieves a single work-item document by ID. |
| `POST` | `/workitems` | Creates a manual work item (`tradeId`, `type`, `title`, `description`, `ownerQueue`, `priority`). |
| `PATCH` | `/workitems/{id}` | Updates ticket status, priority, owner queue, or operator notes. |
| `POST` | `/workitems/{id}/review` | Four-Eyes Review: submits supervisor `decision` (`APPROVE` or `REJECT`) with rationale notes. |
| `GET` | `/dlq?status=&page=&size=` | Paginated DLQ entries (`PENDING_REPLAY`, `REPLAYING`, `REPLAY_SUCCEEDED`, `PERMANENTLY_FAILED`). |
| `GET` | `/dlq/{id}` | Retrieves dead-letter document by ID (automatically resolves merge aliases). |
| `POST` | `/dlq/{id}/replay` | Initiates a controlled replay to the original Kafka topic. |
| `GET` | `/actuator/health` | Spring Boot Actuator health check (verifies CouchDB connectivity). |

*Note: Through the Nginx API gateway, these endpoints are mapped under `/api/workitems/*` and `/api/dlq/*`.*

---

## 6. Downstream & Peer Contracts

- **`exception.classified` & `manual.review.required`:** Consumes `ExceptionEvent` keyed by `tradeId`.
- **`confirmation.generated`:** Consumes `ConfirmationEvent`, logs `CONFIRMED` audit entry, and automatically resolves any remaining open work items for the trade.
- **`workitem.linked`:** When an operator approves a manual review, publishes `reviewDecision = APPROVED` on `workitem.linked`. `validation-service` listens to this event, fetches the held trade from CouchDB, and automatically generates the confirmation.
- **Replay Handshake:** Downstream microservices preserve the `messageHeader` (`eventType=REPLAY` and `replayCount`) when processing, allowing `ReplayOutcomeService` to accurately correlate outcomes.

---

## 7. Configuration Properties

| Property | Default Value | Environment Variable | Description |
|---|---|---|---|
| Server Port | `8085` | `SERVER_PORT` | HTTP port for REST APIs. |
| Kafka Bootstrap | `localhost:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker connection string. |
| Consumer Group | `workitem-service-group` | `SPRING_KAFKA_CONSUMER_GROUP_ID` | Base consumer group ID. |
| CouchDB URL | `http://localhost:5984` | `COUCHDB_URL` | CouchDB endpoint. |
| Max Replay Attempts | `3` | `WORKITEM_MAX_REPLAY_ATTEMPTS` | Maximum allowed DLQ replay retries before permanent failure. |
| DLQ Owner Queue | `TECHNOLOGY_SUPPORT` | `WORKITEM_DLQ_OWNER_QUEUE` | Default queue for technical dead-letter tickets. |
| Low-Confidence Threshold | `0.6` | `WORKITEM_LOW_CONFIDENCE_THRESHOLD` | Threshold triggering 4-eyes manual review routing. |
| Aged Hours Threshold | `24` | `WORKITEM_AGED_HOURS` | Age in hours after which open tickets are flagged as aged. |

---

## 8. Automated Testing & Verification

```bash
mvn -f services/pom.xml -pl common-lib,workitem-service -am verify
```
* **Coverage:** 118 passing unit and integration tests covering work-item creation, rule mapping, review approvals, DLQ aliasing, idempotency keys, and REST controller operations.
* **Hermetic Execution:** Utilizes `InMemoryCouchDbOperations` and mocked Kafka producers to run the entire service graph in memory without requiring external daemon dependencies.
