# Validation Service — Progress Tracker & Implementation Guide

> **Author / Owner:** Mahadev Balla (Backend Engineer)  
> **Service:** `validation-service` (Port 8083)  
> **Feature Branch:** `feat/validation-service` (cut from `main`)  
> **Key Specs:** FR5 (STP Validation), FR6 (Confirmation Generation & Duplicate Suppression), FR7 (Exception Routing)  
> **Last Updated:** November 2026 (all phases done + Phase 8 hardening)

---

## 1. Quick Context & Purpose

The **Validation Service** acts as the core decision-making and confirmation issuance authority of the TCEM platform:

1. **Consumes Enriched Trades (FR5):** Subscribes to the `trade.enriched` topic published upstream by `enrichment-service`.
2. **Evaluates STP Rules Engine (FR5):** Executes non-short-circuiting validation across mandatory confirmable economic terms, counterparty and product STP eligibility flags, confirmation template applicability, and date chronology (`tradeDate` <= `expiryDate` <= `settlementDate`).
3. **Publishes STP Decisions (FR5):** Emits canonical audit outcome events to `stp.validated` with validation metadata, pass/fail status (`STP_PASSED` vs `STP_FAILED`), and failure diagnostics.
4. **Executes Confirmation Generation & Duplicate Suppression (FR6):**
   - Renders deterministic, monospace plain-text confirmation notices based on resolved template identifiers.
   - Computes deterministic SHA-256 fingerprint over confirmable contract terms via `ConfirmationFingerprint`.
   - On `AMEND_TRADE`, queries CouchDB for the previous confirmation; if confirmable terms are unchanged, suppresses duplicate confirmation generation and avoids redundant counterparty transmissions ("nothing changed = don't resend").
   - Persists confirmation records into the CouchDB `confirmations` database and dispatches `ConfirmationEvent` to `confirmation.generated`.
5. **Routes STP Exceptions (FR7):** Maps validation failures to structured `ExceptionRecord` entries with category taxonomy (`MISSING_ECONOMIC_DATA`, `MISSING_STATIC_REFERENCE_DATA`, `BUSINESS_RULE_FAILURE`, `TEMPLATE_ISSUE`, `TECHNICAL_PROCESSING_FAILURE`), sub-themes, recommended actions, and queue routing (`DATA_REMEDIATION`, `OPERATIONS_REVIEW`, `TECHNICAL_SUPPORT`), dispatching `ExceptionEvent` to `exception.created`.
6. **Serves Operations REST APIs (Phase 6):** Exposes `GET /confirmations/{id}`, `GET /trades/{tradeId}/confirmations`, `GET /trades/{tradeId}/confirmation`, and `GET /metrics/stp-rate` for the React Operations UI via the Nginx API Gateway.

---

## 2. Status: What is Done vs. What is Remaining

### What is DONE:

