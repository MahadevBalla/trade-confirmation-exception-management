# TCEM Code Consistency & System Risk Report

**Audit Mode:** Multi-Phase Architecture & Code Consistency Audit  
**Audit Status:** All 5 Audit Phases Complete + 7 Showstoppers + 20 Sprint 2 Fixes Remediated (Branches `fix/code-consistency`, `feat/frontend-live-data`)  
**Target Repository:** `trade-confirmation-exception-management`  
**Live Demo Guide:** [`live_demo_presentation_guide.md`](./live_demo_presentation_guide.md)  
**Remediation Guide:** [`presentation_fixes_guide.md`](./presentation_fixes_guide.md)  

---

## Risk Categorization Scale

| Severity Level | Definition & Operational Impact |
| :--- | :--- |
| 🔴 **Breaks the System** | Critical functional, runtime, or contract failure. Leads to crashes, silent data corruption, deserialization failure, or broken business logic (e.g. duplicate storms, stranded trades, failed suppression, broken replay loops, disconnected client state). |
| 🟠 **High / Moderate Risk** | Latent defects, edge-case failures, architectural deviations, or broken contract propagation that will fail under stress, recovery, or schema version bumps. |
| 🟡 **Low Risk / Inconsistency** | Configuration drift, dead code, redundant boilerplate, or unstandardized property wiring that doesn't immediately crash the system but degrades maintainability. |
| 🟢 **Not Breaking / Cosmetic** | Documentation drift, naming style differences, comment typos, or minor cosmetic discrepancies. |

---

## Executive Summary: Full Scope & Remediation

