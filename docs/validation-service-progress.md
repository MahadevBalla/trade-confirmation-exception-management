# Validation Service — Progress Tracker & Implementation Guide

> **Author / Owner:** Mahadev Balla (Backend Engineer)  
> **Service:** `validation-service` (Port 8083)  
> **Feature Branch:** `feat/validation-service` (cut from `main`)  
> **Key Specs:** FR5 (STP Validation), FR6 (Confirmation Generation), FR7 (Exception Routing)  
> **Key Specs:** FR5 (STP Validation), FR6 (Confirmation Generation & Duplicate Suppression), FR7 (Exception Routing)  
> **Last Updated:** October 2026 (In Progress)

---

## 1. Quick Context & Purpose

The **Validation Service** acts as the core decision-making and confirmation issuance authority of the TCEM platform:

1. **Consumes Enriched Trades (FR5):** Subscribes to `trade.enriched` topic published upstream by `enrichment-service`.
2. **Evaluates STP Rules Engine (FR5):** Checks mandatory confirmable economic terms, counterparty and product STP eligibility flags, template applicability, date coherence, and non-economic parameters.
3. **Publishes STP Decisions:** Publishes canonical audit outcome to `stp.validated` topic with rich validation metadata and decision status (`STP_PASSED` vs `STP_FAILED`).
4. **Consumes Enriched Trades (FR5):** Subscribes to the `trade.enriched` topic published upstream by `enrichment-service`.
5. **Evaluates STP Rules Engine (FR5):** Checks mandatory confirmable economic terms, counterparty and product STP eligibility flags, confirmation template applicability, and date chronology without short-circuiting.
6. **Publishes STP Decisions:** Publishes canonical audit outcome events to `stp.validated` with validation metadata and decision status (`STP_PASSED` vs `STP_FAILED`).
7. **Executes Confirmation Generation & Duplicate Suppression (FR6):**
   - Renders deterministic plain-text confirmation notices based on resolved template identifiers.
   - Computes SHA-256 fingerprint over confirmable contract terms via `ConfirmationFingerprint`.
   - On `AMEND_TRADE`, detects if only non-confirmable metadata altered; suppresses duplicate confirmation generation and avoids redundant counterparty transmissions ("nothing changed = don't resend").
   - Persists confirmation records into CouchDB `confirmations` database and dispatches `ConfirmationEvent` to `confirmation.generated`.
8. **Routes STP Exceptions (FR7):** Formulates structured `ExceptionRecord` with appropriate classification category (`BUSINESS_RULE_FAILURE`, `TEMPLATE_ISSUE`, `MISSING_ECONOMIC_DATA`, etc.), sub-theme, recommended remediation action, and target queue (`OPERATIONS_REVIEW`, `DATA_REMEDIATION`), dispatching `ExceptionEvent` to `exception.created`.
9. **Serves Operations REST APIs:** Exposes `GET /confirmations/{id}`, `GET /trades/{tradeId}/confirmation`, and `GET /metrics/stp-rate` for the React Operations UI via Nginx API Gateway.
   - Computes deterministic SHA-256 fingerprint over confirmable contract terms via `ConfirmationFingerprint`.
   - On `AMEND_TRADE`, queries CouchDB for the prior confirmation; if confirmable terms have not changed, suppresses duplicate confirmation generation and avoids redundant counterparty transmissions.
   - Persists confirmation records into the CouchDB `confirmations` database and dispatches `ConfirmationEvent` to `confirmation.generated`.
10. **Routes STP Exceptions (FR7):** Formulates structured `ExceptionRecord` entries with appropriate classification (`BUSINESS_RULE_FAILURE`, `TEMPLATE_ISSUE`, `MISSING_ECONOMIC_DATA`), sub-theme, recommended remediation action, and target queue (`OPERATIONS_REVIEW`, `DATA_REMEDIATION`), dispatching `ExceptionEvent` to `exception.created`.
11. **Serves Operations REST APIs:** Exposes `GET /confirmations/{id}`, `GET /trades/{tradeId}/confirmation`, and `GET /metrics/stp-rate` for the React Operations UI via the Nginx API Gateway.

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
- [x] **Phase 2 Unit Tests — 54 tests, all passing:**
  - [`StpValidationResultTest`](../services/validation-service/src/test/java/com/tcem/validation/rules/StpValidationResultTest.java) — 9 tests: pass/fail flag, multi-failure accumulation, `summarize()`, immutable list contract.
  - [`EconomicFieldsRuleTest`](../services/validation-service/src/test/java/com/tcem/validation/rules/EconomicFieldsRuleTest.java) — 17 tests: null trade, null economics block, each confirmable field missing/blank, all three date fields, multi-field non-short-circuit aggregation.
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

### What is REMAINING (To Be Built)

- [ ] **Phase 4: Confirmation Generator & Fingerprint Engine (FR6):**
  - `PlainTextConfirmationGenerator.java` (deterministic plain-text rendering matching frontend display expectations).
  - `ConfirmationService.java` (orchestrating generation, resolving `parties[ROLE]` for `ConfirmationFingerprint`, CouchDB persistence, and duplicate suppression on `AMEND_TRADE`).
- [ ] **Phase 5: Exception Routing Mapper (FR7):**
  - `StpExceptionMapper.java` (mapping `StpValidationResult.Failure` records to structured `ExceptionRecord` and `ExceptionEvent`).