- [x] **DevOps & Infrastructure Provisioning (PR #2 merged):**
  - Multi-stage Dockerfile for `validation-service` with `maven:3.9.9-alpine` + `eclipse-temurin:21-jre-alpine` non-root execution (`appuser`).
  - Auto-created Kafka topics `stp.validated`, `confirmation.generated`, and `exception.created` (3 partitions, replication 1) via `kafka-init`.
  - Auto-provisioned CouchDB database `confirmations` via `couchdb-init`.
  - Health checks established for CouchDB (`/_up`) and Spring Boot Actuator (`wget /actuator/health`).
  - Nginx gateway reverse proxy configured: `/api/validation/*` -> `http://validation-service:8083/*`.
- [x] **Frontend Contract & Confirmation View (PR #3 merged):**
  - Full TypeScript models for `ConfirmationRecord`, `ConfirmationStatus`, `ConfirmationView.tsx` expecting plain-text confirmation body, monospace layout, SHA-256 fingerprint badge, and download/copy controls.
- [x] **Shared Contracts & Utilities in `common-lib`:**
  - `TradeEvent`, `Trade`, `FxOptionEconomics`, `ReferenceDataEnrichment`, `NonEconomicTerms`, `Party`.
  - `ConfirmationRecord`, `ConfirmationEvent`, `ExceptionRecord`, `ExceptionEvent`, `DeadLetterEvent`.
  - `ConfirmableFieldRegistry` defining FX Option confirmable fields.
  - `ConfirmationFingerprint` SHA-256 utility for duplicate suppression.
  - `TopicNames`, `ProcessingStatus`, `ConfirmationStatus`, `ExceptionCategory`, `ExceptionSubTheme`.
- [x] **Enrichment Contract Dependency (PR #5 merged):**
  - Verified upstream implementation in `services/enrichment-service`.
  - `trade.enriched` outputs canonical `TradeEvent` with in-place `enrichmentMetadata` populated.
  - Resolved template ID format verified: `TMPL-FX-OPT-001` (version `1.2.0`).
  - Party enrichment verified: `legalEntityName`, `lei`, and `country` populated in-place on `trade.parties`.
- [x] **Phase 1: CouchDB Persistence Infrastructure (Completed):**
  - Configured `application.yml` with CouchDB credentials (`COUCHDB_USER`, `COUCHDB_PASSWORD`), Kafka consumer/producer configs, and listener auto-startup toggle.
  - `CouchDbProperties.java` & `ValidationProperties.java`.
  - `WebConfig.java` (CORS support for ports 3000 & 5173).
  - `CouchDbConfig.java` with connectivity checking & database initialization runner.
  - `CouchDbOperations.java` interface, `CouchDbClient.java` (RestClient with Basic Auth and conflict retry), `CouchDbResponse.java`, `FindQueryRequest.java`, `CouchDbException.java`.
  - `CouchDbHealthIndicator.java` for `/actuator/health`.
  - `ConfirmationDocument.java` (isolated CouchDB persistence document entity with explicit builder).
  - `ConfirmationCouchDbRepository.java` (save, findById, findByTradeId, findLatestForTrade, countAll).
- [x] **STP Rules Engine (FR5 Core):**
  - `StpRule.java` interface & `StpValidationResult.java` failure accumulator.
  - `EconomicFieldsRule.java`: Validates confirmable FX Option economics and dates; maps missing fields to specific `ExceptionSubTheme`.
  - `BusinessEligibilityRule.java`: Checks counterparty & product STP eligibility flags from `enrichmentMetadata`.
  - `TemplateResolutionRule.java`: Verifies confirmation template resolution (`resolvedTemplateId`, `resolvedTemplateVersion`).
  - `StpRulesEngine.java`: Rule aggregator and execution pipeline with safety fallback.
- [x] **STP Event Model:**
  - `StpValidatedEvent.java`: Event envelope for publishing to `stp.validated` topic with failure diagnostic details.
  - Domain models: [`Trade`](../services/common-lib/src/main/java/com/tcem/common/model/trade/Trade.java), [`MessageHeader`](../services/common-lib/src/main/java/com/tcem/common/model/message/MessageHeader.java), [`TradeEvent`](../services/common-lib/src/main/java/com/tcem/common/model/message/TradeEvent.java), [`FxOptionEconomics`](../services/common-lib/src/main/java/com/tcem/common/model/trade/FxOptionEconomics.java), [`Party`](../services/common-lib/src/main/java/com/tcem/common/model/trade/Party.java), [`NonEconomicTerms`](../services/common-lib/src/main/java/com/tcem/common/model/trade/NonEconomicTerms.java).
  - Enrichment provenance: [`ReferenceDataEnrichment`](../services/common-lib/src/main/java/com/tcem/common/model/reference/ReferenceDataEnrichment.java), [`EnrichmentSource`](../services/common-lib/src/main/java/com/tcem/common/model/reference/EnrichmentSource.java).
  - Confirmation & Exception events: [`ConfirmationRecord`](../services/common-lib/src/main/java/com/tcem/common/model/confirmation/ConfirmationRecord.java), [`ConfirmationEvent`](../services/common-lib/src/main/java/com/tcem/common/event/ConfirmationEvent.java), [`ExceptionRecord`](../services/common-lib/src/main/java/com/tcem/common/model/exception/ExceptionRecord.java), [`ExceptionEvent`](../services/common-lib/src/main/java/com/tcem/common/event/ExceptionEvent.java).
  - Registry & Fingerprinting: [`ConfirmableFieldRegistry`](../services/common-lib/src/main/java/com/tcem/common/registry/ConfirmableFieldRegistry.java), [`ConfirmationFingerprint`](../services/common-lib/src/main/java/com/tcem/common/registry/ConfirmationFingerprint.java).
  - Constants & Taxonomies: [`TopicNames`](../services/common-lib/src/main/java/com/tcem/common/constants/TopicNames.java), [`ProcessingStatus`](../services/common-lib/src/main/java/com/tcem/common/constants/ProcessingStatus.java), [`ConfirmationStatus`](../services/common-lib/src/main/java/com/tcem/common/constants/ConfirmationStatus.java), [`ExceptionCategory`](../services/common-lib/src/main/java/com/tcem/common/constants/ExceptionCategory.java), [`ExceptionSubTheme`](../services/common-lib/src/main/java/com/tcem/common/constants/ExceptionSubTheme.java).
- [x] **Enrichment Contract Resolution (PR #5 merged):**
  - Inspected upstream `enrichment-service` implementation.
  - Confirmed `trade.enriched` publishes `TradeEvent` with in-place `enrichmentMetadata` populated.
  - Confirmed resolved template format: `TMPL-FX-OPT-001` (version `1.2.0`).
  - Confirmed party reference fields (`legalEntityName`, `lei`, `country`) populated in-place on `trade.parties`.
- [x] **`common-lib` Accessor Completeness:**
  - Added explicit getters/setters to `ReferenceDataEnrichment.java` (needed by Phase 2 rules & future phases).
  - Added explicit getters/setters to `ConfirmationRecord.java` (needed by Phase 3/4).
  - Added explicit getters/setters to `ConfirmationEvent.java` (needed by Phase 3/4).
  - Pattern consistent with `Trade`, `Party`, `FxOptionEconomics`, `ExceptionRecord`, `MessageHeader`, `TradeEvent`.
- [x] **Phase 2 Unit Tests — 53 tests, all passing:**
  - [`StpValidationResultTest`](../services/validation-service/src/test/java/com/tcem/validation/rules/StpValidationResultTest.java) — 9 tests: pass/fail flag, multi-failure accumulation, `summarize()`, immutable list contract.
  - [`EconomicFieldsRuleTest`](../services/validation-service/src/test/java/com/tcem/validation/rules/EconomicFieldsRuleTest.java) — 16 tests: null trade, null economics block, each confirmable field missing/blank, all three date fields, multi-field non-short-circuit aggregation.
  - [`BusinessEligibilityRuleTest`](../services/validation-service/src/test/java/com/tcem/validation/rules/BusinessEligibilityRuleTest.java) — 11 tests: null trade guard, null/missing counterparty party, null enrichment metadata, `counterpartySTPEligible` false/null, `productSTPEligible` false/null, both-ineligible producing two failures, case-insensitive role match.
  - [`TemplateResolutionRuleTest`](../services/validation-service/src/test/java/com/tcem/validation/rules/TemplateResolutionRuleTest.java) — 10 tests: guard clauses, null/blank templateId → `NO_MATCHING_TEMPLATE` with hint in reason, templateId-present-version-missing → `TEMPLATE_VERSION_MISMATCH`, happy path, no double-failure when templateId is null.
  - [`StpRulesEngineTest`](../services/validation-service/src/test/java/com/tcem/validation/rules/StpRulesEngineTest.java) — 7 tests: full pass, all-three-rules-fire accumulation, single-rule partial failures, defensive fallback on rule exception (continues remaining rules), empty rule list.

- [x] **Phase 3: Decision Dispatch & Event Producers:**
  - [`StpValidatedProducer.java`](../services/validation-service/src/main/java/com/tcem/validation/producer/StpValidatedProducer.java): Publishes `StpValidatedEvent` to `TopicNames.STP_VALIDATED`; fallback key `"UNKNOWN_TRADE"`.
  - [`ConfirmationGeneratedProducer.java`](../services/validation-service/src/main/java/com/tcem/validation/producer/ConfirmationGeneratedProducer.java): Publishes `ConfirmationEvent` to `TopicNames.CONFIRMATION_GENERATED`; fallback key `"UNKNOWN_CONFIRMATION"`.
  - [`ExceptionCreatedProducer.java`](../services/validation-service/src/main/java/com/tcem/validation/producer/ExceptionCreatedProducer.java): Publishes `ExceptionEvent` to `TopicNames.EXCEPTION_CREATED`; fallback key `"UNKNOWN_EXCEPTION"`.
  - [`DeadLetterProducer.java`](../services/validation-service/src/main/java/com/tcem/validation/producer/DeadLetterProducer.java): Publishes `DeadLetterEvent` to `TopicNames.DEADLETTER_TRADE`; fallback key `"DLQ_<millis>"`.
- [x] **Phase 3 Unit Tests — 17 new tests (71 total), all passing:**
  - [`StpValidatedProducerTest`](../services/validation-service/src/test/java/com/tcem/validation/producer/StpValidatedProducerTest.java) — 4 tests: STP-passed dispatch, STP-failed dispatch with failure details, null-key fallback, Kafka send failure.
  - [`ConfirmationGeneratedProducerTest`](../services/validation-service/src/test/java/com/tcem/validation/producer/ConfirmationGeneratedProducerTest.java) — 4 tests: confirmation record dispatch, null-key fallback, Kafka send failure, fingerprint in payload.
  - [`ExceptionCreatedProducerTest`](../services/validation-service/src/test/java/com/tcem/validation/producer/ExceptionCreatedProducerTest.java) — 5 tests: missing economic data, business rule failure, template issue, null-key fallback, Kafka send failure.
  - [`DeadLetterProducerTest`](../services/validation-service/src/test/java/com/tcem/validation/producer/DeadLetterProducerTest.java) — 4 tests: replayable event dispatch, non-replayable event, null-key `DLQ_` fallback, Kafka send failure.
- [x] **Phase 4: Confirmation Generator & Fingerprint Engine (FR6) (Completed):**
  - [`PlainTextConfirmationGenerator.java`](../services/validation-service/src/main/java/com/tcem/validation/confirmation/PlainTextConfirmationGenerator.java): Deterministic, monospace plain-text confirmation generator with labeled trade identifiers, dates, parties, FX Option economics, non-economic terms, and template reference. Null-safe throughout with N/A fallbacks.
  - [`ConfirmationService.java`](../services/validation-service/src/main/java/com/tcem/validation/confirmation/ConfirmationService.java): Full confirmation lifecycle orchestrator — computes `ConfirmationFingerprint`, handles `AMEND_TRADE` duplicate suppression (comparing against `findLatestForTrade()`), saves `ConfirmationDocument` in CouchDB, constructs `ConfirmationRecord` and `ConfirmationEvent`, publishes to `confirmation.generated` via `ConfirmationGeneratedProducer`.
- [x] **Phase 5: Exception Routing Mapper (FR7) (Completed):**
  - [`StpExceptionMapper.java`](../services/validation-service/src/main/java/com/tcem/validation/exception/StpExceptionMapper.java): Maps `StpValidationResult.Failure` items to structured `ExceptionRecord` and `ExceptionEvent` instances; routes categories to appropriate operational queues (`DATA_REMEDIATION`, `OPERATIONS_REVIEW`, `TECHNICAL_SUPPORT`); populates recommended actions, resolution difficulty, operational impact, and replay flags.
- [x] **Phase 4 & 5 Unit Tests — 31 new tests (102 total), all passing:**
  - [`PlainTextConfirmationGeneratorTest`](../services/validation-service/src/test/java/com/tcem/validation/confirmation/PlainTextConfirmationGeneratorTest.java) — 12 tests: trade details, dates, party details, economics, non-economic terms, template reference, null safety, deterministic output, section headers.
  - [`ConfirmationServiceTest`](../services/validation-service/src/test/java/com/tcem/validation/confirmation/ConfirmationServiceTest.java) — 5 tests: new trade generation + persistence + publish, duplicate suppression returning empty `Optional`, amendment with changed fingerprint generating new confirmation, outbound header correlationId propagation, CouchDB document field mirroring.
  - [`StpExceptionMapperTest`](../services/validation-service/src/test/java/com/tcem/validation/exception/StpExceptionMapperTest.java) — 14 tests: queue routing per `ExceptionCategory`, traceability field propagation, failure mapping, multi-failure generation of distinct events, `replayMayResolve` per category, outbound header propagation, classification confidence, non-blank exception ID, non-null capture timestamp.

- [x] **Phase 6: Kafka Consumer Pipeline & Operations REST APIs (Completed):**
  - [`TradeEnrichedConsumer.java`](../services/validation-service/src/main/java/com/tcem/validation/consumer/TradeEnrichedConsumer.java): `@KafkaListener` on `trade.enriched` (group `validation-service-group`). Full processing pipeline: deserialise `TradeEvent`, guard `CANCEL_TRADE` (skip with log), idempotency check via `findLatestForTrade()` (skip if same `tradeId + tradeVersion` already confirmed — bypassed for `REPLAY` events), execute `StpRulesEngine`, always publish STP decision to `stp.validated`, then branch: STP pass → `ConfirmationService.processConfirmation()`; STP fail → `StpExceptionMapper` + `ExceptionCreatedProducer` per failure. Irrecoverable errors routed to DLQ via `DeadLetterProducer`. *(Parsing and idempotency were upgraded in Phase 8, see below.)*
  - [`ConfirmationQueryController.java`](../services/validation-service/src/main/java/com/tcem/validation/controller/ConfirmationQueryController.java): Operations REST API exposing four endpoints: `GET /confirmations/{id}` (single by ID), `GET /trades/{tradeId}/confirmations` (all versions, newest-first), `GET /trades/{tradeId}/confirmation` (latest), `GET /metrics/stp-rate` (total confirmation count).
- [x] **Phase 6 Unit Tests — 17 new tests (119 total), all passing:**
  - [`TradeEnrichedConsumerTest`](../services/validation-service/src/test/java/com/tcem/validation/consumer/TradeEnrichedConsumerTest.java) — 9 tests: NEW_TRADE STP-passed happy path, NEW_TRADE STP-failed exception dispatch, CANCEL_TRADE skip, idempotency guard skip, REPLAY bypasses idempotency, malformed JSON → DLQ, null trade → DLQ, multiple STP failures dispatched individually, AMEND_TRADE different version allowed through.
  - [`ConfirmationQueryControllerTest`](../services/validation-service/src/test/java/com/tcem/validation/controller/ConfirmationQueryControllerTest.java) — 8 tests: `GET /confirmations/{id}` found/not-found, `GET /trades/{tradeId}/confirmations` multiple/empty, `GET /trades/{tradeId}/confirmation` found/not-found, `GET /metrics/stp-rate` non-zero/zero.

- [x] **Phase 7: Automated Integration Testing & Verification (Completed):**
  - [`TradeEnrichedPipelineIntegrationTest`](../services/validation-service/src/test/java/com/tcem/validation/integration/TradeEnrichedPipelineIntegrationTest.java) — 5 tests. `@SpringBootTest` + `@EmbeddedKafka` with a real `@KafkaListener` and real producers; CouchDB is replaced by `@MockBean` on `ConfirmationCouchDbRepository` and `CouchDbClient`, so the consumer, rules engine, `ConfirmationService`, mapper and all Kafka producers run for real:
    - Valid `NEW_TRADE` → `STP_PASSED` on `stp.validated`, confirmation persisted, event on `confirmation.generated`.
    - `NEW_TRADE` with missing strike → `STP_FAILED` on `stp.validated`, event on `exception.created`, no confirmation.
    - Consumer-level idempotency: same `tradeId + tradeVersion` already confirmed → nothing published on any topic.
    - `AMEND_TRADE` with unchanged fingerprint → `STP_PASSED` published, confirmation suppressed.
    - `REPLAY` → bypasses version idempotency and re-runs STP, but fingerprint dedup still suppresses the confirmation.
  - [`IntegrationTestConsumer`](../services/validation-service/src/test/java/com/tcem/validation/integration/IntegrationTestConsumer.java): test helper reading output topics via manual partition assignment.
  - **Verified:** `mvn clean test -pl common-lib,validation-service` → `BUILD SUCCESS`; **124 tests, 0 failures, 0 errors, 0 skipped** (119 unit + 5 integration; includes `ValidationServiceApplicationTests` context-load test).
  - **Scope note:** CouchDB is mocked in the integration suite, so CouchDB persistence queries (Mango `_find`, conflict retry) are not exercised against a real CouchDB. They are covered by the Phase 1 unit tests and need a live-stack check during cross-service integration.

- [x] **Phase 8: Sprint 2 Hardening (Audit C1-01, C1-03, C1-04, C1-06) (Completed):**
  - [`ValidationIdempotencyRepository.java`](../services/validation-service/src/main/java/com/tcem/validation/idempotency/ValidationIdempotencyRepository.java): one small CouchDB document per processed `IdempotencyKey` in the `validation-idempotency` database (`isProcessed`, `record`). The database is configured via `couchdb.idempotency-database` (env `COUCHDB_VALIDATION_IDEMPOTENCY_DB`), created on startup by `CouchDbConfig`, and provisioned by `scripts/init-couchdb.sh` and `couchdb-init`.
  - [`TradeEnrichedConsumer.java`](../services/validation-service/src/main/java/com/tcem/validation/consumer/TradeEnrichedConsumer.java) now: parses through `SchemaVersionRouter` (unsupported `schemaVersion` -> DLQ with `SCHEMA_VERSION_UNSUPPORTED`, non-replayable); dead-letters events missing `tradeId`, `messageId`, `schemaVersion` or `eventType`; skips messages whose `IdempotencyKey` is already recorded (then applies the existing same-version confirmation guard); records the key only after all publishes succeed; `REPLAY` bypasses both guards.
  - Dead `validation.input-topic` property removed; the listener uses `TopicNames.TRADE_ENRICHED`.
  - `ConfirmationFingerprint` (`common-lib`) now resolves `parties[ROLE]` paths, so a counterparty or bank change produces a new fingerprint and a new confirmation (commit `a2cb6d6`).
  - **Tests:** `TradeEnrichedConsumerTest` grew from 9 to 17 tests. New cases: duplicate delivery of an STP-failed trade publishes exceptions once, already-recorded key is skipped, key recorded with the message's own identity, key not recorded on processing failure, `REPLAY` bypasses the key check, missing `messageId` -> DLQ, unsupported schema -> DLQ (non-replayable), schema v1.1 routed normally.
  - **Verified:** `mvn clean verify` -> `validation-service` 132 tests, 0 failures (482 across the reactor). Live on docker compose: `validation-idempotency` created and populated; a re-published processed message is logged as a duplicate and the document count is unchanged; a payload with `schemaVersion` `capstone-fpml-json-9.9` produced a DLQ entry with `SCHEMA_VERSION_UNSUPPORTED`.
  - Commits: `a2cb6d6` (fingerprint), `bda73d5` (database provisioning), `537f353` (idempotency + router), `970ecf6` (topic wiring).

- [x] **Phase 9: Replay and Manual Review Fixes (Audit C2-01, C3-01) (Completed):**
  - Outbound headers on `stp.validated`, `confirmation.generated` and `exception.created` keep the inbound `eventType` and `replayCount`.
  - Dead letters are keyed by `tradeId` instead of `correlationId`, because `workitem-service` stores the key as the trade id.
  - [`HeldTradeRepository.java`](../services/validation-service/src/main/java/com/tcem/validation/repository/HeldTradeRepository.java): keeps the latest raw enriched message of a trade that failed STP (`docType=HELD_TRADE` in `confirmations`; confirmation queries select `docType=CONFIRMATION`, so they are unaffected).
  - [`ManualReviewApprovalConsumer.java`](../services/validation-service/src/main/java/com/tcem/validation/consumer/ManualReviewApprovalConsumer.java): on `workitem.linked`, a closed `MANUAL_REVIEW` item with `reviewDecision=APPROVED` generates the confirmation. Other events are ignored. No held trade, or a confirmation failure, goes to the DLQ as non-replayable.
  - **Verified:** `mvn verify` -> `validation-service` 142 tests, 0 failures (496 across the reactor). Live on an isolated docker compose stack, before and after the fix. Commit: `8b4435d`.

### What is REMAINING (To Be Built)

- Nothing within validation-service scope. The cross-service replay and manual review flows have been verified against a live docker compose stack.

### Known Limitations (documented, not blocking)

- **Residual duplicate window (at-least-once):** since Phase 8 the `IdempotencyKey` is recorded in `validation-idempotency` after all publishes succeed, which covers redelivered messages for STP-failed trades. A crash between publishing and recording can still cause one duplicate on redelivery.
- **Fire-and-forget producers:** Kafka send failures are logged but do not propagate to the listener (see Phase 6), so events can be lost without a consumer retry. External monitoring is required.
- **Manual review approval confirms the whole trade**, even with other open exceptions, and can produce a confirmation with template `N/A`. Only the latest held message per trade is kept.
- **Replayed `CANCEL_TRADE`:** replay overwrites `eventType` with `REPLAY`, so the cancel check no longer recognises it and it runs through STP.
- **`GET /metrics/stp-rate`** returns a total confirmation count, not a pass/fail ratio.

---

## 3. Phased Implementation Details

- [x] **Phase 1: CouchDB Client & Persistence Layer** (Completed)
  - [`application.yml`](../services/validation-service/src/main/resources/application.yml): Configured CouchDB credentials (`COUCHDB_USER`, `COUCHDB_PASSWORD`), Kafka consumer/producer serializers, consumer group `validation-service-group`, auto-startup toggle, and port 8083.
  - [`CouchDbProperties`](../services/validation-service/src/main/java/com/tcem/validation/config/CouchDbProperties.java) & [`ValidationProperties`](../services/validation-service/src/main/java/com/tcem/validation/config/ValidationProperties.java): Spring Boot `@ConfigurationProperties` binding `couchdb.*` and `validation.*`.
  - [`CouchDbOperations`](../services/validation-service/src/main/java/com/tcem/validation/couchdb/CouchDbOperations.java) & [`CouchDbClient`](../services/validation-service/src/main/java/com/tcem/validation/couchdb/CouchDbClient.java): Spring Boot 3.3 `RestClient` implementation with Basic Auth, CouchDB conflict retry loop, rev extraction, document get/put, and Mango `_find` queries.
  - [`CouchDbConfig`](../services/validation-service/src/main/java/com/tcem/validation/config/CouchDbConfig.java): Spring configuration wiring client bean + health-aware `ApplicationRunner` database initializer for `confirmations` DB.
  - [`CouchDbException`](../services/validation-service/src/main/java/com/tcem/validation/couchdb/CouchDbException.java), [`CouchDbResponse`](../services/validation-service/src/main/java/com/tcem/validation/couchdb/CouchDbResponse.java), [`FindQueryRequest`](../services/validation-service/src/main/java/com/tcem/validation/couchdb/FindQueryRequest.java): DTOs and runtime exceptions for CouchDB operations.
  - [`CouchDbHealthIndicator`](../services/validation-service/src/main/java/com/tcem/validation/health/CouchDbHealthIndicator.java): Spring Boot Actuator indicator monitoring CouchDB liveness at `/actuator/health`.
  - [`ConfirmationDocument`](../services/validation-service/src/main/java/com/tcem/validation/repository/ConfirmationDocument.java): Self-contained CouchDB document entity decoupled from transport contracts with `_id`, `_rev`, explicit accessors, and builder.
  - [`ConfirmationCouchDbRepository`](../services/validation-service/src/main/java/com/tcem/validation/repository/ConfirmationCouchDbRepository.java): Document repository supporting `save`, `findById`, `findByTradeId`, `findLatestForTrade` (ordered by `tradeVersion` desc for fingerprint comparison), and `countAll`.
  - [`WebConfig`](../services/validation-service/src/main/java/com/tcem/validation/config/WebConfig.java): Global CORS configuration enabling React UI on ports 3000 and 5173.

- [x] **Phase 2: STP Rules Engine (FR5)** (Completed)
  - [`StpRule`](../services/validation-service/src/main/java/com/tcem/validation/rules/StpRule.java): Common rule interface for stateless, non-short-circuiting validation checks (`void evaluate(Trade trade, StpValidationResult result)`).
  - [`StpValidationResult`](../services/validation-service/src/main/java/com/tcem/validation/rules/StpValidationResult.java): Failure accumulator capturing `category`, `subTheme`, `reasonCode`, and `affectedFields`, reporting `isStpPassed()` and failure summaries.
  - [`EconomicFieldsRule`](../services/validation-service/src/main/java/com/tcem/validation/rules/EconomicFieldsRule.java): Validates mandatory confirmable economics (notional, currency, strike, underlier, premium amount/currency/date, buy/sell, option type) and trade dates; maps specific missing fields to `ExceptionSubTheme`.
  - [`BusinessEligibilityRule`](../services/validation-service/src/main/java/com/tcem/validation/rules/BusinessEligibilityRule.java): Validates `counterpartySTPEligible` and `productSTPEligible` flags from `enrichmentMetadata`; screens for unknown counterparty roles.
  - [`TemplateResolutionRule`](../services/validation-service/src/main/java/com/tcem/validation/rules/TemplateResolutionRule.java): Verifies that `resolvedTemplateId` and `resolvedTemplateVersion` are present from the enrichment catalogue.
  - [`StpRulesEngine`](../services/validation-service/src/main/java/com/tcem/validation/rules/StpRulesEngine.java): Service orchestrator running all registered rules, accumulating all violations, and guarding against uncaught rule exceptions.
  - [`StpValidatedEvent`](../services/validation-service/src/main/java/com/tcem/validation/model/StpValidatedEvent.java): Event model published to `stp.validated` with pass/fail flag, summary, and structured diagnostics.

- [x] **Phase 3: Decision Dispatch & Event Producers** (Completed)
  - [`StpValidatedProducer`](../services/validation-service/src/main/java/com/tcem/validation/producer/StpValidatedProducer.java): Publishes `StpValidatedEvent` to `TopicNames.STP_VALIDATED` partitioned by `tradeId`; falls back to `"UNKNOWN_TRADE"` on null key.
  - [`ConfirmationGeneratedProducer`](../services/validation-service/src/main/java/com/tcem/validation/producer/ConfirmationGeneratedProducer.java): Publishes canonical `ConfirmationEvent` to `TopicNames.CONFIRMATION_GENERATED`; falls back to `"UNKNOWN_CONFIRMATION"` on null key.
  - [`ExceptionCreatedProducer`](../services/validation-service/src/main/java/com/tcem/validation/producer/ExceptionCreatedProducer.java): Publishes canonical `ExceptionEvent` to `TopicNames.EXCEPTION_CREATED` on STP failure; falls back to `"UNKNOWN_EXCEPTION"` on null key.
  - [`DeadLetterProducer`](../services/validation-service/src/main/java/com/tcem/validation/producer/DeadLetterProducer.java): Publishes unrecoverable processing failures to `deadletter.trade`; falls back to `"DLQ_<millis>"` on null key.
  - All four producers use the established codebase pattern: `KafkaOperations<String, String>`, `ObjectMapper`, SLF4J, `try/catch` serialization guard returning `CompletableFuture.failedFuture` on error.

- [x] **Phase 4: Confirmation Generator & Fingerprint Engine (FR6)** (Completed)
  - [`PlainTextConfirmationGenerator.java`](../services/validation-service/src/main/java/com/tcem/validation/confirmation/PlainTextConfirmationGenerator.java): Deterministic, monospace plain-text confirmation generator. Renders structured sections (`TRADE DETAILS`, `PARTIES`, `FX OPTION ECONOMICS`, `NON-ECONOMIC TERMS`, `TEMPLATE REFERENCE`) matching the Operations UI layout. Fully null-safe with `N/A` fallbacks and deterministic string output.
  - [`ConfirmationService.java`](../services/validation-service/src/main/java/com/tcem/validation/confirmation/ConfirmationService.java): Orchestrates the complete confirmation issuance lifecycle:
    - Computes deterministic SHA-256 `ConfirmationFingerprint` over confirmable fields via `ConfirmationFingerprint.compute(trade, objectMapper)`.
    - Duplicate suppression on `AMEND_TRADE`: queries CouchDB via `repository.findLatestForTrade()`; if the fingerprint matches the previous confirmation, suppresses duplicate issuance and returns `Optional.empty()`.
    - Generates confirmation text body via `PlainTextConfirmationGenerator`.
    - Persists `ConfirmationDocument` to CouchDB `confirmations` database with status `ALLEGED`, generated `CONF-` ID, fingerprint, and trade version.
    - Constructs `ConfirmationRecord` and `ConfirmationEvent` envelope with outbound header carrying inbound `correlationId` and `SchemaVersions.CURRENT`.
    - Publishes event to `TopicNames.CONFIRMATION_GENERATED` via `ConfirmationGeneratedProducer`.

- [x] **Phase 5: Exception Routing Mapper (FR7)** (Completed)
  - [`StpExceptionMapper.java`](../services/validation-service/src/main/java/com/tcem/validation/exception/StpExceptionMapper.java): Maps each `StpValidationResult.Failure` into a distinct `ExceptionEvent` containing a structured `ExceptionRecord`:
    - **Queue Routing Strategy:** `MISSING_ECONOMIC_DATA` & `MISSING_STATIC_REFERENCE_DATA` → `DATA_REMEDIATION`; `BUSINESS_RULE_FAILURE` & `TEMPLATE_ISSUE` → `OPERATIONS_REVIEW`; `TECHNICAL_PROCESSING_FAILURE` → `TECHNICAL_SUPPORT`.
    - **Remediation Metadata:** Assigns action guidance, `replayMayResolve` flag (true for missing data & technical errors), `resolutionDifficulty` (`LOW`/`MEDIUM`/`HIGH`), and `operationalImpact` (`HIGH`/`MEDIUM`).
    - **Traceability & Diagnostics:** Sets `tradeId`, `tradeVersion`, `sourceMessageId`, and `correlationId`; defaults `classificationConfidence = 1.0` for deterministic rule output; populates `category`, `subTheme`, `reasonCode`, and `affectedFields`.

- [x] **Phase 6: Kafka Consumer Pipeline & Operations REST APIs** (Completed)
  - [`TradeEnrichedConsumer`](../services/validation-service/src/main/java/com/tcem/validation/consumer/TradeEnrichedConsumer.java): `@KafkaListener` on `trade.enriched` (group `validation-service-group`). Full pipeline: deserialise `TradeEvent`, guard `CANCEL_TRADE` (log and skip — no STP, no confirmation), idempotency check via `confirmationRepository.findLatestForTrade()` — skips if `tradeId + tradeVersion` already confirmed (bypassed for `REPLAY`). Runs `StpRulesEngine`, publishes STP decision to `stp.validated`, then branches: STP pass → `ConfirmationService.processConfirmation()`; STP fail → `StpExceptionMapper` + `ExceptionCreatedProducer` per failure. Irrecoverable parse/processing errors routed to DLQ via `DeadLetterProducer`. *(Parsing and idempotency were upgraded in Phase 8, see below.)*
  - [`ConfirmationQueryController`](../services/validation-service/src/main/java/com/tcem/validation/controller/ConfirmationQueryController.java): `GET /confirmations/{id}` (by ID, 404 if absent), `GET /trades/{tradeId}/confirmations` (all versions newest-first), `GET /trades/{tradeId}/confirmation` (latest, 404 if none), `GET /metrics/stp-rate` (total confirmation count).
  - **Idempotency vs. Fingerprint Deduplication:** Two independent, complementary mechanisms: *(Phase 8 adds a durable `IdempotencyKey` guard in front of the version check.)*
    - *Consumer-level idempotency* (in `TradeEnrichedConsumer`): Prevents the same `tradeId + tradeVersion` from entering the STP pipeline more than once. Bypassed for `REPLAY` event type so operators can force reprocessing after a data fix.
    - *Business-level fingerprint dedup* (in `ConfirmationService`): Prevents re-emitting a `confirmation.generated` event when an amendment (`AMEND_TRADE`) or replayed trade has identical confirmable fields. Compares SHA-256 fingerprints; returns `Optional.empty()` when suppressed. Not bypassed by `REPLAY` — a replay of an unchanged trade still avoids duplicate confirmation emission.
  - **Kafka producer failure semantics (fire-and-forget):** All four producers (`StpValidatedProducer`, `ConfirmationGeneratedProducer`, `ExceptionCreatedProducer`, `DeadLetterProducer`) use `kafkaOperations.send().whenComplete(...)`. Kafka broker failures are logged as warnings but do **not** propagate back to the consumer listener — events are silently lost. Serialisation errors return `CompletableFuture.failedFuture(ex)` and are also fire-and-forget. External monitoring/alerting is required to detect dropped events.

- [x] **Phase 7: Automated Integration Testing & Verification** (Completed)
  - [`TradeEnrichedPipelineIntegrationTest`](../services/validation-service/src/test/java/com/tcem/validation/integration/TradeEnrichedPipelineIntegrationTest.java) + [`IntegrationTestConsumer`](../services/validation-service/src/test/java/com/tcem/validation/integration/IntegrationTestConsumer.java): embedded-Kafka end-to-end tests of `trade.enriched` → STP evaluation → `stp.validated` + (`confirmation.generated` | `exception.created`), with CouchDB mocked at the repository/client boundary.
  - Covers: STP pass + confirmation, STP fail + exception (no confirmation), consumer-level idempotency skip, amendment fingerprint suppression, and REPLAY bypass with fingerprint suppression.
  - Full reactor build verified: 124 tests, 0 failures, 0 errors.

---

- [x] **Phase 8: Sprint 2 Hardening** (Completed)
  - Flow in `TradeEnrichedConsumer.onMessage`: route payload via `SchemaVersionRouter` -> validate identity fields -> skip `CANCEL_TRADE` -> unless `REPLAY`: skip if `IdempotencyKey` already recorded, then skip if the trade version is already confirmed -> STP rules -> publish `stp.validated` -> confirmation or exceptions -> record `IdempotencyKey`. Any exception on this path dead-letters the message and leaves the key unrecorded, so redelivery or replay retries it.
  - Key format is the shared `idempotency:{tradeId}:{messageId}:{schemaVersion}:{eventType}`; documents are stored as `validation-<key>` in `validation-idempotency`, so they cannot collide with other services' markers.
  - Rationale for keeping the older version guard: it still protects against a different `messageId` carrying an already-confirmed `tradeId + tradeVersion` (for example an upstream re-publish).

---

## 4. Phased Implementation Roadmap for Mahadev

| Phase | Title | Key Deliverables | Verification / Test | Status |
| --- | --- | --- | --- | --- |
| **Phase 1** | **CouchDB Client & Persistence Layer** | `CouchDbClient`, `CouchDbConfig`, `ConfirmationDocument`, `ConfirmationCouchDbRepository`, auto-init for `confirmations` DB. | Repository unit tests with CouchDB test double; verify database initialization. | **Completed** |
| **Phase 2** | **STP Rules Engine (FR5)** | `StpRule`, `StpValidationResult`, `EconomicFieldsRule`, `BusinessEligibilityRule`, `TemplateResolutionRule`, `StpRulesEngine`, `StpValidatedEvent`. | Unit tests covering 10+ validation pass/fail combinations; verify failure accumulation. | **Completed** |
| **Phase 3** | **Decision Dispatch & Producers** | `StpValidatedProducer`, `ConfirmationGeneratedProducer`, `ExceptionCreatedProducer`, `DeadLetterProducer`. | Producer unit tests verifying Kafka template interactions, message keys, and headers. | **Completed** |
| **Phase 4** | **Confirmation Generator & Fingerprinting (FR6)** | `PlainTextConfirmationGenerator`, `ConfirmationService`, `ConfirmationFingerprint` party resolution, duplicate suppression on `AMEND_TRADE`. | Unit tests verifying plain-text template formatting, fingerprint stability, and amendment duplicate suppression. | **Completed** |
| **Phase 5** | **Exception Routing Mapper (FR7)** | `StpExceptionMapper` mapping rule failures to `ExceptionRecord` with category, subtheme, queue, and impact. | Unit tests validating exception classification against spec §12 taxonomy. | **Completed** |
| **Phase 6** | **Kafka Pipeline & REST APIs** | `TradeEnrichedConsumer` on `trade.enriched`, idempotency guard, STP evaluation, dual-dispatch, DLQ; `ConfirmationQueryController` (`GET /confirmations/{id}`, `GET /trades/{tradeId}/confirmations`, `GET /trades/{tradeId}/confirmation`, `GET /metrics/stp-rate`). | 9 consumer unit tests + 8 MockMvc controller tests (17 total). Full suite at the time: 119 tests passing. | **Completed** |
| **Phase 7** | **End-to-End Integration Testing & Verification** | Complete pipeline integration test (`trade.enriched` -> `stp.validated` + confirmation/exception), CouchDB & Kafka verification, Maven reactor build. | 5 embedded-Kafka integration tests green; reactor builds cleanly — 124 tests, 0 failures. | **Completed** |
| **Phase 8** | **Sprint 2 Hardening** | `ValidationIdempotencyRepository`, `IdempotencyKey` guard and `SchemaVersionRouter` parsing in `TradeEnrichedConsumer`, `validation-idempotency` database, topic wiring cleanup, fingerprint party-path fix. | `TradeEnrichedConsumerTest` 9 -> 17 tests; reactor `mvn clean verify` green (validation-service 132 tests); live duplicate-skip and unsupported-schema DLQ checks. | **Completed** |

---

## 5. Teammate Communication Contracts

### A. Upstream: Bhavik (`enrichment-service` — PR #5 Merged)

- **Topic Consumed:** `trade.enriched`
- **Key:** `tradeId`
- **Payload:** Canonical `TradeEvent` with populated `trade.enrichmentMetadata` (`counterpartySTPEligible`, `productSTPEligible`, `resolvedTemplateId`, `resolvedTemplateVersion`).
- **Contract guarantees:**
  - `counterpartySTPEligible`: `true` if eligible, `false` or `null` if not eligible / unknown.
  - `productSTPEligible`: `true` if eligible, `false` or `null` if not configured.
  - `resolvedTemplateId`: e.g. `TMPL-FX-OPT-001`, or `null` if unresolved.
  - `resolvedTemplateVersion`: e.g. `1.2.0`, or `null` if unresolved.
  - `parties`: In-place enriched with `legalEntityName`, `lei`, and `country`.

### B. Downstream: Kapish (`exception-service`)

- **Topic Produced:** `exception.created`
- **Key:** `tradeId`
- **Payload:** Canonical `ExceptionEvent` wrapping `ExceptionRecord`.
- **Contract guarantees:**
  - `category` populated with one of `ExceptionCategory` (e.g., `MISSING_ECONOMIC_DATA`, `BUSINESS_RULE_FAILURE`, `TEMPLATE_ISSUE`, `TECHNICAL_PROCESSING_FAILURE`).
  - `subTheme` populated with corresponding `ExceptionSubTheme`.
  - `affectedFields` populated with comma-separated field names.
  - `ownerQueue` selected according to exception category: `DATA_REMEDIATION`, `OPERATIONS_REVIEW`, or `TECHNICAL_SUPPORT`.
  - `sourceMessageId` and `correlationId` linking back to original message.

### C. Downstream: Oren (`workitem-service` — PR #4 Merged) & Frontend (`frontend` — PR #3 Merged)

- **Topics Produced:** `stp.validated` and `confirmation.generated`
- **Database:** CouchDB `confirmations`
- **Key:** `tradeId`
- **REST Endpoints Exposed (Phase 6 — Live):**
  - `GET /confirmations/{id}`: Returns a single confirmation by `confirmationId`; 404 if not found.
  - `GET /trades/{tradeId}/confirmations`: Returns all confirmation versions for a trade, ordered newest-first.
  - `GET /trades/{tradeId}/confirmation`: Returns the latest confirmation for a trade; 404 if none.
  - `GET /metrics/stp-rate`: Returns `{ "totalConfirmations": <count> }` — total count of persisted confirmations, used as a simple STP throughput proxy for the Operations dashboard. Not a ratio; named for UI alignment with FR11 "STP rate" dashboard card.

---

## 6. Key Reference Files

1. [Architecture Overview](../docs/architecture.md)
2. [Kafka Topics Definition](../docs/kafka-topics.md)
3. [Project Status Tracker](../docs/project-status.md)
4. [Contributing Guidelines](../CONTRIBUTING.md)
5. [Ingestion Service Progress](../docs/ingestion-service-progress.md)
6. [Capstone Specification](../spec.txt)

---

If context is lost, read these files in order:

### A. Architecture & Workflow Documentation

1. [`README.md`](../README.md) — System summary, port mappings, and running instructions.
2. [`CONTRIBUTING.md`](../CONTRIBUTING.md) — Git workflow, branch conventions, PR rules.
3. [`docs/architecture.md`](../docs/architecture.md) — Event pipeline, topic sequence, and service boundaries.
4. [`docs/kafka-topics.md`](../docs/kafka-topics.md) — Topic specifications, message keys, and schemas.
5. [`docs/project-status.md`](../docs/project-status.md) — Status across all 6 teammates.
6. [`spec.txt`](../spec.txt) — Capstone specifications (Sections 5, 7, 8, 10, 11, 12, 13).
7. [`docs/ingestion-service-progress.md`](../docs/ingestion-service-progress.md) — Reference implementation guide.

### B. Shared Domain Contracts (`common-lib`)

1. [`TradeEvent.java`](../services/common-lib/src/main/java/com/tcem/common/model/message/TradeEvent.java) — Top-level Kafka envelope.
2. [`Trade.java`](../services/common-lib/src/main/java/com/tcem/common/model/trade/Trade.java) — Canonical trade entity.
3. [`ConfirmationRecord.java`](../services/common-lib/src/main/java/com/tcem/common/model/confirmation/ConfirmationRecord.java) — Confirmation data structure.
4. [`ConfirmationFingerprint.java`](../services/common-lib/src/main/java/com/tcem/common/registry/ConfirmationFingerprint.java) — Deterministic SHA-256 fingerprinting utility.
5. [`ConfirmableFieldRegistry.java`](../services/common-lib/src/main/java/com/tcem/common/registry/ConfirmableFieldRegistry.java) — Whitelist of confirmable fields for FX Option.
6. [`ExceptionRecord.java`](../services/common-lib/src/main/java/com/tcem/common/model/exception/ExceptionRecord.java) — Structured exception entity.

### C. Validation Service Files

1. [`services/validation-service/src/main/resources/application.yml`](../services/validation-service/src/main/resources/application.yml) — Kafka, CouchDB, and service configuration.
2. [`StpRulesEngine.java`](../services/validation-service/src/main/java/com/tcem/validation/rules/StpRulesEngine.java) — Core validation engine.
3. [`ConfirmationCouchDbRepository.java`](../services/validation-service/src/main/java/com/tcem/validation/repository/ConfirmationCouchDbRepository.java) — Persistence repository.
