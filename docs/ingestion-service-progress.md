# Ingestion Service — Progress Tracker & Implementation Guide

> **Author / Owner:** Mayank Chauhan (Team Lead & Ingestion Service Lead)  
> **Service:** `ingestion-service` (Port 8081)  
> **Feature Branch:** `feat/ingestion-service` (cut from `main`)  
> **Key Specs:** FR1 (Event Intake), FR2 (Schema Validation), FR3 (Persistence), FR12 (DLQ / Error Intake)  
> **Last Updated:** October 2026

---

## 1. Quick Context & Purpose

The **Ingestion Service** is the entry gateway of the entire Trade Confirmation & Exception Management Platform. It is an event-driven ingestion pipeline and data generator that:
1. **Generates / Ingests Synthetic Trades (FR1):** Produces realistic FpML-inspired JSON post-trade messages to the `trade.raw` Kafka topic from seed files or programmatic generators.
2. **Validates Inbound Trade Schemas (FR2):** Consumes messages from `trade.raw`, checks header metadata, structural JSON rules, and product economics (e.g., FX Option mandatory fields).
3. **Manages Idempotency & Persistence (FR3):** Uses `IdempotencyKey` to prevent duplicate processing, persists raw and validated trade state to Apache CouchDB (`trades` database), and writes immutable audit logs to CouchDB (`audit-events`).
4. **Routes Outcomes Downstream:**
   - **Valid Trades:** Publishes canonical `TradeEvent` to `trade.accepted` (consumed by Gunjan's `enrichment-service`).
   - **Validation Failures / Duplicates:** Publishes `ExceptionEvent` to `exception.created` (consumed by Kapish's `exception-service`) or routes unparseable payloads to `deadletter.trade` (FR12).
5. **Serves Operations REST APIs:** Provides `GET /trades/{id}` and `GET /trades?status=&page=` for Oren's React Operations UI (FR11).

---

## 2. Status: What is Done vs. What is Remaining

### What is DONE:
- [x] **Shared Event Schema & Models (`common-lib`):**
  - Canonical FpML-inspired models: [`Trade`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/model/trade/Trade.java), [`MessageHeader`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/model/message/MessageHeader.java), [`TradeEvent`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/model/message/TradeEvent.java), [`FxOptionEconomics`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/model/trade/FxOptionEconomics.java), [`Party`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/model/trade/Party.java), [`NonEconomicTerms`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/model/trade/NonEconomicTerms.java).
  - Version routing mechanism: [`SchemaVersionRouter`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/routing/SchemaVersionRouter.java).
  - Idempotency key pattern: [`IdempotencyKey`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/idempotency/IdempotencyKey.java).
  - Standard topic names: [`TopicNames`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/constants/TopicNames.java).
  - Exception taxonomies: [`ExceptionCategory`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/constants/ExceptionCategory.java), [`ExceptionSubTheme`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/constants/ExceptionSubTheme.java).
  - Fingerprinting & confirmable field registry: [`ConfirmationFingerprint`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/registry/ConfirmationFingerprint.java).
- [x] **Repository Skeleton & Build Reactor:**
  - Parent Maven reactor [`services/pom.xml`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/pom.xml) compiling all 6 submodules with Java 21 and Spring Boot 3.3.4.
  - Multi-service Docker Compose configuration [`docker-compose.yml`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/docker-compose.yml).
  - Initial `ingestion-service` skeleton with Spring Boot Actuator and Spring Kafka test dependencies.

---

- [x] **Phase 1: Ingestion Infrastructure & CouchDB Client** (Completed)
  - [`CouchDbProperties`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/config/CouchDbProperties.java): Config properties reading `couchdb.*` with env defaults.
  - [`CouchDbOperations`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/couchdb/CouchDbOperations.java) & [`CouchDbClient`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/couchdb/CouchDbClient.java): Native Spring Boot 3.3 `RestClient` client with Basic Auth, auto DB creation (`trades`, `audit-events`), conflict retry handling, and Mango `_find` queries.
  - [`CouchDbConfig`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/config/CouchDbConfig.java): Spring beans + `ApplicationRunner` database auto-initializer with graceful fallback when CouchDB is offline during local dev.
  - Document models: [`TradeDocument`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/model/document/TradeDocument.java), [`AuditDocument`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/model/document/AuditDocument.java), [`IdempotencyDocument`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/model/document/IdempotencyDocument.java).
  - Repositories: [`TradeCouchDbRepository`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/repository/TradeCouchDbRepository.java), [`AuditCouchDbRepository`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/repository/AuditCouchDbRepository.java), [`IdempotencyCouchDbRepository`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/repository/IdempotencyCouchDbRepository.java).
  - Actuator Health: [`CouchDbHealthIndicator`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/health/CouchDbHealthIndicator.java).
  - Unit Tests: 15/15 passing tests across [`CouchDbClientTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/couchdb/CouchDbClientTest.java), [`TradeCouchDbRepositoryTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/repository/TradeCouchDbRepositoryTest.java), [`IdempotencyCouchDbRepositoryTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/repository/IdempotencyCouchDbRepositoryTest.java), and [`IngestionServiceApplicationTests`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/IngestionServiceApplicationTests.java).
- [x] **Phase 2: Synthetic Trade Generator & Seed Loader (FR1)** (Completed)
  - [`TradeScenarioType`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/generator/TradeScenarioType.java): 10 granular scenarios (standard STP, missing strike/notional/premium, unknown counterparty, template mismatch, non-STP jurisdiction, duplicate, malformed JSON, amendment).
  - [`SyntheticTradeGenerator`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/generator/SyntheticTradeGenerator.java): Generates individual scenarios or 500+ trade batches (70% STP, 30% realistic edge cases/exceptions).
  - Seed Dataset [`sample-trades.json`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/resources/seed/sample-trades.json): 500 pre-generated synthetic trades with realistic parties, notionals, FX pairs (EUR/USD, GBP/USD, USD/JPY, AUD/USD).
  - [`TradeRawProducer`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/producer/TradeRawProducer.java): Kafka publisher dispatching to `trade.raw` partitioned by `tradeId`.
  - [`TradeProducerController`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/controller/TradeProducerController.java): REST endpoints (`POST /trades/generate`, `POST /trades/generate/scenario`, `POST /trades/seed`, `GET /trades/scenarios`).
  - Unit & Controller Tests: 12 passing tests across [`SyntheticTradeGeneratorTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/generator/SyntheticTradeGeneratorTest.java), [`TradeRawProducerTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/producer/TradeRawProducerTest.java), [`TradeProducerControllerTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/controller/TradeProducerControllerTest.java). Total 27/27 tests green.
- [x] **Phase 3: Schema Validation Engine (FR2)** (Completed)
  - [`ValidationError`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/validator/ValidationError.java): Structured validation error model capturing field name, message, `ExceptionCategory`, `ExceptionSubTheme`, and diagnostic reason code.
  - [`ValidationResult`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/validator/ValidationResult.java): Container with error aggregation, summaries, and factory converting validation failures into platform-standard [`ExceptionRecord`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/model/exception/ExceptionRecord.java) entries (with `ownerQueue`, `recommendedAction`, `operationalImpact`, and `replayMayResolve` flags).
  - [`TradeValidator`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/validator/TradeValidator.java): Comprehensive validation engine checking:
    * Envelope & headers (`messageId`, `correlationId`, `eventType`, `schemaVersion` against supported versions).
    * Core identifiers and product type (`tradeId`, `sourceSystemTradeId`, `productType`).
    * Date constraints and chronological order (`tradeDate <= effectiveDate <= maturityDate`).
    * Party constraints (at least 2 parties, executing `BANK` and client `COUNTERPARTY` with valid IDs).
    * FX Option economics (`strike > 0`, `notionalAmount > 0`, ISO 4217 currencies, `CALL`/`PUT`, `BUY`/`SELL`, currency pair `underlier`, non-negative `premiumAmount`, `premiumPaymentDate >= tradeDate`).
    * Non-economic terms (screening for non-STP jurisdictions and missing template hints).
    * Raw JSON parsing with automatic syntax error capture (`PARSE_FAILURE`).
  - Unit Tests: 15 passing tests in [`TradeValidatorTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/validator/TradeValidatorTest.java) covering standard STP, missing strike/notional/premium/underlier, inverted dates, invalid currencies, unparseable JSON, and ExceptionRecord generation. Total 42/42 tests green.
- [x] **Phase 4: Kafka Stream Processing Pipeline & Idempotency** (Completed)
  - [`TradeAcceptedProducer`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/producer/TradeAcceptedProducer.java): Kafka publisher dispatching schema-validated canonical `TradeEvent` to `trade.accepted` partitioned by `tradeId`.
  - [`ExceptionCreatedProducer`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/producer/ExceptionCreatedProducer.java): Kafka publisher dispatching structured `ExceptionEvent` instances to `exception.created` for downstream triage.
  - [`DeadLetterProducer`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/producer/DeadLetterProducer.java): Kafka publisher routing unparseable and fatal payloads to `deadletter.trade` (FR12) with diagnostic context (stack trace excerpt, failed service, replayable flag).
  - [`TradeIngestionService`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/service/TradeIngestionService.java) & [`DefaultTradeIngestionService`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/service/DefaultTradeIngestionService.java):
    * Pipeline step 1: Validates raw JSON syntax; routes fatal parsing errors to `deadletter.trade` and writes audit event.
    * Pipeline step 2: Resolves composite `IdempotencyKey` (`tradeId:messageId:schemaVersion:eventType`).
    * Pipeline step 3: Queries CouchDB idempotency store; deduplicates retransmitted messages, writes audit log, and emits duplicate exception to `exception.created`.
    * Pipeline step 4: Executes `TradeValidator` (FR2) on inbound trade.
    * Pipeline step 5 (SUCCESS): Persists `TradeDocument` (`status: ACCEPTED`), records idempotency key, records audit event (`stage: SCHEMA_VALIDATION`), and produces canonical `TradeEvent` to `trade.accepted`.
    * Pipeline step 6 (FAILURE): Persists `TradeDocument` (`status: SCHEMA_INVALID`), records idempotency key, records audit event, generates canonical `ExceptionRecord`s, and produces `ExceptionEvent` to `exception.created`.
  - [`TradeRawConsumer`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/consumer/TradeRawConsumer.java): `@KafkaListener` consuming messages from `trade.raw` with group `ingestion-service-group` and delegating to `TradeIngestionService`.
  - [`IngestionResult`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/model/IngestionResult.java): Outcome model capturing processing status (`ACCEPTED`, `DUPLICATE`, `REJECTED`, `DEAD_LETTER`).
  - Unit & Pipeline Tests: 11 passing tests across [`TradeAcceptedProducerTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/producer/TradeAcceptedProducerTest.java), [`ExceptionCreatedProducerTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/producer/ExceptionCreatedProducerTest.java), [`DeadLetterProducerTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/producer/DeadLetterProducerTest.java), [`TradeRawConsumerTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/consumer/TradeRawConsumerTest.java), and [`TradeIngestionServiceTest`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/test/java/com/tcem/ingestion/service/TradeIngestionServiceTest.java). Total 53/53 tests green.
- [ ] **Phase 5: Operations REST APIs (FR3, FR11)**
  - `GET /trades/{id}`: Fetches trade document and processing history by `tradeId`.
  - `GET /trades?status={status}&page={page}&size={size}`: Paginated search of trades.
  - Actuator metrics: Ingestion throughput, validation success rate, error count.
- [ ] **Phase 6: Automated Testing & Verification**
  - Comprehensive unit tests for `TradeValidator` across all edge cases (missing fields, invalid dates, negative notionals).
  - Integration tests using `@EmbeddedKafka` and mock CouchDB server testing the end-to-end `trade.raw` -> `trade.accepted` pipeline.

---

## 3. Essential Files to Read Before Starting Work

If context is lost, or another engineer/agent needs to understand or resume work on `ingestion-service`, read these files in order:

### A. Architecture & Workflow Documentation
1. [`README.md`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/README.md) — System summary, port mappings, and running instructions.
2. [`CONTRIBUTING.md`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/CONTRIBUTING.md) — Git workflow, branch conventions (`<name>/<feature>`), commit style, PR checklist.
3. [`docs/architecture.md`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/docs/architecture.md) — Event flow, topic sequence, and service boundaries.
4. [`docs/kafka-topics.md`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/docs/kafka-topics.md) — Complete list of Kafka topics, producers, consumers, and payload keys.
5. [`docs/project-status.md`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/docs/project-status.md) — Status across all 6 teammates.
6. [`architecture-diagram.drawio.xml`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/architecture-diagram.drawio.xml) — Comprehensive architectural schema (FR6–FR12), REST API routes, and retry tiers.
7. [`spec.txt`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/spec.txt) — Full functional specifications (Sections 5, 7, 8, 10, 11, 12, 13).

### B. Shared Domain Contracts (`common-lib`)
1. [`TradeEvent.java`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/model/message/TradeEvent.java) — Top-level Kafka envelope.
2. [`MessageHeader.java`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/model/message/MessageHeader.java) — Header containing `messageId`, `correlationId`, `eventType`, `schemaVersion`.
3. [`Trade.java`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/model/trade/Trade.java) — Canonical trade entity with `parties`, `fxOptionEconomics`, `nonEconomicTerms`.
4. [`TopicNames.java`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/constants/TopicNames.java) — Constant topic strings (`TRADE_RAW`, `TRADE_ACCEPTED`, `EXCEPTION_CREATED`, `DEADLETTER_TRADE`).
5. [`ProcessingStatus.java`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/constants/ProcessingStatus.java) — Trade lifecycle statuses (`RAW_RECEIVED`, `ACCEPTED`, `REJECTED`, etc.).
6. [`IdempotencyKey.java`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/idempotency/IdempotencyKey.java) — Storage key generator for duplicate suppression.
7. [`SchemaVersionRouter.java`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/common-lib/src/main/java/com/tcem/common/routing/SchemaVersionRouter.java) — Payload routing and version migration utility.

### C. Ingestion Service Files
1. [`services/ingestion-service/pom.xml`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/pom.xml) — Service dependencies (`spring-boot-starter-web`, `spring-kafka`, `common-lib`).
2. [`services/ingestion-service/src/main/resources/application.yml`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/resources/application.yml) — Kafka bootstrap servers, CouchDB URL, server port (8081).
3. [`services/ingestion-service/src/main/java/com/tcem/ingestion/IngestionServiceApplication.java`](file:///Users/mayankchauhan/Documents/trade-confirmation-exception-management/services/ingestion-service/src/main/java/com/tcem/ingestion/IngestionServiceApplication.java) — Spring Boot entry point.

---

## 4. Phased Implementation Roadmap for Mayank

| Phase | Title | Key Deliverables | Verification / Test |
|---|---|---|---|
| **Phase 1** | **CouchDB Client & Persistence Layer** | `CouchDbClient`, `CouchDbConfig`, Document models (`TradeDocument`, `AuditDocument`), Database auto-initialization (`trades`, `audit-events`). | Unit tests with MockWebServer / CouchDB HTTP mock. |
| **Phase 2** | **Synthetic Trade Generator & Seed Loader (FR1)** | `TradeGeneratorService`, seed dataset loader (`sample-trades.json`), REST endpoints (`POST /trades/generate`, `POST /trades/seed`), Kafka producer pushing to `trade.raw`. | Test generating 500 diverse trades (happy path + 6 exception types). Verify messages in Kafka UI or logs. |
| **Phase 3** | **Validation Engine (FR2)** | `TradeValidator`, `ValidationResult`, checks for headers, dates, parties, and FX Option mandatory fields. Generates structured `ExceptionRecord` on errors. | Unit test suite testing 15+ validation failure scenarios. |
| **Phase 4** | **Kafka Pipeline & Idempotency** | `TradeRawConsumer`, idempotency filter using CouchDB, persistence of raw trade, routing to `trade.accepted` or `exception.created` / `deadletter.trade`. | EmbeddedKafka integration test validating event flow from `trade.raw` to `trade.accepted` and CouchDB. |
| **Phase 5** | **Operations REST APIs & Health (FR3, FR11)** | `TradeQueryController` exposing `GET /trades/{id}` and `GET /trades?status=&page=`, custom Actuator health indicator. | MockMvc tests for endpoints. |
| **Phase 6** | **End-to-End Verification & Documentation** | Verify full workflow with Docker Compose / local test runner, update docs, prepare for Gunjan's handoff. | All reactor tests pass (`mvn verify`). |

---

## 5. Teammate Communication Contract (Handoff to Gunjan)

- **Topic produced:** `trade.accepted`
- **Kafka message key:** `tradeId` (e.g., `TRD-10001`)
- **Payload format:** Serialized JSON of canonical `TradeEvent`
- **Guarantee:** Every message on `trade.accepted` has:
  * Non-null `messageHeader` with `schemaVersion: "capstone-fpml-json-1.0"`, valid `messageId`, `correlationId`, and `eventType: NEW_TRADE`.
  * Non-null `trade` with valid `tradeId`, `tradeDate`, `effectiveDate`, `maturityDate`.
  * Exactly 2 parties: `BANK` and `COUNTERPARTY`.
  * Complete `fxOptionEconomics` with non-null `strike`, `notionalAmount`, `notionalCurrency`, `buySell`, `optionType`, `underlier`, `premiumAmount`, `premiumCurrency`.
  * Raw state stored in CouchDB `trades` with status `ACCEPTED`.