- [ ] **Phase 6: Kafka Consumer Pipeline & Operations REST APIs:**
  - `TradeEnrichedConsumer.java` (`@KafkaListener` consuming `trade.enriched`, performing idempotency checking, executing STP engine, and performing dual-dispatch).
  - `ConfirmationQueryController.java` (`GET /confirmations/{id}`, `GET /trades/{tradeId}/confirmation`, `GET /metrics/stp-rate`).
- [ ] **Phase 7: Automated Testing & Verification:**
  - Unit tests for rules engine, repository, confirmation generator, mapper, and producers.
  - End-to-end integration tests verifying complete pipeline from `trade.enriched` through CouchDB persistence and Kafka outputs.

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

- [ ] **Phase 4: Confirmation Generator & Fingerprint Engine (FR6)** (Pending)
  - `PlainTextConfirmationGenerator`: Renders deterministic plain-text confirmation notices based on template and trade terms, structured for monospace display in the Operations UI.
  - `ConfirmationService`: Orchestrates confirmation creation, party extraction for `ConfirmationFingerprint` (`parties[ROLE]` resolution), comparison with `previousConfirmationFingerprint` to suppress duplicate confirmations on `AMEND_TRADE`, and CouchDB persistence.

- [ ] **Phase 5: Exception Routing Mapper (FR7)** (Pending)
  - `StpExceptionMapper`: Maps `StpValidationResult.Failure` records to structured `ExceptionRecord` and `ExceptionEvent` with categories, themes, queue routing (`DATA_REMEDIATION` vs `OPERATIONS_REVIEW`), and resolution hints.

- [ ] **Phase 6: Kafka Consumer Pipeline & Operations REST APIs** (Pending)
  - `TradeEnrichedConsumer`: `@KafkaListener` consuming `trade.enriched`, performing idempotency checking, executing STP engine, and performing dual-dispatch (`stp.validated` + outcome branch).
  - `ConfirmationQueryController`: REST controller exposing `GET /confirmations/{id}`, `GET /trades/{tradeId}/confirmation`, and `GET /metrics/stp-rate`.

- [ ] **Phase 7: Automated Testing & Verification** (Pending)
  - Unit tests for rules engine, repository, confirmation generator, producers, and mapper.
  - End-to-end integration tests verifying complete pipeline from `trade.enriched` through CouchDB persistence and Kafka outputs.

---

## 4. Phased Implementation Roadmap for Mahadev

| Phase | Title | Key Deliverables | Verification / Test | Status |
| --- | --- | --- | --- | --- |
| **Phase 1** | **CouchDB Client & Persistence Layer** | `CouchDbClient`, `CouchDbConfig`, `ConfirmationDocument`, `ConfirmationCouchDbRepository`, auto-init for `confirmations` DB. | Repository unit tests with CouchDB test double; verify database initialization. | **Completed** |
| **Phase 2** | **STP Rules Engine (FR5)** | `StpRule`, `StpValidationResult`, `EconomicFieldsRule`, `BusinessEligibilityRule`, `TemplateResolutionRule`, `StpRulesEngine`, `StpValidatedEvent`. | Unit tests covering 10+ validation pass/fail combinations; verify failure accumulation. | **Completed** |
| **Phase 3** | **Decision Dispatch & Producers** | `StpValidatedProducer`, `ConfirmationGeneratedProducer`, `ExceptionCreatedProducer`, `DeadLetterProducer`. | Producer unit tests verifying Kafka template interactions, message keys, and headers. | **Completed** |
| **Phase 4** | **Confirmation Generator & Fingerprinting (FR6)** | `PlainTextConfirmationGenerator`, `ConfirmationService`, `ConfirmationFingerprint` party resolution, duplicate suppression on `AMEND_TRADE`. | Unit tests verifying plain-text template formatting, fingerprint stability, and amendment duplicate suppression. | **Pending** |
| **Phase 5** | **Exception Routing Mapper (FR7)** | `StpExceptionMapper` mapping rule failures to `ExceptionRecord` with category, subtheme, queue, and impact. | Unit tests validating exception classification against spec §12 taxonomy. | **Pending** |
| **Phase 6** | **Kafka Pipeline & REST APIs** | `TradeEnrichedConsumer` on `trade.enriched`, idempotency handling, `ConfirmationQueryController` (`/confirmations/{id}`, `/metrics/stp-rate`). | MockMvc tests for endpoints, consumer message processing tests. | **Pending** |
| **Phase 7** | **End-to-End Verification & Verification** | Full pipeline integration test (`trade.enriched` -> `stp.validated` + confirmation/exception), Maven reactor build check. | All unit and integration tests green; reactor builds cleanly. | **Pending** |

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
  - `category` populated with one of `ExceptionCategory` (e.g., `MISSING_ECONOMIC_DATA`, `BUSINESS_RULE_FAILURE`, `TEMPLATE_ISSUE`).
  - `subTheme` populated with corresponding `ExceptionSubTheme`.
  - `affectedFields` populated with comma-separated field names.
  - `ownerQueue` set to `OPERATIONS_REVIEW` or `DATA_REMEDIATION`.
  - `sourceMessageId` and `correlationId` linking back to original message.

### C. Downstream: Oren (`workitem-service` — PR #4 Merged) & Frontend (`frontend` — PR #3 Merged)

- **Topics Produced:** `stp.validated` and `confirmation.generated`
- **Database:** CouchDB `confirmations`
- **Key:** `tradeId`
- **REST Endpoints Exposed:**
  - `GET /confirmations/{id}`: Returns confirmation details by confirmation ID.
  - `GET /trades/{tradeId}/confirmation`: Returns latest confirmation for a given trade.
  - `GET /metrics/stp-rate`: Returns aggregate STP rate metrics for dashboard display.

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