- **Phase 1 (Completed):** Audited `common-lib`, Maven reactor setup, Kafka topic definitions, canonical event models, fingerprinting algorithm, and TypeScript type alignment.
- **Phase 2 (Completed):** Audited ingestion and validation pipelines (`ingestion-service` & `validation-service`), STP rules engine, edge validations, confirmation generation, and Mango queries.
- **Phase 3 (Completed):** Audited reference data enrichment (`enrichment-service`), deterministic exception classification and SLA calculation (`exception-service`), and work item tracking, manual review, and DLQ replay orchestration (`workitem-service`).
- **Phase 4 (Completed):** Audited frontend React 19 application (`frontend`), Vite and Nginx reverse proxies, TypeScript types vs Java DTOs, `AppContext` state management, recommendation engine parity, and backend API integration.
- **Phase 5 (Completed):** Audited DevOps infrastructure, Docker orchestration, initializers, JVM memory flags, and GitHub Actions CI.
- **Remediation Sprint (Completed):** Fixed 7 critical demo-breaking showstoppers on `fix/code-consistency` (Docker build `.dockerignore`, Kafka persistence volume, CouchDB 25-limit cap, TypeScript enum alignment, DLQ dual-property models, enriched counterparty name display, and database provisioning). System health score upgraded from **68/100 to 86/100**.
- **Sprint 2 Remediation (In Progress):** Resolved C1-01 (fingerprint party paths), C1-03 (validation idempotency), C1-04 (`SchemaVersionRouter` in validation), C1-05 (`common-lib` test suite), C1-06 (Kafka topic wiring), C2-01, C3-02 and C3-03 (DLQ replay loop), C3-01 (manual review approval), C3-09 (`IdempotencyKey` Javadoc), C5-03, C5-05 and C5-06 (DevOps), and all remaining high-severity findings: C2-03, C2-04, C3-04, C3-05, C4-04 and C5-04, on `fix/code-consistency`. C5-02 was reopened and fixed properly, and the C5-08 `kafka-ui` tag is now pinned. C4-01 (live data in the UI) is resolved on `feat/frontend-live-data`. **30 of 37 findings are now resolved; 7 remain, all low severity or cosmetic** (see the risk matrix). Remediation also found further issues, recorded in [Findings Added During Sprint 2 Remediation](#findings-added-during-sprint-2-remediation).

---

## Detailed Findings & Risk Analysis

### Phase 1 Findings: Shared Core Library, Kafka Topics & Event Contracts

### 1. 🔴 BREAKS THE SYSTEM: Algorithmic Failure in `ConfirmationFingerprint` for Counterparty/Bank Party Changes — ✅ [RESOLVED in `fix/code-consistency`: Commit `a2cb6d6`]
- **Location:** [ConfirmationFingerprint.java:L106-L125](../../services/common-lib/src/main/java/com/tcem/common/registry/ConfirmationFingerprint.java#L106-L125) and [ConfirmableFieldRegistry.java:L72-L73](../../services/common-lib/src/main/java/com/tcem/common/registry/ConfirmableFieldRegistry.java#L72-L73).
- **Issue:** 
  `ConfirmableFieldRegistry` registers `"parties[COUNTERPARTY].partyId"` and `"parties[BANK].partyId"` as confirmable fields. However, `ConfirmationFingerprint.extractValue()` parses paths only by dot-separation (`String[] parts = dotPath.split("\\.")`) and executes `root.get(part)`. Because `parties` is a JSON Array (`List<Party>`), `root.get("parties[COUNTERPARTY]")` returns `null`. 
- **System Impact:**
  `extractValue` always evaluates both party IDs to `__NULL__`. If a trade is amended with a counterparty novation or party identifier update, `ConfirmationFingerprint.compute()` yields the exact same SHA-256 hash as the previous version. The system **falsely suppresses the confirmation generation**, leaving the new counterparty without a legal confirmation document.
- **Recommended Remediation:**
  Refactor `extractValue()` in `ConfirmationFingerprint` to parse array predicates (e.g. `parties[partyRole=COUNTERPARTY].partyId`) by iterating over the `parties` JsonNode array, matching `partyRole`, and extracting `partyId`.

- **Resolution:**
  `ConfirmationFingerprint.extractValue()` now understands array selectors: the shorthand `parties[COUNTERPARTY].partyId` and the explicit form `parties[partyRole=COUNTERPARTY].partyId` select the first element of the array whose `partyRole` matches. Missing parties still resolve to the `__NULL__` marker. Covered by `ConfirmationFingerprintTest`, including a test asserting that every `FX_OPTION` / `FX_FORWARD` registry path resolves on a fully populated trade.
  *Behaviour note:* fingerprints now include party IDs, so a confirmation stored before this fix has a different fingerprint; the first amendment of such a trade generates one new confirmation (one-time).

---

### 2. 🔴 BREAKS THE SYSTEM: Enum Contract Mismatch Between Backend (`common-lib`) and Frontend (`tcem.ts`) — ✅ [RESOLVED in `fix/code-consistency`: Commit `c43263d`]
- **Location:** [ProductType.java:L23](../../services/common-lib/src/main/java/com/tcem/common/constants/ProductType.java#L23) vs [frontend/src/types/tcem.ts:L10](../../frontend/src/types/tcem.ts#L10) and [EventType.java:L28-L34](../../services/common-lib/src/main/java/com/tcem/common/constants/EventType.java#L28-L34) vs [frontend/src/types/tcem.ts:L12-L17](../../frontend/src/types/tcem.ts#L12-L17).
- **Issues:**
  1. `ProductType`: Backend defines `IRS`, `FX_FORWARD`, `UNKNOWN`. Frontend defines `IR_SWAP` (mismatched name with `IRS`) and omits `FX_FORWARD` and `UNKNOWN`. Any IRS payload will fail frontend deserialization/filtering.
  2. `EventType`: Backend defines `NOVATION` and `COMPRESSION`. Frontend omits both and introduces `HEARTBEAT` (which is nonexistent in the backend).
- **System Impact:**
  TypeScript runtime / UI render errors, filter breakage, and API payload rejection when communicating between the frontend and Spring Boot microservices.
- **Recommended Remediation:**
  Align [frontend/src/types/tcem.ts](../../frontend/src/types/tcem.ts) with the canonical Java enum values.

---

### 3. 🔴 BREAKS THE SYSTEM: Flawed Idempotency Architecture in `validation-service` Causing Duplicate Exception Storms — ✅ [RESOLVED in `fix/code-consistency`: Commits `537f353`, `bda73d5`]
- **Location:** [TradeEnrichedConsumer.java:L149-L163](../../services/validation-service/src/main/java/com/tcem/validation/consumer/TradeEnrichedConsumer.java#L149-L163).
- **Issue:**
  While `ingestion-service`, `enrichment-service`, and `exception-service` consistently utilize `IdempotencyKey` and persist keys to CouchDB (`idempotency:{tradeId}:{messageId}:{schemaVersion}:{eventType}`), `validation-service` skips `IdempotencyKey` entirely. Instead, it checks `Optional<ConfirmationDocument> existing = confirmationRepository.findLatestForTrade(tradeId)`.
- **System Impact:**
  If a trade failed STP, no `ConfirmationDocument` exists. When a duplicate message arrives (via Kafka rebalance, redelivery, or network retry), `findLatestForTrade` returns empty. `validation-service` re-validates the trade, re-fails STP, and produces a duplicate `exception.created` event, which leads to duplicate work items and skewed analytics.
- **Recommended Remediation:**
  Integrate `IdempotencyKey` check and recording in `validation-service` before running STP rules, storing the processing outcome in an `idempotency` collection.

- **Resolution:**
  `validation-service` now builds the shared `IdempotencyKey` and persists it through the new `ValidationIdempotencyRepository` in a dedicated `validation-idempotency` CouchDB database (provisioned in `scripts/init-couchdb.sh` and `couchdb-init`, commit `bda73d5`; also auto-created on startup). The key is checked before STP runs (duplicates are skipped) and recorded only after every downstream publish succeeds, so a failed attempt is retried on redelivery or replay. `REPLAY` events bypass the check, and the version-based confirmation guard is kept as a second line of defence. Messages missing `tradeId`, `messageId`, `schemaVersion` or `eventType` are dead-lettered (same as `enrichment-service`).
  *Verified live:* re-publishing an already-processed `trade.enriched` message logs `Idempotency: duplicate message ... skipping` and leaves the `validation-idempotency` document count unchanged.
  *Known limitation:* the key is written after publishing, so a crash between publish and record can still produce one duplicate on redelivery (at-least-once semantics, identical to `enrichment-service`).

---

### 4. 🟠 HIGH / MODERATE RISK: Bypassing `SchemaVersionRouter` in `validation-service` — ✅ [RESOLVED in `fix/code-consistency`: Commit `537f353`]
- **Location:** [TradeEnrichedConsumer.java:L114-L115](../../services/validation-service/src/main/java/com/tcem/validation/consumer/TradeEnrichedConsumer.java#L114-L115).
- **Issue:**
  `common-lib` provides `SchemaVersionRouter` to enforce backward compatibility, schema version validation, and migration of legacy payloads. While `enrichment-service` properly routes incoming payloads through `SchemaVersionRouter(objectMapper).route(record.value())`, `validation-service` directly calls `objectMapper.readValue(payload, TradeEvent.class)`.
- **System Impact:**
  Incoming messages with unsupported schema versions or requiring migrations will cause unhandled parsing exceptions or silent field drops rather than structured DLQ routing with `SCHEMA_VERSION_UNSUPPORTED`.
- **Recommended Remediation:**
  Update `TradeEnrichedConsumer` to utilize `SchemaVersionRouter` just like `TradeAcceptedConsumer`.

- **Resolution:**
  `TradeEnrichedConsumer` now parses payloads through `SchemaVersionRouter`. An unsupported `schemaVersion` is routed to the DLQ with a `SCHEMA_VERSION_UNSUPPORTED` failure reason and flagged non-replayable (a replay would fail again); the original header is extracted for correlation. Unit-tested, and verified live: a payload with `schemaVersion` `capstone-fpml-json-9.9` produced a DLQ entry with that reason.

---

### 5. 🟠 HIGH / MODERATE RISK: Zero Unit Test Coverage in `common-lib` — ✅ [RESOLVED in `fix/code-consistency`: Commit `8b00f86`]
- **Location:** `services/common-lib/src/test` (directory is absent).
- **Issue:**
  `common-lib` contains foundational business logic including SHA-256 fingerprint generation, confirmable field definitions, schema routing, and idempotency key hashing. Yet it contains zero automated unit or integration tests.
- **System Impact:**
  Regressions and algorithmic defects (like the `parties[COUNTERPARTY]` bug found in Issue #1) go completely undetected during build time.
- **Recommended Remediation:**
  Add a comprehensive test suite in `services/common-lib/src/test/java/com/tcem/common/...` validating `ConfirmationFingerprint`, `SchemaVersionRouter`, `IdempotencyKey`, and `ConfirmableFieldRegistry`.

- **Resolution:**
  `common-lib` now has test-scope dependencies (`junit-jupiter`, `assertj-core`, `jackson-datatype-jsr310`, versions managed by the Spring Boot BOM) and 34 tests: `ConfirmationFingerprintTest` (15), `ConfirmableFieldRegistryTest` (6), `IdempotencyKeyTest` (6) and `SchemaVersionRouterTest` (7). CI (`mvn verify` on the reactor) now runs them.

---

### 6. 🟡 LOW RISK / INCONSISTENCY: Discrepancy Between YAML Configuration and `@KafkaListener` Topic Declarations — ✅ [RESOLVED in `fix/code-consistency`: Commit `970ecf6`]
- **Location:** [services/validation-service/src/main/resources/application.yml:L33](../../services/validation-service/src/main/resources/application.yml#L33) vs [TradeEnrichedConsumer.java:L106](../../services/validation-service/src/main/java/com/tcem/validation/consumer/TradeEnrichedConsumer.java#L106).
- **Issue:**
  `application.yml` defines `validation.input-topic: ${VALIDATION_INPUT_TOPIC:trade.enriched}`, but `TradeEnrichedConsumer` uses the compile-time constant `TopicNames.TRADE_ENRICHED`. Conversely, `TradeAcceptedConsumer` in `enrichment-service` uses `@KafkaListener(topics = "${enrichment.input-topic:trade.accepted}", ...)`.
- **System Impact:**
  The property in `validation-service`'s `application.yml` is dead configuration. Operators overriding `VALIDATION_INPUT_TOPIC` via environment variables will find it has zero effect.
- **Recommended Remediation:**
  Standardize across all microservices: either consistently allow Spring expression injection with fallback to `TopicNames.*` or standardize strictly on `TopicNames` constants.

- **Resolution:**
  Standardized strictly on the `TopicNames` constants. The dead `validation.input-topic` property was removed (`application.yml` and `ValidationProperties`) and `TradeAcceptedConsumer` now uses `TopicNames.TRADE_ACCEPTED` instead of `${enrichment.input-topic:trade.accepted}`. Rationale: all producers use the constants and `kafka-init` provisions a fixed topic list, so overriding a single consumer's topic would silently desynchronize the pipeline. The `VALIDATION_INPUT_TOPIC` and `enrichment.input-topic` overrides no longer exist.

---

### 7. 🟢 NOT BREAKING / COSMETIC: Documentation Drift in `docs/architecture.md`
- **Location:** [docs/architecture.md:L61-L63](../../docs/architecture.md#L61-L63).
- **Issue:**
  Line 62 states that `enrichment-service`, `validation-service`, and `exception-service` are merely "stubs answering only `/actuator/health`". In reality, all three services are fully implemented with business logic, CouchDB persistence, and Kafka pipelines.
- **System Impact:**
  None on runtime; causes confusion for developers and evaluators reviewing the codebase.
- **Recommended Remediation:**
  Update `docs/architecture.md` to reflect current 100% implementation status as tracked in `docs/project-status.md`.

---

### Phase 2 Findings: Ingestion & Validation Pipeline

### 8. 🔴 BREAKS THE SYSTEM: Lost Replay Context & Event Type in `validation-service` Breaking DLQ Resolution — ✅ [RESOLVED in `fix/code-consistency`: Commit `8b4435d`]
- **Location:** [ConfirmationService.java:L158-L164](../../services/validation-service/src/main/java/com/tcem/validation/confirmation/ConfirmationService.java#L158-L164) and [StpExceptionMapper.java:L111-L118](../../services/validation-service/src/main/java/com/tcem/validation/exception/StpExceptionMapper.java#L111-L118).
- **Issue:**
  When `validation-service` builds outbound `MessageHeader`s for `confirmation.generated` or `exception.created`, `outboundHeader.setEventType(...)` is omitted (remaining `null`), and `inboundHeader.getReplayCount()` is never propagated. In addition, neither producer sets the Kafka record header `tcem.replayCount`.
- **System Impact:**
  In `workitem-service`, `ReplayOutcomeService.onPipelineMessage()` checks whether `hasReplayMarker(value)` (`eventType == REPLAY || replayCount > 0`) or `tcem.replayCount` is present. Because `validation-service` strips these attributes, `workitem-service` **fails to detect that a replayed trade was resolved**. The DLQ entry remains permanently stuck in `REPLAYING` status, and open work items are never closed upon successful confirmation of a replayed trade. This directly breaks FR12.
- **Recommended Remediation:**
  Propagate `inboundHeader.getEventType()` and `inboundHeader.getReplayCount()` into the outbound `MessageHeader`, and attach the `HeaderKeys.REPLAY_COUNT` record header in `ConfirmationGeneratedProducer` and `ExceptionCreatedProducer`.
- **Resolution:**
  `TradeEnrichedConsumer`, `ConfirmationService` and `StpExceptionMapper` now copy `eventType` and `replayCount` from the inbound header onto `stp.validated`, `confirmation.generated` and `exception.created`. The `tcem.replayCount` record header was not added because `ReplayOutcomeService` already accepts the payload marker. Fixing this alone was not enough: see the dead-letter key entry in [Findings Added During Sprint 2 Remediation](#findings-added-during-sprint-2-remediation).

---

### 9. 🔴 BREAKS THE SYSTEM: Hardcoded Default Mango Limit in `ConfirmationCouchDbRepository.countAll()` Caps Metrics at 25 — ✅ [RESOLVED in `fix/code-consistency`: Commit `2c4b3aa`]
- **Location:** [ConfirmationCouchDbRepository.java:L97-L102](../../services/validation-service/src/main/java/com/tcem/validation/repository/ConfirmationCouchDbRepository.java#L97-L102).
- **Issue:**
  `countAll()` constructs a `FindQueryRequest(selector)` where `limit` is `null`. CouchDB defaults Mango `_find` queries without a limit to **25 records**. Calling `.size()` on the returned list will therefore never exceed 25.
- **System Impact:**
  `GET /metrics/stp-rate` and operations dashboards display a hard cap of 25 confirmations regardless of whether 50, 500, or 10,000 trades were confirmed, corrupting platform analytics and STP throughput tracking.
- **Recommended Remediation:**
  Do not use in-memory `.size()` on unbounded `_find` queries. Query the CouchDB database info endpoint `GET /{db}` (which returns `doc_count` in O(1)) or query a CouchDB reduce view (`_count`).

---

### 10. 🟠 HIGH / MODERATE RISK: Functional Duplication & Dead Rule Between `TradeValidator` and `EconomicFieldsRule` — ✅ [RESOLVED in `fix/code-consistency`]
- **Location:** [TradeValidator.java:L389-L440](../../services/ingestion-service/src/main/java/com/tcem/ingestion/validator/TradeValidator.java#L389-L440) vs [EconomicFieldsRule.java:L42-L60](../../services/validation-service/src/main/java/com/tcem/validation/rules/EconomicFieldsRule.java#L42-L60).
- **Issue:**
  `TradeValidator` in `ingestion-service` strictly validates economic completeness (`strike`, `notionalAmount`, `optionType`, `premiumAmount`) and marks incomplete trades as `SCHEMA_INVALID`, routing them directly to `exception.created` without forwarding to `trade.accepted`.
- **System Impact:**
  In a normal pipeline, `EconomicFieldsRule` in `validation-service` is largely dead code because missing economic fields are intercepted at the edge. More importantly, this blurs the boundary between schema format validation (FR2) and STP business evaluation (FR5), preventing trades with economic discrepancies from undergoing reference data enrichment.
- **Recommended Remediation:**
  Clarify architecture: `TradeValidator` (FR2) should validate structural format, JSON types, and mandatory envelope headers, allowing well-formed trades with missing business economics to flow to `enrichment-service` and then fail STP cleanly in `validation-service` (FR5).
- **Resolution:**
  `TradeValidator` now rejects only *invalid* economic values (negative strike, bad currency code, unknown option type). *Absent* values pass, so the trade continues through enrichment and `EconomicFieldsRule` raises `MISSING_ECONOMIC_DATA` at STP. This keeps value checks in FR2, as the spec requires, and presence checks in FR5. `TradeEnricher` needed a guard: the reference maps reject null keys, so an absent `underlier` crashed enrichment. On the 500-trade seed, rejections at ingestion fell from 85 to 34 and confirmations were unchanged at 201.

---

### 11. 🟠 HIGH / MODERATE RISK: Silent Dropping of `CANCEL_TRADE` Events in `validation-service` — ✅ [RESOLVED in `fix/code-consistency`]
- **Location:** [TradeEnrichedConsumer.java:L142-L146](../../services/validation-service/src/main/java/com/tcem/validation/consumer/TradeEnrichedConsumer.java#L142-L146).
- **Issue:**
  When `EventType.CANCEL_TRADE` is received on `trade.enriched`, `TradeEnrichedConsumer` simply logs and returns early.
- **System Impact:**
  While suppressing confirmation generation is correct, dropping the event silently means no cancellation status is emitted to `stp.validated` or the audit log, leaving open exceptions and active work items for the trade dangling indefinitely.
- **Recommended Remediation:**
  Publish a cancellation outcome to `stp.validated` (or a dedicated lifecycle event) so `workitem-service` can automatically close open work items and update the trade's audit state to `CANCELLED`.
- **Resolution:**
  `TradeEnrichedConsumer` publishes `StpValidatedEvent` with `cancelled=true`, after the duplicate check but before the confirmed-version guard, which would otherwise swallow a cancel of a confirmed trade. `TradeCancellationService` in `workitem-service` marks the trade's open work items `CANCELLED` and writes a `TRADE_CANCELLED` audit entry (new `ProcessingStatus.CANCELLED`). *Not covered:* exception records stay `OPEN`, an existing confirmation is not changed, and an exception arriving just after the cancel can still open a work item.

---

### 12. 🟡 LOW RISK / INCONSISTENCY: Route Collision and Payload Discrepancy on `GET /metrics/stp-rate`
- **Location:** [TradeQueryController.java:L140](../../services/ingestion-service/src/main/java/com/tcem/ingestion/controller/TradeQueryController.java#L140) vs [ConfirmationQueryController.java:L113](../../services/validation-service/src/main/java/com/tcem/validation/controller/ConfirmationQueryController.java#L113).
- **Issue:**
  Both `ingestion-service:8081` and `validation-service:8083` define `GET /metrics/stp-rate`, but return completely different JSON payloads:
  - `ingestion-service`: `{"stpRate": 0.85, "acceptedTrades": 420, "totalTrades": 500}`
  - `validation-service`: `{"totalConfirmations": 25}`
- **System Impact:**
  Ambiguity at the API Gateway level (`nginx.conf`) and potential UI dashboard rendering failures if the frontend queries one endpoint expecting the other's schema.
- **Recommended Remediation:**
  Rename `validation-service`'s endpoint to `/confirmations/metrics/summary` or integrate confirmation metrics into `ingestion-service`'s consolidated metrics calculator.

---

### 13. 🟢 NOT BREAKING / COSMETIC: Documentation Inaccuracy in `ingestion-service/README.md`
- **Location:** [ingestion-service/README.md:L188](../../services/ingestion-service/README.md#L188).
- **Issue:**
  Lists `TERMINATE_TRADE` as an allowed value for `eventType`. However, `EventType.java` in `common-lib` does not contain `TERMINATE_TRADE`.
- **System Impact:**
  None on runtime unless an external client relies on the doc and sends `TERMINATE_TRADE`, causing Jackson deserialization failure.
- **Recommended Remediation:**
  Remove `TERMINATE_TRADE` from `README.md` to match `EventType.java`.

---

### Phase 3 Findings: Enrichment, Exception Processing & Work Items

### 14. 🔴 BREAKS THE SYSTEM: Manual Review "APPROVE" Action is an Architectural Dead End (Trade Stranded) — ✅ [RESOLVED in `fix/code-consistency`: Commit `8b4435d`]
- **Location:** [ManualReviewService.java:L22-L25](../../services/workitem-service/src/main/java/com/tcem/workitem/service/ManualReviewService.java#L22-L25), [WorkItemService.java:L206-L213](../../services/workitem-service/src/main/java/com/tcem/workitem/service/WorkItemService.java#L206-L213), and [WorkItemLinkedProducer.java:L20-L30](../../services/workitem-service/src/main/java/com/tcem/workitem/producer/WorkItemLinkedProducer.java#L20-L30).
- **Issue:**
  When an operations specialist reviews an exception on the manual review queue and submits an `APPROVE` decision, `ManualReviewService.decide()` closes the work item as `DONE` and publishes a `WorkItemEvent` to `workitem.linked`. The service documentation states: *"Generating the confirmation stays with validation-service, which can react to the closed item on workitem.linked"*. However, `validation-service` **only listens to `trade.enriched`**! Across all microservices, **no component listens to `workitem.linked`**.
- **System Impact:**
  Manually approved trades are completely abandoned: no confirmation is ever triggered, no message is forwarded to `stp.validated` or `confirmation.generated`, and the trade remains permanently unconfirmed despite human authorization.
- **Recommended Remediation:**
  Implement a consumer for `workitem.linked` in `validation-service` (or dispatch an approved trade re-submission event) that loads the trade and emits a confirmation upon manual approval.
- **Resolution:**
  `validation-service` now stores the raw enriched message when STP fails (`HeldTradeRepository`, `docType=HELD_TRADE` in the `confirmations` database, so no new database is needed) and `ManualReviewApprovalConsumer` listens to `workitem.linked`. A closed `MANUAL_REVIEW` item with `reviewDecision=APPROVED` generates the confirmation; `confirmation.generated` then closes the trade's other open work items. `WorkItem` gained `reviewDecision`, because approved and rejected items were otherwise both just `DONE`. A repeated approval is suppressed by the fingerprint check. An approval that cannot be confirmed (for example a schema-invalid trade, which was never enriched) goes to `deadletter.trade` as non-replayable.

---

### 15. 🔴 BREAKS THE SYSTEM: End-to-End Replay Acknowledgment Failure Across Services (DLQ Stuck in `REPLAYING`) — ✅ [RESOLVED in `fix/code-consistency`: Commit `8b4435d`]
- **Location:** [DefaultExceptionProcessingService.java:L314-L323](../../services/exception-service/src/main/java/com/tcem/exception/service/DefaultExceptionProcessingService.java#L314-L323), [ExceptionClassifiedProducer.java:L43-L60](../../services/exception-service/src/main/java/com/tcem/exception/producer/ExceptionClassifiedProducer.java#L43-L60), and [ReplayOutcomeService.java:L75-L81](../../services/workitem-service/src/main/java/com/tcem/workitem/service/ReplayOutcomeService.java#L75-L81).
- **Issue:**
  `ReplayOutcomeService` in `workitem-service` monitors downstream pipeline topics to detect replay success. It considers a replay successful only when either `ConsumerRecords.hasReplayHeader(record)` (Kafka header `tcem.replayCount > 0`) or `hasReplayMarker(value)` (`eventType == REPLAY || replayCount > 0`) is true.
  In `exception-service`, `buildOutboundHeader()` unconditionally sets `header.setEventType(EventType.NEW_TRADE)` and leaves `replayCount` at `0`. In addition, `ExceptionClassifiedProducer` does not forward Kafka headers.
- **System Impact:**
  When an `exception.created` message is replayed, its output on `exception.classified` loses all replay metadata. `ReplayOutcomeService.onPipelineMessage()` ignores the message. The DLQ entry remains permanently in `REPLAYING` status, its linked work item is never auto-closed, and subsequent replays are blocked with `ALREADY_REPLAYING`.
- **Recommended Remediation:**
  Preserve inbound `eventType` and `replayCount` when constructing outbound headers in `DefaultExceptionProcessingService`, and propagate `tcem.replayCount` in `ExceptionClassifiedProducer`.
- **Resolution:**
  `DefaultExceptionProcessingService` now keeps the inbound `eventType` and `replayCount` on its outbound header. `workitem-service` also counts `manual.review.required` as proof that a replayed `exception.created` message was processed; those replays previously stayed in `REPLAYING`.

---

### 16. 🔴 BREAKS THE SYSTEM: Multi-Attempt Replay Suppression in `TradeAcceptedConsumer` (Attempt > 1 Dropped) — ✅ [RESOLVED in `fix/code-consistency`: Commit `8b4435d`]
- **Location:** [TradeAcceptedConsumer.java:L55-L61](../../services/enrichment-service/src/main/java/com/tcem/enrichment/consumer/TradeAcceptedConsumer.java#L55-L61), [ReplayService.java:L139-L155](../../services/workitem-service/src/main/java/com/tcem/workitem/service/ReplayService.java#L139-L155), and [IdempotencyKey.java:L40-L51](../../services/common-lib/src/main/java/com/tcem/common/idempotency/IdempotencyKey.java#L40-L51).
- **Issue:**
  When `ReplayService` republishes a dead-lettered trade to `trade.accepted`, it rewrites `eventType` to `REPLAY`. On replay attempt 1, `TradeAcceptedConsumer` processes the message and stores the key `idempotency:{tradeId}:{messageId}:{version}:REPLAY`. If downstream fails and the operator initiates a 2nd replay attempt (`replayCount=2`), `TradeAcceptedConsumer` calculates the exact same `IdempotencyKey` because `IdempotencyKey` does **not** include `replayCount`. `idempotencyRepository.isProcessed(key)` returns `true`.
- **System Impact:**
  Any subsequent replay attempt (> 1) on `trade.accepted` is silently dropped as a duplicate! The replayed trade never reaches enrichment, and the operator is unable to retry failed replays.
- **Recommended Remediation:**
  Bypass the idempotency check in `TradeAcceptedConsumer` when Kafka header `tcem.replayCount > 0` or payload `eventType == REPLAY` (matching the pattern in `DefaultExceptionProcessingService:L171`).
- **Resolution:**
  `TradeAcceptedConsumer` skips the duplicate check for `REPLAY` messages and does not re-record the key, because its repository PUTs without a `_rev` and would get a 409. `DefaultTradeIngestionService` had the same defect and now bypasses the check too. *Reachability note:* a successful first replay closes its DLQ entry, so in normal use this needs two DLQ entries for the same message; the live test reproduced it that way.

---

### 17. 🟠 HIGH / MODERATE RISK: Discarding Refined Classification Results in `DefaultExceptionProcessingService` — ✅ [RESOLVED in `fix/code-consistency`]
- **Location:** [DefaultExceptionProcessingService.java:L203-L210](../../services/exception-service/src/main/java/com/tcem/exception/service/DefaultExceptionProcessingService.java#L203-L210).
- **Issue:**
  `classificationRulesEngine.classify(record)` evaluates the exception record and returns a `ClassificationResult` containing `confirmedCategory` and `confirmedSubTheme`. However, `DefaultExceptionProcessingService` only calls `record.setClassificationConfidence(classificationResult.getClassificationConfidence())`. It forgets to call `record.setCategory(...)` and `record.setSubTheme(...)`.
- **System Impact:**
  If a rule resolves an `UNKNOWN` or unmapped sub-theme to a specific failure reason, that refined classification is discarded. The unrefined record is persisted to CouchDB and emitted to `exception.classified`, degrading reporting accuracy and downstream routing.
- **Recommended Remediation:**
  Update `record.setCategory(classificationResult.getConfirmedCategory())` and `if (classificationResult.getConfirmedSubTheme() != null) record.setSubTheme(classificationResult.getConfirmedSubTheme())`.
- **Resolution:**
  `DefaultExceptionProcessingService` copies the confirmed category and sub-theme onto the record. The rules mostly echo the inbound values, so the practical effect is small: mainly a null sub-theme becoming `UNKNOWN`.

---

### 18. 🟠 HIGH / MODERATE RISK: Synchronous Blocking Kafka Publishing in Event-Driven Consumer Threads — ✅ [RESOLVED in `fix/code-consistency`]
- **Location:** [TradeEnrichedProducer.java:L34-L39](../../services/enrichment-service/src/main/java/com/tcem/enrichment/producer/TradeEnrichedProducer.java#L34-L39) and [DeadLetterProducer.java:L34](../../services/enrichment-service/src/main/java/com/tcem/enrichment/producer/DeadLetterProducer.java#L34).
- **Issue:**
  In `enrichment-service`, both `TradeEnrichedProducer.publish()` and `DeadLetterProducer.publish()` call `.join()` on Kafka `CompletableFuture<SendResult>`.
- **System Impact:**
  Under high throughput or during transient Kafka latency spikes, consumer worker threads block synchronously. This can exceed Kafka consumer max poll intervals, trigger frequent consumer rebalances, and stall the enrichment pipeline.
- **Recommended Remediation:**
  Handle Kafka publish results asynchronously using `.whenComplete()` or configure explicit bounded timeouts (`.get(3, TimeUnit.SECONDS)`).
- **Resolution:**
  `.join()` is replaced by a bounded wait (`KafkaSends`, `enrichment.kafka.send-timeout-ms`, default 10 seconds), and `max.block.ms` is capped at 10 seconds. The audit's `.whenComplete()` suggestion was not used: it would record the idempotency key before knowing the publish succeeded, risking silent loss. A send that times out may still be delivered later, which downstream duplicate checks should absorb; that case was not tested.

---

### 19. 🟡 LOW RISK / INCONSISTENCY: Missing Database in `scripts/init-couchdb.sh` (`enrichment-idempotency`) — ✅ [RESOLVED in `fix/code-consistency`: Commit `d30e283`]
- **Location:** [scripts/init-couchdb.sh:L19-L27](../../scripts/init-couchdb.sh#L19-L27) and [application.yml:L26-L27](../../services/enrichment-service/src/main/resources/application.yml#L26-L27).
- **Issue:**
  `scripts/init-couchdb.sh` provisions `trades`, `confirmations`, `exceptions`, `work-items`, `audit-events`, `stp-metrics`, `dlq-entries`, but omits `enrichment-idempotency`.
- **System Impact:**
  In deployments where CouchDB database auto-creation is disabled or restricted by security policy, `enrichment-service` fails on startup when attempting to verify its idempotency collection.
- **Recommended Remediation:**
  Add `"enrichment-idempotency"` to `APP_DBS` in `scripts/init-couchdb.sh`.

---

### 20. 🟡 LOW RISK / INCONSISTENCY: Inconsistent Hardcoded Pagination Limit (1000) for Aggregate Exception Counters
- **Location:** [ExceptionCouchDbRepository.java:L118-L128](../../services/exception-service/src/main/java/com/tcem/exception/repository/ExceptionCouchDbRepository.java#L118-L128).
- **Issue:**
  `countAll()` and `countByStatus()` execute Mango queries fetching up to 1,000 complete documents into memory and calculate `.size()`.
- **System Impact:**
  Exception counts are artificially capped at 1,000 in large or historical datasets. Fetching 1,000 documents across HTTP for an aggregate dashboard counter creates unnecessary network and memory overhead.
- **Recommended Remediation:**
  Use CouchDB reduce views (`_count`) or lightweight database metadata (`doc_count`) for metrics.

---

### 21. 🟡 LOW RISK / INCONSISTENCY: Build Failure on Modern JDKs without ByteBuddy Experimental Flag
- **Location:** [services/pom.xml](../../services/pom.xml).
- **Issue:**
  Running `mvn test` on Java 25/26 throws `java.lang.IllegalArgumentException: Java 26 (70) is not supported by the current version of Byte Buddy` during Mockito inline mock initialization in `enrichment-service`.
- **System Impact:**
  Build failures when running tests in local modern developer environments.
- **Recommended Remediation:**
  Add `<argLine>-Dnet.bytebuddy.experimental=true</argLine>` to `maven-surefire-plugin` in `services/pom.xml`.

---

### 22. 🟢 NOT BREAKING / COSMETIC: Stale Documentation Regarding Idempotency Cleanup — ✅ [RESOLVED in `fix/code-consistency`: Commit `8b4435d`]
- **Location:** [IdempotencyKey.java:L28-L34](../../services/common-lib/src/main/java/com/tcem/common/idempotency/IdempotencyKey.java#L28-L34).
- **Issue:**
  Javadoc claims that services must manually clear their CouchDB idempotency store before DLQ replay is initiated. In practice, `ReplayService` relies on mutating `eventType=REPLAY` in the envelope.
- **System Impact:**
  None on runtime; causes developer confusion regarding replay mechanics.
- **Recommended Remediation:**
  Align Javadoc comments with the actual event-type header rewriting architecture.
- **Resolution:**
  The Javadoc now says every replay attempt shares one key, so services skip the duplicate check for `REPLAY` messages.

---

### Phase 4 Findings: Frontend UI Alignment, State Management & API Integration

### 23. 🔴 BREAKS THE SYSTEM: Frontend Operates in 100% In-Memory Mock Isolation Without Backend API Wiring — ✅ [RESOLVED in `feat/frontend-live-data`]
- **Location:** [AppContext.tsx:L1-L786](../../frontend/src/context/AppContext.tsx#L1-L786), [vite.config.ts:L14-L22](../../frontend/vite.config.ts#L14-L22), and [nginx.conf:L11-L82](../../frontend/nginx.conf#L11-L82).
- **Issue:**
  Both `vite.config.ts` and `nginx.conf` define detailed proxy rules for `/api/trades`, `/api/metrics`, `/api/enrichment`, `/api/validation`, `/api/exceptions`, `/api/workitems`, and `/api/dlq`. However, the React frontend contains **zero network calls** (`fetch`, `axios`, `WebSocket`, or `EventSource`). All data collections (`trades`, `exceptions`, `workItems`, `confirmations`, `dlqEntries`, `auditTrail`) are initialized from `mockData.ts`. User mutations (DLQ replay, manual review sign-off, work item status update, exception resolution) mutate only local component state in `AppContext.tsx`.
- **System Impact:**
  The frontend is completely disconnected from the live Spring Boot microservices, Kafka topics, and CouchDB databases. In a running environment, trades ingested by backend services never appear in the dashboard, and operational actions taken in the UI never dispatch to the backend Kafka pipeline.
- **Recommended Remediation:**
  Create a backend API service layer (`frontend/src/services/apiClient.ts`) that calls the proxied `/api/...` endpoints on mount and dispatches actual REST mutations (`POST /api/dlq/{id}/replay`, `POST /api/workitems/{id}/review`), retaining mock data as an offline fallback.
- **Resolution:**
  The UI now runs on real backend data by default. `LiveAppProvider` polls the APIs every 3 seconds and sends every action to the backend; the simulation moved to `MockAppProvider` behind a visible LIVE / DEMO DATA switch, and the two never mix. This needed backend work the audit did not list: a trade lifecycle read model in `ingestion-service` (trade status previously stayed at `ACCEPTED`), `GET /audit` and `GET /confirmations`, routing non-eligible counterparties to manual review, and a generator and seed in which valid trades really are eligible. Values the UI invented are computed from real data in live mode or removed. *Limits:* the UI loads whole lists by polling, which suits demo volumes but not the 1M/day design target. Authentication was added afterwards: HTTP Basic with demo accounts on the write APIs of `ingestion-service` and `workitem-service`, with reads left open.

---

### 24. 🔴 BREAKS THE SYSTEM: DTO Contract Mismatch in `DeadLetterEvent` / `DlqEntryDocument` — ✅ [RESOLVED in `fix/code-consistency`: Commit `370fa98`]
- **Location:** [types/tcem.ts:L279-L294](../../frontend/src/types/tcem.ts#L279-L294) vs [DlqEntryDocument.java](../../services/workitem-service/src/main/java/com/tcem/workitem/model/document/DlqEntryDocument.java) & [DlqController.java:L43](../../services/workitem-service/src/main/java/com/tcem/workitem/controller/DlqController.java#L43).
- **Issue:**
  When `DlqController.java` serves `GET /dlq`, it serializes `DlqEntryDocument` objects whose primary key is `id` and status field is `dlqStatus`. In contrast, the frontend `DeadLetterEvent` type expects `dlqId` and `status`.
- **System Impact:**
  Connecting the frontend to the backend `/api/dlq` endpoint results in `entry.dlqId` and `entry.status` being `undefined`. Status badges fail to render, and `handleReplayClick(entry.dlqId)` passes `undefined` to the replay endpoint, causing runtime errors.
- **Recommended Remediation:**
  Update `DeadLetterEvent` in [types/tcem.ts](../../frontend/src/types/tcem.ts) to accept `id?: string; dlqId?: string; dlqStatus?: string; status?: string;` or map attributes upon ingestion.

---

### 25. 🟠 HIGH / MODERATE RISK: Counterparty Legal Entity Name Mapping Loss — ✅ [RESOLVED in `fix/code-consistency`: Commit `d00ccb6`]
- **Location:** [types/tcem.ts:L129-L133](../../frontend/src/types/tcem.ts#L129-L133) and [TradeDetailModal.tsx:L146](../../frontend/src/components/trades/TradeDetailModal.tsx#L146) vs [Party.java:L20-L30](../../services/common-lib/src/main/java/com/tcem/common/model/trade/Party.java#L20-L30).
- **Issue:**
  In `common-lib`, `Party.java` stores the enriched institution name in `legalEntityName` (populated by `TradeEnricher.java:L75`). The frontend `Party` interface defines `partyName?: string` and line 146 renders `{p.partyName || p.partyId}`.
- **System Impact:**
  When real trades from `ingestion-service` or `enrichment-service` are viewed in `TradeDetailModal`, `p.partyName` is undefined, causing the UI to fall back to raw party IDs (e.g. `PTY-CP-101`) instead of displaying the enriched counterparty name.
- **Recommended Remediation:**
  Update `Party` interface in `tcem.ts` and `TradeDetailModal.tsx` to read `p.legalEntityName || p.partyName || p.partyId`.

---

### 26. 🟠 HIGH / MODERATE RISK: Manual Review Identifier Mismatch (`tradeId` vs `workItemId`) — ✅ [RESOLVED in `fix/code-consistency`]
- **Location:** [ManualReviewQueue.tsx:L29-L32](../../frontend/src/components/manualReview/ManualReviewQueue.tsx#L29-L32) and [AppContext.tsx:L247-L249](../../frontend/src/context/AppContext.tsx#L247-L249) vs [WorkItemController.java:L99-L100](../../services/workitem-service/src/main/java/com/tcem/workitem/controller/WorkItemController.java#L99-L100).
- **Issue:**
  The backend manual review endpoint is `POST /workitems/{id}/review`, requiring the work item identifier (`WI-xxxxxxxx`). In the frontend, `ManualReviewQueue` binds directly to `Trade` objects and invokes `approveManualReview(currentTrade.tradeId, ...)` passing `tradeId`.
- **System Impact:**
  Calling the backend endpoint with `tradeId` results in `404 NOT_FOUND` because the work item repository looks up by `workItemId`, preventing operators from approving or rejecting manual review tickets through the API.
- **Recommended Remediation:**
  Ensure the manual review screen queries `GET /api/workitems?type=MANUAL_REVIEW` and passes `item.workItemId` to the review action.
- **Resolution:**
  The review queue is now built from open `MANUAL_REVIEW` work items, and `approveManualReview` / `rejectManualReview` take a `workItemId`. This also fixed visible bugs in the simulation: decisions left the work item `OPEN`, and `TRD-10005` and injected dispute trades had no work item at all. The 404 itself could not occur, because the frontend does not call the backend; live wiring is C4-01.

---

### 27. 🟡 LOW RISK / INCONSISTENCY: Proxy Routing Collisions for Metrics Endpoints
- **Location:** [vite.config.ts:L16](../../frontend/vite.config.ts#L16) and [nginx.conf:L23-L31](../../frontend/nginx.conf#L23-L31).
- **Issue:**
  Both proxies route `/api/metrics` to `ingestion-service:8081`. However, `exception-service` exposes `/metrics/exception-rate` on port 8084, and `validation-service` exposes `/metrics/stp-rate` on port 8083. If the frontend requests `/api/metrics/exception-rate`, the request is misrouted to `ingestion-service:8081`, returning HTTP 404.
- **System Impact:**
  Dashboard metric widgets cannot fetch exception rates or validation metrics through the generic `/api/metrics` path.
- **Recommended Remediation:**
  Scope proxy routes explicitly by service: `/api/exceptions/metrics/...` &rarr; port 8084; `/api/validation/metrics/...` &rarr; port 8083; `/api/trades/metrics/...` &rarr; port 8081.

---

### 28. 🟡 LOW RISK / INCONSISTENCY: Missing Owner Queues in Filter Dropdowns
- **Location:** [ExceptionQueue.tsx:L31-L38](../../frontend/src/components/exceptions/ExceptionQueue.tsx#L31-L38) and [WorkItemList.tsx:L81-L88](../../frontend/src/components/workitems/WorkItemList.tsx#L81-L88).
- **Issue:**
  `types/tcem.ts` and backend `WorkItemRules.java` define 7 canonical owner queues (`DATA_REMEDIATION`, `OPERATIONS_REVIEW`, `TEMPLATE_MAINTENANCE`, `REFERENCE_DATA`, `TECHNOLOGY_SUPPORT`, `SPECIALIST_REVIEW`, `RULES_OWNER`). `ExceptionQueue.tsx` omits `SPECIALIST_REVIEW` and `RULES_OWNER`, while `WorkItemList.tsx` omits `RULES_OWNER`.
- **System Impact:**
  Operators cannot filter exceptions or work items assigned to the legal specialist or rules maintenance teams.
- **Recommended Remediation:**
  Synchronize dropdown options across all queue views to include all 7 canonical queues.

---

### 29. 🟢 NOT BREAKING / COSMETIC: Inconsistent CouchDB Database Naming in Topology View
- **Location:** [ServiceTopology.tsx:L34-L41](../../frontend/src/components/topology/ServiceTopology.tsx#L34-L41) vs [scripts/init-couchdb.sh:L19-L27](../../scripts/init-couchdb.sh#L19-L27).
- **Issue:**
  `ServiceTopology.tsx` displays database names using underscores (`workitems`, `audit_events`, `dlq_entries`), whereas the actual CouchDB databases provisioned in `init-couchdb.sh` use hyphens (`work-items`, `audit-events`, `dlq-entries`).
- **Recommended Remediation:**
  Update labels in `ServiceTopology.tsx` to match the exact hyphenated database names.

---

# Phase 5: DevOps, Docker Orchestration, Shell Scripts & End-to-End System Integration

---

### 30. 🔴 BREAKS THE SYSTEM: Missing `frontend/.dockerignore` Leaks Host OS Native Modules into Container Build — ✅ [RESOLVED in `fix/code-consistency`: Commit `ef23e52`]
- **Location:** [frontend/Dockerfile:L15-L19](../../frontend/Dockerfile#L15-L19), [docker-compose.yml:L337-L340](../../docker-compose.yml#L337-L340).
- **Issue:**
  The `docker-compose.yml` defines the frontend build context as `./frontend`. In Docker, `.dockerignore` must reside at the root of the build context (`./frontend/.dockerignore`). However, no `.dockerignore` exists inside `./frontend/` (only at the repo root and in `services/`).
  In `frontend/Dockerfile`:
  ```dockerfile
  COPY package*.json ./
  RUN npm ci
  COPY . .
  RUN npm run build
  ```
  When developers develop locally and execute `npm run build` or `npm install`, macOS ARM64 / Darwin native binaries and symlinks are written into `frontend/node_modules` and `frontend/dist`. When `docker compose up --build` or `./scripts/dev-up.sh` is executed, `COPY . .` copies the host machine's `node_modules` into the Alpine Linux container, clobbering the clean Linux dependencies installed by `RUN npm ci`.
- **System Impact:**
  `npm run build` inside Docker fails with binary architecture mismatch (`exec format error` or missing Linux musl bindings for `@rolldown/binding` / `oxlint`), completely breaking the container build on macOS developer workstations.
- **Recommended Remediation:**
  Create [frontend/.dockerignore](../../frontend/.dockerignore) containing:
  ```
  node_modules/
  dist/
  .env*
  .git/
  ```

---

### 31. 🔴 BREAKS THE SYSTEM: Asymmetric Data Persistence & Kafka Log Loss (No Volume Mount for Broker) — ✅ [RESOLVED in `fix/code-consistency`: `KAFKA_LOG_DIRS`, completing `e181b58`]
- **Location:** [docker-compose.yml:L32-L58](../../docker-compose.yml#L32-L58) vs [docker-compose.yml:L106-L125](../../docker-compose.yml#L106-L125).
- **Issue:**
  In `docker-compose.yml`, CouchDB has a persistent named volume attached:
  ```yaml
  volumes:
    - couchdb_data:/opt/couchdb/data
  ```
  In stark contrast, the Kafka broker (`tcem-kafka`) has **no volume mount configured whatsoever**. All Kafka cluster metadata, topic partition logs, and consumer group offset commits are written to the container's ephemeral root filesystem (`/tmp` / `/var/lib/kafka/data`).
- **System Impact:**
  Whenever containers are stopped or recreated (`docker compose down`, machine reboot, or image update), all Kafka logs and consumer group offsets are permanently wiped. However, CouchDB retains all previously stored trades, audit events, and confirmations. Upon the next startup, Kafka consumers restart at offset 0, attempting to re-read empty topics, while CouchDB records remain. Idempotency checks collide, DLQ replays reference nonexistent message offsets, and the system experiences split-brain state desynchronization between streaming and persistence tiers.
- **Recommended Remediation:**
  Add a persistent named volume for Kafka data in `docker-compose.yml`:
  ```yaml
  volumes:
    couchdb_data:
    kafka_data:

  services:
    kafka:
      volumes:
        - kafka_data:/var/lib/kafka/data
  ```
- **Resolution:**
  `e181b58` mounted the `kafka_data` volume, but the broker kept writing to its default `/tmp/kafka-logs`, so the volume stayed empty and recreating the container lost all 10 application topics. `docker-compose.yml` now also sets `KAFKA_LOG_DIRS: /var/lib/kafka/data`. Verified on an isolated stack: topics, messages and consumer offsets survive both a container recreate and `docker compose down` + `up`, nothing is reprocessed, and new trades are confirmed afterwards. `docker compose down -v` still gives a clean reset.

---

### 32. 🟠 HIGH / MODERATE RISK: Unbounded JVM Heap Allocation (75% Host RAM per Container) — ✅ [RESOLVED in `fix/code-consistency`: Commit `8b4435d`]
- **Location:** All backend Dockerfiles ([ingestion-service/Dockerfile:L42](../../services/ingestion-service/Dockerfile#L42), [enrichment-service/Dockerfile:L42](../../services/enrichment-service/Dockerfile#L42), [validation-service/Dockerfile:L42](../../services/validation-service/Dockerfile#L42), [exception-service/Dockerfile:L42](../../services/exception-service/Dockerfile#L42), [workitem-service/Dockerfile:L42](../../services/workitem-service/Dockerfile#L42)) vs [docker-compose.yml](../../docker-compose.yml).
- **Issue:**
  All five backend Dockerfiles specify container-aware memory flags:
  ```dockerfile
  ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
  ```
  However, `docker-compose.yml` defines **zero memory constraints or limits** (`mem_limit` or `deploy.resources.limits.memory`) on any service. In Docker, an unconstrained container sees 100% of the host machine's physical memory.
- **System Impact:**
  Each JVM dynamically provisions its max heap as 75% of the host's total RAM. On a 16 GB developer machine, each container calculates a ~12 GB max heap ceiling. Concurrently launching 5 backend services alongside Kafka and CouchDB causes extreme memory overcommit (~60 GB theoretical ceiling), resulting in severe OS thrashing, swap exhaustion, and abrupt container kills by the host OOM-killer during batch seed execution (`500` synthetic trades).
- **Recommended Remediation:**
  Define explicit memory limits in `docker-compose.yml` for each service (e.g., `mem_limit: 512m` or `mem_limit: 768m`), giving each JVM an explicit, predictable heap budget (~384 MB - 576 MB).
- **Resolution:**
  The five JVM services now have `mem_limit: 768m` (about 576 MB of heap at `MaxRAMPercentage=75`). A 500-trade seed ran with no service failures under this limit.

---

### 33. 🟠 HIGH / MODERATE RISK: End-to-End Pipeline CI Blindspot in GitHub Actions — ✅ [RESOLVED in `fix/code-consistency`]
- **Location:** [.github/workflows/smoke-test.yml:L31-L54](../../.github/workflows/smoke-test.yml#L31-L54).
- **Issue:**
  The CI workflow `smoke-test.yml` starts only a subset of services:
  ```yaml
  - name: Build and start ingestion service, workitem service and gateway
    run: docker compose up -d --build ingestion-service workitem-service frontend
  ```
  It completely omits `enrichment-service`, `validation-service`, and `exception-service`. Furthermore, neither `backend-ci.yml` nor `smoke-test.yml` ever tests the event pipeline end-to-end (`trade.raw` -> `trade.accepted` -> `trade.enriched` -> `stp.validated` -> `confirmation.generated` / `exception.created`).
- **System Impact:**
  Critical inter-service regressions (such as the broken `workitem.linked` topic, schema router mismatches, or header propagation drops) can be committed and merged into `main` without failing any PR checks, because CI never boots or asserts the core STP validation pipeline.
- **Recommended Remediation:**
  Add a full-stack integration step to `smoke-test.yml` that boots `docker compose up -d --build`, triggers `./scripts/seed-data.sh --generate 5`, and asserts that confirmations and exceptions appear in CouchDB.
- **Resolution:**
  `smoke-test.yml` now boots the full stack with `--wait`, runs `smoke-test.sh --all`, and runs the new `scripts/pipeline-test.sh` (12 checks: confirmation, missing economics, manual review approval, DLQ replay, cancellation). The script fails on code without the C2-03 and C2-04 fixes. The workflow's steps were run locally with shifted ports, but have not yet run on GitHub Actions. This needed a further fix: the frontend healthcheck called `localhost`, which resolves to IPv6 in that image while nginx listens on IPv4 only, so the container had always been reported unhealthy.

---

### 34. 🟡 LOW RISK / INCONSISTENCY: `scripts/dev-up.sh` Fails to Forward `--all` to Smoke Test — ✅ [RESOLVED in `fix/code-consistency`: Commit `8b4435d`]
- **Location:** [scripts/dev-up.sh:L36-L46](../../scripts/dev-up.sh#L36-L46) vs [scripts/smoke-test.sh:L44-L57](../../scripts/smoke-test.sh#L44-L57).
- **Issue:**
  `scripts/dev-up.sh` supports the `--all` argument:
  ```bash
  if [ "$MODE" = "--all" ]; then
    echo "=== [dev-up] Building and starting ALL services containerized ==="
    docker compose up -d --build
  ...
  # 5. Run smoke test
  echo "=== [dev-up] Running smoke test verification ==="
  ./scripts/smoke-test.sh
  ```
  Notice line 46 runs `./scripts/smoke-test.sh` with **no arguments**. In `smoke-test.sh`, checking backend services (:8082, :8083, :8084, :8085) is marked as optional `[SKIP]` unless `--all` is explicitly passed!
- **System Impact:**
  When a developer runs `./scripts/dev-up.sh --all` expecting strict verification of all five microservices, `smoke-test.sh` silently skips verifying enrichment, validation, and exception services, reporting "SMOKE TEST PASSED!" even if those services crashed on startup.
- **Recommended Remediation:**
  Pass `${MODE}` to `smoke-test.sh`:
  ```bash
  ./scripts/smoke-test.sh ${MODE}
  ```
- **Resolution:**
  `dev-up.sh` now passes `"$MODE"` to `smoke-test.sh`, so `--all` asserts all five services.

---

### 35. 🟡 LOW RISK / INCONSISTENCY: `scripts/init-kafka.sh` Dead Environment Variable & Container Coupling — ✅ [RESOLVED in `fix/code-consistency`: Commit `8b4435d`]
- **Location:** [scripts/init-kafka.sh:L8-L45](../../scripts/init-kafka.sh#L8-L45).
- **Issue:**
  Line 8 defines `BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"`, and line 22 echoes it. However, the variable `$BOOTSTRAP_SERVER` is **never referenced in any execution command**. Instead, the script hardcodes `docker compose exec -T kafka ... --bootstrap-server localhost:9092`.
- **System Impact:**
  Setting `KAFKA_BOOTSTRAP_SERVERS` has zero effect. Furthermore, running `./scripts/init-kafka.sh` against an external Kafka cluster or standalone container fails because it is tightly coupled to the local Docker Compose service name `kafka`.
- **Recommended Remediation:**
  Support both in-container execution and external bootstrap server targets via the `$BOOTSTRAP_SERVER` variable.
- **Resolution:**
  The script now uses its bootstrap variable, renamed `INIT_KAFKA_BOOTSTRAP_SERVER` (default `localhost:9092`). The commands run inside the Kafka container through `docker compose exec`, so this is the address seen from inside the container and it remains coupled to the Compose service. Reusing `KAFKA_BOOTSTRAP_SERVERS` would have broken it, because that is the host-side address.

---

### 36. 🟡 LOW RISK / INCONSISTENCY: Database Initializers Omit `enrichment-idempotency` — ✅ [RESOLVED in `fix/code-consistency`: Commit `d30e283`]
- **Location:** [docker-compose.yml:L163-L170](../../docker-compose.yml#L163-L170), [scripts/init-couchdb.sh:L19-L27](../../scripts/init-couchdb.sh#L19-L27).
- **Issue:**
  Both `couchdb-init` in `docker-compose.yml` and `scripts/init-couchdb.sh` provision 7 application databases:
  `trades`, `confirmations`, `exceptions`, `work-items`, `audit-events`, `stp-metrics`, `dlq-entries`.
  The `enrichment-idempotency` database used by `enrichment-service` is absent from both initializers.
- **System Impact:**
  While `enrichment-service` has `auto-create-databases: true` enabled as a fallback, running CouchDB in environments with restricted administrative permissions causes `enrichment-service` to fail with HTTP 401/403 when attempting to auto-create the missing database.
- **Recommended Remediation:**
  Add `"enrichment-idempotency"` to `APP_DBS` in [scripts/init-couchdb.sh](../../scripts/init-couchdb.sh) and `couchdb-init` in [docker-compose.yml](../../docker-compose.yml).

---

### 37. 🟢 NOT BREAKING / COSMETIC: Documentation & Base Image Tag Drift — ✅ [RESOLVED in `fix/code-consistency`: README updated in `9f2688d`; `kafka-ui` pinned in `8b4435d`]
- **Location:** [README.md:L13-L17](../../README.md#L13-L17), [docker-compose.yml:L93](../../docker-compose.yml#L93).
- **Issue:**
  1. Root `README.md` states: *"enrichment-service, validation-service and exception-service are still runnable stubs"*, and *"common-lib (empty for now)"*, even though all modules are fully implemented with test suites.
  2. `docker-compose.yml` uses an unpinned mutable image tag: `provectuslabs/kafka-ui:latest`.
- **System Impact:**
  Misleads new developers about repository maturity and risks unpredictable Docker pulls if `:latest` changes upstream.
- **Recommended Remediation:**
  Update `README.md` to reflect current implementation status, and pin `kafka-ui` to a specific version tag (e.g. `provectuslabs/kafka-ui:v0.7.2` or `kafbat/kafka-ui:v1.0.0`).
- **Resolution:**
  `kafka-ui` is now pinned to `provectuslabs/kafka-ui:v0.7.2`.

---

## Findings Added During Sprint 2 Remediation

These were found while fixing and live-testing the items above. They are not part of the original 37.

| Finding | Impact | Status |
| :--- | :--- | :--- |
| `validation-service` keyed dead letters by `correlationId` | `workitem-service` stored it as the trade id, so replay outcomes could never match the entry. | ✅ Fixed in `8b4435d`: keyed by `tradeId` |
| `ingestion-service` had no `REPLAY` bypass | A second replay emitted a duplicate exception still carrying the replay marker, which was misread as a successful replay. | ✅ Fixed in `8b4435d` |
| Replays routed to `manual.review.required` were not counted as success | The DLQ entry stayed in `REPLAYING`. | ✅ Fixed in `8b4435d` |
| The original C5-02 fix did not persist Kafka data | See finding 31. | ✅ Fixed with `KAFKA_LOG_DIRS` |
| DLQ API returns `_id`, not `id` | The `id` alias added for C4-02 would not match the live API. | ✅ Fixed: `adaptDlq` maps `_id` |
| Generator counterparties made outcomes depend on sequence | Half of `VALID_STANDARD_STP` trades used ineligible counterparties and failed STP, and `BUSINESS_RULE_NON_STP` often passed. | ✅ Fixed: valid trades use eligible counterparties, business-rule trades use `PTY-CP-103`, seed updated. Seed STP rate rose from about 44% to 79% |
| The 500-trade seed produced no manual review items | Nothing routed to `manual.review.required`. | ✅ Fixed: `COUNTERPARTY_NOT_STP_ELIGIBLE` routes to manual review; the seed now produces 17 |
| Frontend healthcheck always failed | `localhost` resolves to `::1` in the image while nginx listens on IPv4 only, so the container was reported unhealthy and `docker compose up --wait` failed. | ✅ Fixed: healthcheck uses `127.0.0.1` |
| Repeating the seed, or running `--generate` after it, raised exceptions but added no trades | Both reused the seed's message ids, so every message counted as a duplicate. A second seed took work items from about 100 to about 400. Found while testing C4-01. | ✅ Fixed: a repeat seed gets a 409 unless forced, and published batches get new ids |
| One stopped service froze the whole UI | A poll waits for all sources, and a request to a stopped service hung for 39 seconds. Found while testing C4-01. | ✅ Fixed: client request timeouts, and a connect timeout on every gateway route |
| STP chart labelled leftovers as dead letters | The segment was calculated as the remainder, so 34 schema-invalid trades showed as dead letters. Found while testing C4-01. | ✅ Fixed: each segment is counted directly |
| A replayed `CANCEL_TRADE` runs through STP | Replay overwrites `eventType` with `REPLAY`, so `TradeEnrichedConsumer` no longer recognises the cancel. | 🟡 Open |
| One approval confirms the whole trade | Approving a review closes the trade's other open work items, even a `DATA_FIX`, and can produce a confirmation with template `N/A`. | 🟡 Team to confirm this is the intended rule |

---

## Consolidated Multi-Phase Risk Matrix

| ID | Phase | Component | Issue Description | Severity / Status | Fix Complexity |
| :---: | :---: | :--- | :--- | :---: | :---: |
| **C1-01** | Phase 1 | `ConfirmationFingerprint` | Party field paths (`parties[COUNTERPARTY].partyId`) always return `null`, breaking amendment confirmation checks. | ✅ **RESOLVED (`a2cb6d6`)** | Low (Array-selector paths in `extractValue`) |
| **C1-02** | Phase 1 | `types/tcem.ts` vs `common-lib` | Enum divergence (`IRS` vs `IR_SWAP`, missing `NOVATION`/`COMPRESSION`, extra `HEARTBEAT`). | ✅ **RESOLVED (`c43263d`)** | Trivial (TypeScript alignment) |
| **C1-03** | Phase 1 | `validation-service` | Missing `IdempotencyKey` deduplication on STP failures causes duplicate `exception.created` events. | ✅ **RESOLVED (`537f353`, `bda73d5`)** | Medium (Added `validation-idempotency` store + key guard) |
| **C1-04** | Phase 1 | `validation-service` | Bypassing `SchemaVersionRouter` in `TradeEnrichedConsumer`. | ✅ **RESOLVED (`537f353`)** | Low (Routed via `SchemaVersionRouter`) |
| **C1-05** | Phase 1 | `common-lib` | Complete absence of unit tests in shared module. | ✅ **RESOLVED (`8b00f86`)** | Medium (Added 34-test JUnit 5 suite) |
| **C1-06** | Phase 1 | Configuration vs Listeners | Inconsistent Kafka topic property injection vs static constants across consumers. | ✅ **RESOLVED (`970ecf6`)** | Low (Standardized on `TopicNames` constants) |
| **C1-07** | Phase 1 | `docs/architecture.md` | Stale documentation claiming 3 backend services are stubs. | ✅ **RESOLVED (`fix/code-consistency`)** | Trivial (Update markdown) |
| **C2-01** | Phase 2 | `ConfirmationService` & `StpExceptionMapper` | Strips `eventType` and `replayCount` from outbound headers, permanently breaking DLQ replay completion in `workitem-service`. | ✅ **RESOLVED (`8b4435d`)** | Low (Copy header fields) |
| **C2-02** | Phase 2 | `ConfirmationCouchDbRepository` | `countAll()` Mango query defaults to limit 25, capping STP metrics permanently at 25. | ✅ **RESOLVED (`2c4b3aa`)** | Low (Added explicit limit 10000) |
| **C2-03** | Phase 2 | `TradeValidator` vs `EconomicFieldsRule` | Edge schema validation drops incomplete economic trades, making `EconomicFieldsRule` dead code. | ✅ **RESOLVED (`fix/code-consistency`)** | Medium (Align FR2 vs FR5 boundary) |
| **C2-04** | Phase 2 | `TradeEnrichedConsumer` | Silently drops `CANCEL_TRADE` without publishing to `stp.validated` or closing open exceptions. | ✅ **RESOLVED (`fix/code-consistency`)** | Low (Emit cancellation outcome) |
| **C2-05** | Phase 2 | API Gateway / Controllers | Conflicting `GET /metrics/stp-rate` endpoint paths with differing response structures across services. | 🟡 Low Risk (Sprint 2) | Low (Rename endpoint) |
| **C2-06** | Phase 2 | `ingestion-service/README.md` | Documents nonexistent `TERMINATE_TRADE` event type. | 🟢 Not Breaking (Sprint 2) | Trivial (Update markdown) |
| **C3-01** | Phase 3 | `ManualReviewService` | Operator "APPROVE" decision publishes to `workitem.linked` which no service consumes; approved trades are permanently stranded. | ✅ **RESOLVED (`8b4435d`)** | Medium (Add consumer or direct confirmation) |
| **C3-02** | Phase 3 | `exception-service` & Producers | `buildOutboundHeader` resets `eventType` to `NEW_TRADE`, dropping replay identity and preventing `ReplayOutcomeService` from detecting success. | ✅ **RESOLVED (`8b4435d`)** | Low (Preserve inbound header fields) |
| **C3-03** | Phase 3 | `TradeAcceptedConsumer` | Multi-attempt replays (> 1) share identical `IdempotencyKey`, causing 2nd replay attempts to be silently dropped as duplicates. | ✅ **RESOLVED (`8b4435d`)** | Low (Bypass check for REPLAY / replayCount > 0) |
| **C3-04** | Phase 3 | `DefaultExceptionProcessingService` | Discards `ClassificationResult.confirmedCategory` and `confirmedSubTheme`, keeping unrefined exception metadata. | ✅ **RESOLVED (`fix/code-consistency`)** | Low (Copy confirmed fields to record) |
| **C3-05** | Phase 3 | `enrichment-service` Producers | `.join()` blocks Kafka consumer threads synchronously on publish, risking consumer heartbeat timeouts and rebalances. | ✅ **RESOLVED (`fix/code-consistency`)** | Low (Use async callback or timeout) |
| **C3-06** | Phase 3 | `scripts/init-couchdb.sh` | Omits `enrichment-idempotency` database from CouchDB provisioning script. | ✅ **RESOLVED (`d30e283`)** | Trivial (Added db name to script) |
| **C3-07** | Phase 3 | `ExceptionCouchDbRepository` | `countAll()` caps exception counts at 1,000 and pulls full documents into heap memory. | 🟡 Low Risk (Sprint 2) | Low (Use CouchDB view / db metadata) |
| **C3-08** | Phase 3 | Build Configuration (`pom.xml`) | Missing `-Dnet.bytebuddy.experimental=true` surefire argLine breaks tests on modern JDKs (Java 25+). | 🟡 Low Risk (Sprint 2) | Trivial (Update parent POM plugin config) |
| **C3-09** | Phase 3 | `IdempotencyKey` Javadoc | Documents nonexistent workitem API for manual CouchDB idempotency record purging. | ✅ **RESOLVED (`8b4435d`)** | Trivial (Update Javadoc comments) |
| **C4-01** | Phase 4 | `AppContext.tsx` | Frontend operates in an in-browser operational simulation harness; live REST binding scheduled for hardening phase. | ✅ **RESOLVED (`feat/frontend-live-data`)** | Architecture Decoupling |
| **C4-02** | Phase 4 | `types/tcem.ts` (`DeadLetterEvent`) | Property naming divergence (`dlqId`/`status` vs backend `id`/`dlqStatus`) causes rendering/action failure against live API. | ✅ **RESOLVED (`370fa98`)** | Low (Dual-mapped attributes) |
| **C4-03** | Phase 4 | `TradeDetailModal.tsx` | Party name reads `p.partyName` instead of `p.legalEntityName`, hiding enriched counterparty names. | ✅ **RESOLVED (`d00ccb6`)** | Trivial (Reads `p.legalEntityName`) |
| **C4-04** | Phase 4 | `ManualReviewQueue.tsx` | Review action passes `tradeId` instead of `workItemId` to backend approval endpoint. | ✅ **RESOLVED (`fix/code-consistency`)** | Low (Look up and pass `workItemId`) |
| **C4-05** | Phase 4 | Proxy Routing (`vite`/`nginx`) | Generic `/api/metrics` proxy to port 8081 conflicts with port 8084 metrics endpoint. | 🟡 Low Risk (Sprint 2) | Low (Scope metric routes by service) |
| **C4-06** | Phase 4 | Queue Filters | Dropdowns omit `RULES_OWNER` and `SPECIALIST_REVIEW` queues from filterable options. | 🟡 Low Risk (Sprint 2) | Trivial (Add missing queue options) |
| **C4-07** | Phase 4 | `ServiceTopology.tsx` | Displays CouchDB database names with underscores rather than actual hyphenated names. | 🟢 Not Breaking (Sprint 2) | Trivial (Update string labels) |
| **C5-01** | Phase 5 | Docker Build (`frontend`) | Missing `frontend/.dockerignore` leaks host macOS binaries into Alpine Linux container build, breaking container builds. | ✅ **RESOLVED (`ef23e52`)** | Trivial (Added `frontend/.dockerignore`) |
| **C5-02** | Phase 5 | Docker Compose (`kafka`) | Asymmetric persistence: Kafka has no volume mount while CouchDB does, wiping offsets and causing streaming/persistence desync. | ✅ **RESOLVED (`KAFKA_LOG_DIRS`, completing `e181b58`)** | Low (Added `kafka_data` volume) |
| **C5-03** | Phase 5 | Orchestration & JVM Memory | Zero memory limits in `docker-compose.yml` paired with `-XX:MaxRAMPercentage=75.0` risks host OOM thrashing under load. | ✅ **RESOLVED (`8b4435d`)** | Low (Set `mem_limit: 512m`) |
| **C5-04** | Phase 5 | GitHub Actions CI | `smoke-test.yml` only boots 2 services, leaving `enrichment`, `validation`, and `exception` unverified in CI PR checks. | ✅ **RESOLVED (`fix/code-consistency`)** | Medium (Add full-stack test job) |
| **C5-05** | Phase 5 | `scripts/dev-up.sh` | Fails to pass `--all` to `smoke-test.sh`, skipping health checks for 4 backend services even when requested. | ✅ **RESOLVED (`8b4435d`)** | Trivial (Pass `${MODE}`) |
| **C5-06** | Phase 5 | `scripts/init-kafka.sh` | Dead variable `BOOTSTRAP_SERVER` and rigid coupling to `docker compose exec kafka`. | ✅ **RESOLVED (`8b4435d`)** | Low (Use variable parameter) |
| **C5-07** | Phase 5 | `scripts/init-couchdb.sh` & Init Container | Omits `enrichment-idempotency` database provision. | ✅ **RESOLVED (`d30e283`)** | Trivial (Added database name) |
| **C5-08** | Phase 5 | Documentation & Docker Images | Stale `README.md` claiming services are stubs, and mutable `:latest` tag on `provectuslabs/kafka-ui`. | ✅ **RESOLVED (README `9f2688d`, `kafka-ui` pin `8b4435d`)** | Trivial (Updated README) |

---

## Multi-Phase Roadmap Status

- [x] **Phase 1: Architecture, Shared Core Library (`common-lib`), Kafka Topics & Event Contracts** (Findings C1-01 to C1-07)
- [x] **Phase 2: Ingestion & Validation Pipeline (`ingestion-service` & `validation-service`)** (Findings C2-01 to C2-06)
- [x] **Phase 3: Enrichment, Exception Processing & Work Items (`enrichment-service`, `exception-service`, `workitem-service`)** (Findings C3-01 to C3-09)
- [x] **Phase 4: Frontend UI Alignment, State Management & API Integration (`frontend`)** (Findings C4-01 to C4-07)
- [x] **Phase 5: DevOps, Docker Orchestration, Shell Scripts & Consolidated System Risk Matrix** (Findings C5-01 to C5-08)
- [x] **Showstopper Remediation Sprint:** Fixed all 7 critical blockers (C5-01, C5-02, C2-02, C1-02, C4-02, C4-03, C3-06/C5-07) on `fix/code-consistency`.
- [ ] **Sprint 2 Remediation (in progress):** all high-severity findings and C4-01 resolved; 7 of 37 findings remain open, all low severity or cosmetic (see the risk matrix).

---

## Executive Summary & System Health Scorecard

### Overall Architectural Health Score: 86 / 100 (Upgraded from 68/100)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        TCEM PLATFORM HEALTH SCORE                      │
│                                                                        │
│  Domain Modeling & Design:   ██████████████████░░  92/100 (Strong)     │
│  DevOps & Containerization:  ██████████████████░░  92/100 (Hardened)   │
│  Backend Unit Test Coverage: ██████████████████░░  88/100 (544 pass)   │
│  Event Contracts & Enums:    █████████████████░░░  85/100 (Aligned)    │
│  Operations Cockpit UI:      ██████████████░░░░░░  72/100 (Live Ready) │
│                                                                        │
│  TOTAL PLATFORM RATING:      86 / 100 (Demo & Presentation Hardened)   │
└────────────────────────────────────────────────────────────────────────┘
```

### Key Architectural Strengths
1. **Clean Microservice Decoupling:** Clean module boundaries across ingestion, enrichment, validation, exception handling, and work item management.
2. **Robust Multi-Stage Docker Builds:** Standardized pinned base images (`eclipse-temurin:21-jre-alpine`), unprivileged `appuser:appgroup` execution, and BuildKit `.m2` cache mounts across all 5 backend services.
3. **Hardened Container & Streaming Persistence:** Persistent Kafka volume ensures consumer group offsets survive restarts without state divergence against CouchDB.
4. **High-Performance Operations UI:** Modern React 19 + TypeScript + Vite + Tailwind CSS stack with aligned data contracts, enriched party resolution, and zero build/lint errors.
5. **Deterministic Exception & Recommendation Engine:** Automated classification and real-time SLA tracking across 6 canonical exception categories.
