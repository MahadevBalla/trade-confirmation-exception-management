# Exception Service — Progress Tracker & Implementation Guide

> **Author / Owner:** Kapish (Data Engineer — Exception Service Lead)
> **Service:** `exception-service` (Port 8084)
> **Feature Branch:** `feat/exception-service` (cut from `main`)
> **Key Specs:** FR7 (Exception Capture), FR8 (Classification), FR9 (Recommendation & Routing), FR11 (Operations REST APIs), FR12 (Dead-Letter)
> **Last Updated:** November 2026

---

## 1. Quick Context & Purpose

The **Exception Service** is the classification and recommendation hub of the TCEM platform. It sits between `validation-service` (upstream) and `workitem-service` (downstream) and:

1. **Captures Exceptions (FR7):** Consumes `ExceptionEvent` messages from `exception.created` published by `validation-service` (STP failures) and `ingestion-service` (schema/parse failures). Each `ExceptionEvent` contains a partially-filled `ExceptionRecord` — `category` and `subTheme` are already set by the upstream producers, but `classificationConfidence`, SLA deadlines, enriched recommendation hints, and audit persistence are not yet done.

2. **Classifies Exceptions (FR8):** Runs each `ExceptionRecord` through a deterministic rules engine that:
   - Confirms/refines the `ExceptionCategory` and `ExceptionSubTheme` already present.
   - Computes a `classificationConfidence` score (1.0 for deterministic rules; fractional for UNKNOWN/mismatched sub-themes).
   - Persists the classified exception as an `ExceptionDocument` into CouchDB (`exceptions` database).
   - Writes an audit event to CouchDB (`audit-events`) for the classification step.

3. **Generates Recommendations (FR9):** Enriches each classified exception with:
   - `recommendedAction` — human-readable action string.
   - `ownerQueue` — operational queue for routing (`DATA_REMEDIATION`, `OPERATIONS_REVIEW`, `TECHNOLOGY_SUPPORT`, `REFERENCE_DATA`, `SPECIALIST_REVIEW`, etc.)
   - `replayMayResolve` — whether the issue is expected to clear on replay after a data fix.
   - `resolutionDifficulty` — `LOW`, `MEDIUM`, or `HIGH`.
   - `operationalImpact` — `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`.
   - `slaDeadline` — computed from `operationalImpact`.

4. **Routes Classified Exceptions Downstream:**
   - **Standard classified exceptions:** Publishes enriched `ExceptionEvent` to `exception.classified` (consumed by `workitem-service`).
   - **Low-confidence or always-manual categories:** Routes to `manual.review.required` (consumed by `workitem-service` manual review queue).
   - **Irrecoverable processing failures:** Routes malformed/unprocessable payloads to `deadletter.trade` (FR12).

5. **Serves Operations REST APIs:** Exposes `GET /exceptions/{id}`, `GET /exceptions?tradeId=&status=&category=&page=&size=`, `GET /exceptions/metrics/summary`, and `GET /metrics/exception-rate` for Gunjan's React Operations UI (FR11).

---

## 2. Status: What is Done vs. What is Remaining

### Pre-existing from `common-lib`:
- [x] [`ExceptionCategory`](../services/common-lib/src/main/java/com/tcem/common/constants/ExceptionCategory.java) — 6 MVP + 2 stretch categories.
- [x] [`ExceptionSubTheme`](../services/common-lib/src/main/java/com/tcem/common/constants/ExceptionSubTheme.java) — 25 sub-themes.
- [x] [`ExceptionRecord`](../services/common-lib/src/main/java/com/tcem/common/model/exception/ExceptionRecord.java), [`ExceptionEvent`](../services/common-lib/src/main/java/com/tcem/common/event/ExceptionEvent.java), [`DeadLetterEvent`](../services/common-lib/src/main/java/com/tcem/common/event/DeadLetterEvent.java), [`MessageHeader`](../services/common-lib/src/main/java/com/tcem/common/model/message/MessageHeader.java), [`TopicNames`](../services/common-lib/src/main/java/com/tcem/common/constants/TopicNames.java), [`IdempotencyKey`](../services/common-lib/src/main/java/com/tcem/common/idempotency/IdempotencyKey.java)
- [x] Service skeleton: [`ExceptionServiceApplication.java`](../services/exception-service/src/main/java/com/tcem/exception/ExceptionServiceApplication.java), [`application.yml`](../services/exception-service/src/main/resources/application.yml), [`pom.xml`](../services/exception-service/pom.xml)

### Phase Status:
- [x] **Phase 1: CouchDB Client & Persistence Layer** ✅ **COMPLETED**
- [x] **Phase 2: Classification Rules Engine (FR8)** ✅ **COMPLETED**
- [x] **Phase 3: Recommendation Engine & Event Producers (FR9)** ✅ **COMPLETED**
- [x] **Phase 4: Kafka Consumer Pipeline & Idempotency (FR7)** ✅ **COMPLETED**
- [x] **Phase 5: Operations REST APIs & Health (FR11)** ✅ **COMPLETED**
- [x] **Phase 6: End-to-End Testing & Verification** ✅ **COMPLETED**

---

## 3. Phased Implementation Roadmap

| Phase | Title | Key Deliverables | Tests | Status |
|---|---|---|---|---|
| **Phase 1** | **CouchDB Client & Persistence Layer** | `CouchDbProperties`, `CouchDbClient`, `CouchDbOperations`, `CouchDbConfig`, `ExceptionDocument`, `AuditDocument`, `IdempotencyDocument`, `ExceptionCouchDbRepository`, `AuditCouchDbRepository`, `IdempotencyRepository`, `CouchDbHealthIndicator`, `WebConfig` | ~32 tests | ✅ **Completed** |
| **Phase 2** | **Classification Rules Engine (FR8)** | `ClassificationRule`, `ClassificationResult`, 6 category rules, `ClassificationRulesEngine`, `ExceptionIdGenerator` | 22 tests | ✅ **Completed** |
| **Phase 3** | **Recommendation Engine & Producers (FR9)** | `RecommendationEngine`, `SlaCalculator`, `ExceptionClassifiedProducer`, `ManualReviewProducer`, `DeadLetterProducer` | 31 tests | ✅ **Completed** |
| **Phase 4** | **Kafka Consumer Pipeline & Idempotency (FR7)** | `ExceptionCreatedConsumer`, `ExceptionProcessingService`, `DefaultExceptionProcessingService`, `ExceptionProcessingResult` | 14 tests | ✅ **Completed** |
| **Phase 5** | **Operations REST APIs & Health (FR11)** | `ExceptionDetailResponse`, `ExceptionListResponse`, `ExceptionMetricsSummary`, `ExceptionMetricsService`, `ExceptionQueryController` | 22 tests | ✅ **Completed** |
| **Phase 6** | **End-to-End Testing & Verification** | `ExceptionServicePipelineIntegrationTest` (9 E2E tests), `ExceptionServiceApplicationTests` (Spring context with `@EmbeddedKafka`), `ExceptionMetricsService` wired into processing pipeline | 10 tests | ✅ **Completed** |

---

## 4. Phase-by-Phase Implementation Details

### Phase 1: CouchDB Client & Persistence Layer ✅ COMPLETED

| File | Location |
|---|---|
| [`CouchDbProperties`](../services/exception-service/src/main/java/com/tcem/exception/config/CouchDbProperties.java) | `config/CouchDbProperties.java` |
| [`CouchDbOperations`](../services/exception-service/src/main/java/com/tcem/exception/couchdb/CouchDbOperations.java) | `couchdb/CouchDbOperations.java` |
| [`CouchDbClient`](../services/exception-service/src/main/java/com/tcem/exception/couchdb/CouchDbClient.java) | `couchdb/CouchDbClient.java` |
| [`CouchDbResponse`](../services/exception-service/src/main/java/com/tcem/exception/couchdb/CouchDbResponse.java) | `couchdb/CouchDbResponse.java` |
| [`CouchDbException`](../services/exception-service/src/main/java/com/tcem/exception/couchdb/CouchDbException.java) | `couchdb/CouchDbException.java` |
| [`FindQueryRequest`](../services/exception-service/src/main/java/com/tcem/exception/couchdb/FindQueryRequest.java) | `couchdb/FindQueryRequest.java` |
| [`CouchDbConfig`](../services/exception-service/src/main/java/com/tcem/exception/config/CouchDbConfig.java) | `config/CouchDbConfig.java` |
| [`WebConfig`](../services/exception-service/src/main/java/com/tcem/exception/config/WebConfig.java) | `config/WebConfig.java` |
| [`ExceptionDocument`](../services/exception-service/src/main/java/com/tcem/exception/model/document/ExceptionDocument.java) | `model/document/ExceptionDocument.java` |
| [`AuditDocument`](../services/exception-service/src/main/java/com/tcem/exception/model/document/AuditDocument.java) | `model/document/AuditDocument.java` |
| [`IdempotencyDocument`](../services/exception-service/src/main/java/com/tcem/exception/model/document/IdempotencyDocument.java) | `model/document/IdempotencyDocument.java` |
| [`ExceptionCouchDbRepository`](../services/exception-service/src/main/java/com/tcem/exception/repository/ExceptionCouchDbRepository.java) | `repository/ExceptionCouchDbRepository.java` |
| [`AuditCouchDbRepository`](../services/exception-service/src/main/java/com/tcem/exception/repository/AuditCouchDbRepository.java) | `repository/AuditCouchDbRepository.java` |
| [`IdempotencyRepository`](../services/exception-service/src/main/java/com/tcem/exception/repository/IdempotencyRepository.java) | `repository/IdempotencyRepository.java` |
| [`CouchDbHealthIndicator`](../services/exception-service/src/main/java/com/tcem/exception/health/CouchDbHealthIndicator.java) | `health/CouchDbHealthIndicator.java` |
| [`InMemoryCouchDbOperations`](../services/exception-service/src/test/java/com/tcem/exception/couchdb/InMemoryCouchDbOperations.java) | `test/.../couchdb/InMemoryCouchDbOperations.java` |

**Tests:**
- [`InMemoryCouchDbOperationsTest`](../services/exception-service/src/test/java/com/tcem/exception/couchdb/InMemoryCouchDbOperationsTest.java) — 10 tests
- [`ExceptionCouchDbRepositoryTest`](../services/exception-service/src/test/java/com/tcem/exception/repository/ExceptionCouchDbRepositoryTest.java) — 8 tests
- [`AuditCouchDbRepositoryTest`](../services/exception-service/src/test/java/com/tcem/exception/repository/AuditCouchDbRepositoryTest.java) — 7 tests
- [`IdempotencyRepositoryTest`](../services/exception-service/src/test/java/com/tcem/exception/repository/IdempotencyRepositoryTest.java) — 6 tests
- [`ExceptionServiceApplicationTests`](../services/exception-service/src/test/java/com/tcem/exception/ExceptionServiceApplicationTests.java) — 1 context-load test (updated in Phase 6 to use `@EmbeddedKafka`)

**CouchDB Exception Count View (C3-07):**

- Added `countByView(databaseName, designDocument, viewName)` to `CouchDbOperations`.
- `CouchDbClient` queries the CouchDB view endpoint and reads the reduced count from `rows[0].value`. An empty result returns `0`.
- `ExceptionCouchDbRepository.countAll()` uses the `exception_counts/total` view to retrieve the persisted exception count.
- The `exception_counts/total` design view counts documents where `docType === "EXCEPTION"` and uses CouchDB's `_count` reducer.
- The view is provisioned by `couchdb-init` through `docker-compose.yml` and `scripts/init-couchdb.sh`.
- Added `CouchDbClientTest` and updated repository and in-memory test-double coverage.
- Verification: CouchDB 3.3 started healthy, `docker compose config --quiet` passed, and the initialization script successfully created the view.
---

### Phase 2: Classification Rules Engine (FR8) ✅ COMPLETED

| File | Location | Purpose |
|---|---|---|
| [`ClassificationRule`](../services/exception-service/src/main/java/com/tcem/exception/classifier/ClassificationRule.java) | `classifier/ClassificationRule.java` | Interface: `appliesTo()` + `evaluate(record, result)`. |
| [`ClassificationResult`](../services/exception-service/src/main/java/com/tcem/exception/classifier/ClassificationResult.java) | `classifier/ClassificationResult.java` | Mutable accumulator; `requiresManualReview()` checks confidence < 0.6. |
| [`MissingEconomicDataRule`](../services/exception-service/src/main/java/com/tcem/exception/classifier/rules/MissingEconomicDataRule.java) | `classifier/rules/` | Handles `MISSING_ECONOMIC_DATA` (7 sub-themes). |
| [`MissingReferenceDataRule`](../services/exception-service/src/main/java/com/tcem/exception/classifier/rules/MissingReferenceDataRule.java) | `classifier/rules/` | Handles `MISSING_STATIC_REFERENCE_DATA` (4 sub-themes). |
| [`TemplateIssueRule`](../services/exception-service/src/main/java/com/tcem/exception/classifier/rules/TemplateIssueRule.java) | `classifier/rules/` | Handles `TEMPLATE_ISSUE` (3 sub-themes). |
| [`BusinessRuleFailureRule`](../services/exception-service/src/main/java/com/tcem/exception/classifier/rules/BusinessRuleFailureRule.java) | `classifier/rules/` | Handles `BUSINESS_RULE_FAILURE` (4 sub-themes). |
| [`TechnicalFailureRule`](../services/exception-service/src/main/java/com/tcem/exception/classifier/rules/TechnicalFailureRule.java) | `classifier/rules/` | Handles `TECHNICAL_PROCESSING_FAILURE` (5 sub-themes). |
| [`CounterpartyDisputeRule`](../services/exception-service/src/main/java/com/tcem/exception/classifier/rules/CounterpartyDisputeRule.java) | `classifier/rules/` | Handles `COUNTERPARTY_MATCHING_DISPUTE` (3 sub-themes). |
| [`ClassificationRulesEngine`](../services/exception-service/src/main/java/com/tcem/exception/classifier/ClassificationRulesEngine.java) | `classifier/ClassificationRulesEngine.java` | Spring `@Service`; dispatches by category; defensive fallbacks. |
| [`ExceptionIdGenerator`](../services/exception-service/src/main/java/com/tcem/exception/service/ExceptionIdGenerator.java) | `service/ExceptionIdGenerator.java` | Thread-safe `EXC-XXXXX` sequence starting at 30001. |

**Confidence Scoring Model:**
- `1.0` — category + subTheme + affectedFields are fully consistent.
- `0.8` — category correct but subTheme is UNKNOWN or affectedFields blank.
- `0.5` — subTheme from wrong family, or no rule registered for category.
- `0.3` — null record or null category.

**Manual Review Threshold:** `classificationConfidence < 0.6` → `requiresManualReview()` = `true`.

**Tests:**
- [`ClassificationRulesEngineTest`](../services/exception-service/src/test/java/com/tcem/exception/classifier/ClassificationRulesEngineTest.java) — 18 tests
- [`ExceptionIdGeneratorTest`](../services/exception-service/src/test/java/com/tcem/exception/service/ExceptionIdGeneratorTest.java) — 4 tests

---

### Phase 3: Recommendation Engine & Event Producers (FR9) ✅ COMPLETED

| File | Location | Purpose |
|---|---|---|
| [`RecommendationEngine`](../services/exception-service/src/main/java/com/tcem/exception/recommendation/RecommendationEngine.java) | `recommendation/RecommendationEngine.java` | Maps each `ExceptionCategory` to `recommendedAction`, `ownerQueue`, `replayMayResolve`, `resolutionDifficulty`, `operationalImpact`. Mutates `ExceptionRecord` in-place. |
| [`SlaCalculator`](../services/exception-service/src/main/java/com/tcem/exception/recommendation/SlaCalculator.java) | `recommendation/SlaCalculator.java` | CRITICAL→+4h, HIGH→+24h, MEDIUM→+72h, LOW→+7d. Null-safe; defaults to 72h. |
| [`ExceptionClassifiedProducer`](../services/exception-service/src/main/java/com/tcem/exception/producer/ExceptionClassifiedProducer.java) | `producer/ExceptionClassifiedProducer.java` | Publishes `ExceptionEvent` to `exception.classified`; fallback key `"UNKNOWN_EXCEPTION"`. |
| [`ManualReviewProducer`](../services/exception-service/src/main/java/com/tcem/exception/producer/ManualReviewProducer.java) | `producer/ManualReviewProducer.java` | Publishes `ExceptionEvent` to `manual.review.required`; same fallback. |
| [`DeadLetterProducer`](../services/exception-service/src/main/java/com/tcem/exception/producer/DeadLetterProducer.java) | `producer/DeadLetterProducer.java` | Publishes `DeadLetterEvent` to `deadletter.trade`; fallback key `"DLQ_" + millis()`. |

**Recommendation Mapping Table:**

| Category | Queue | Replay? | Difficulty | Impact |
|---|---|---|---|---|
| `MISSING_ECONOMIC_DATA` | `DATA_REMEDIATION` | true | LOW | HIGH |
| `MISSING_STATIC_REFERENCE_DATA` | `REFERENCE_DATA` | true | MEDIUM | MEDIUM |
| `TEMPLATE_ISSUE` | `TEMPLATE_MAINTENANCE` | false | MEDIUM | MEDIUM |
| `BUSINESS_RULE_FAILURE` | `OPERATIONS_REVIEW` | false | HIGH | HIGH |
| `TECHNICAL_PROCESSING_FAILURE` | `TECHNOLOGY_SUPPORT` | true | LOW | LOW |
| `COUNTERPARTY_MATCHING_DISPUTE` | `OPERATIONS_REVIEW` | false | HIGH | CRITICAL |
| `LEGAL_DOCUMENTATION_GAP` | `SPECIALIST_REVIEW` | false | HIGH | HIGH |
| `FALSE_POSITIVE_CONFIRMABLE_EVENT` | `RULES_OWNER` | false | LOW | LOW |
| null / unknown | `OPERATIONS_REVIEW` | false | MEDIUM | MEDIUM |

**Tests:**
- [`RecommendationEngineTest`](../services/exception-service/src/test/java/com/tcem/exception/recommendation/RecommendationEngineTest.java) — 11 tests
- [`SlaCalculatorTest`](../services/exception-service/src/test/java/com/tcem/exception/recommendation/SlaCalculatorTest.java) — 8 tests
- [`ExceptionClassifiedProducerTest`](../services/exception-service/src/test/java/com/tcem/exception/producer/ExceptionClassifiedProducerTest.java) — 4 tests
- [`ManualReviewProducerTest`](../services/exception-service/src/test/java/com/tcem/exception/producer/ManualReviewProducerTest.java) — 4 tests
- [`DeadLetterProducerTest`](../services/exception-service/src/test/java/com/tcem/exception/producer/DeadLetterProducerTest.java) — 4 tests

---

### Phase 4: Kafka Consumer Pipeline & Idempotency (FR7) ✅ COMPLETED

**All deliverables implemented:**

| File | Location | Purpose |
|---|---|---|
| [`ExceptionProcessingResult`](../services/exception-service/src/main/java/com/tcem/exception/model/ExceptionProcessingResult.java) | `model/ExceptionProcessingResult.java` | Enum: `CLASSIFIED`, `MANUAL_REVIEW`, `DUPLICATE`, `DEAD_LETTER`. |
| [`ExceptionProcessingService`](../services/exception-service/src/main/java/com/tcem/exception/service/ExceptionProcessingService.java) | `service/ExceptionProcessingService.java` | Interface: `process(partitionKey, rawPayload)` → `ExceptionProcessingResult`. |
| `DefaultExceptionProcessingService` | `service/DefaultExceptionProcessingService.java` | Full 8-step pipeline + `ExceptionMetricsService` wired (updated in Phase 6). |
| [`ExceptionCreatedConsumer`](../services/exception-service/src/main/java/com/tcem/exception/consumer/ExceptionCreatedConsumer.java) | `consumer/ExceptionCreatedConsumer.java` | `@KafkaListener` on `exception.created`; delegates to `ExceptionProcessingService`. |

**Processing Pipeline (8 steps):**

```
Step 1  Guard — null/blank payload               → DEAD_LETTER (DLQ event sent) + metrics.record(DEAD_LETTER)
Step 2  Deserialize JSON → ExceptionEvent         → DEAD_LETTER on JsonProcessingException
Step 3  Resolve tradeId, messageId, schemaVersion, eventType; build IdempotencyKey
Step 4  Idempotency check                         → DUPLICATE (skip if already processed)
        BYPASS: if eventType == REPLAY, skip Step 4
Step 5  Assign exceptionId if absent (ExceptionIdGenerator); run ClassificationRulesEngine
Step 6  RecommendationEngine.enrich() + SlaCalculator.computeDeadline()
Step 7  Persist: ExceptionDocument + AuditDocument + IdempotencyRecord to CouchDB
Step 8  Route: confidence ≥ 0.6 → exception.classified (CLASSIFIED)
               confidence < 0.6 → manual.review.required (MANUAL_REVIEW)
        → metricsService.record(finalResult) called before return
```

**Key design decisions:**
- All business failures are caught inside `DefaultExceptionProcessingService` — they never propagate to the consumer, preventing Kafka infinite re-delivery loops.
- `ExceptionCreatedConsumer` also wraps `processingService.process()` in a try-catch as a last-resort safety net.
- CouchDB save failures (exception, audit, idempotency) are non-fatal and logged as warnings — processing continues to the routing step.
- SLA deadlines: CRITICAL=+4h, HIGH=+24h, MEDIUM=+72h, LOW=+7d from `capturedAt`.
- `ExceptionMetricsService` is injected into `DefaultExceptionProcessingService` and called at the start (`recordReceived()`) and end (`record(result)`) of every pipeline invocation.

**Tests:**
- [`DefaultExceptionProcessingServiceTest`](../services/exception-service/src/test/java/com/tcem/exception/service/DefaultExceptionProcessingServiceTest.java) — 9 tests (updated in Phase 6 to pass `ExceptionMetricsService` to constructor)
- [`ExceptionCreatedConsumerTest`](../services/exception-service/src/test/java/com/tcem/exception/consumer/ExceptionCreatedConsumerTest.java) — 5 tests

**Verification command:**
```bash
mvn -f services/pom.xml -pl common-lib,exception-service -am test
```

---

### Phase 5: Operations REST APIs & Health (FR11) ✅ COMPLETED

**All deliverables implemented:**

| File | Location | Purpose |
|---|---|---|
| [`ExceptionDetailResponse`](../services/exception-service/src/main/java/com/tcem/exception/model/ExceptionDetailResponse.java) | `model/ExceptionDetailResponse.java` | REST DTO: wraps `ExceptionDocument` + audit trail for `GET /exceptions/{id}`. |
| [`ExceptionListResponse`](../services/exception-service/src/main/java/com/tcem/exception/model/ExceptionListResponse.java) | `model/ExceptionListResponse.java` | Paginated list DTO: `exceptions`, `page`, `size`, `count`, filter fields. |
| [`ExceptionMetricsSummary`](../services/exception-service/src/main/java/com/tcem/exception/model/ExceptionMetricsSummary.java) | `model/ExceptionMetricsSummary.java` | Metrics snapshot DTO: `totalReceived`, `totalClassified`, `totalManualReview`, `totalDuplicates`, `totalDeadLetter`, `classificationRatePercent`, `timestamp`. |
| [`ExceptionMetricsService`](../services/exception-service/src/main/java/com/tcem/exception/service/ExceptionMetricsService.java) | `service/ExceptionMetricsService.java` | In-memory atomic counters; `recordReceived()`, `record(ExceptionProcessingResult)`, `getSummary()`, `getExceptionRate()`. |
| [`ExceptionQueryController`](../services/exception-service/src/main/java/com/tcem/exception/controller/ExceptionQueryController.java) | `controller/ExceptionQueryController.java` | 5 REST endpoints — see table below. |

**REST Endpoints:**

| Endpoint | Method | Description | Response |
|---|---|---|---|
| `/exceptions/{id}` | GET | Single exception + audit trail. 404 if not found. | `ExceptionDetailResponse` |
| `/exceptions?tradeId=&status=&category=&page=&size=` | GET | Paginated list with optional filters. `tradeId` overrides other filters. Page capped at 200. | `ExceptionListResponse` |
| `/exceptions/{id}/audit` | GET | Full audit trail for a specific `exceptionId`. Returns empty list if no records. | `List<AuditDocument>` |
| `/exceptions/metrics/summary` | GET | Aggregate in-memory counters snapshot for dashboard widgets. | `ExceptionMetricsSummary` |
| `/metrics/exception-rate` | GET | Classification rate formatted for the React dashboard (also accessible at `/exceptions/metrics/exception-rate`). | `Map<String, Object>` |

**Classification Rate Formula:**
```
classificationRatePercent = (classified + manualReview) / (received - duplicates) × 100
```
Duplicates are excluded from the denominator since suppression is correct behaviour.

**Important distinction — Persistent Count vs. Processing Metrics:**

- `ExceptionCouchDbRepository.countAll()` retrieves the persisted exception-document count from CouchDB using the `exception_counts/total` view.
- `ExceptionMetricsService` maintains in-memory atomic counters for processing activity, including received, classified, manual-review, duplicate, and dead-letter outcomes.
- `GET /exceptions/metrics/summary` and `GET /metrics/exception-rate` use `ExceptionMetricsService`; they do not retrieve their values from the CouchDB count view.
- These values serve different purposes and should not be treated as interchangeable.

**Tests:**
- [`ExceptionQueryControllerTest`](../services/exception-service/src/test/java/com/tcem/exception/controller/ExceptionQueryControllerTest.java) — 12 MockMvc tests
- [`ExceptionMetricsServiceTest`](../services/exception-service/src/test/java/com/tcem/exception/service/ExceptionMetricsServiceTest.java) — 10 tests

**Verification command:**
```bash
mvn -f services/pom.xml -pl common-lib,exception-service -am test
```

---

### Phase 6: End-to-End Testing & Verification ✅ COMPLETED

**All deliverables implemented:**

| File | Location | Purpose |
|---|---|---|
| [`ExceptionServicePipelineIntegrationTest`](../services/exception-service/src/test/java/com/tcem/exception/integration/ExceptionServicePipelineIntegrationTest.java) | `test/.../integration/` | 9 E2E pipeline integration tests using `InMemoryCouchDbOperations` + capturing mock `KafkaOperations`. No external infrastructure required. |
| [`ExceptionServiceApplicationTests`](../services/exception-service/src/test/java/com/tcem/exception/ExceptionServiceApplicationTests.java) | `test/.../ExceptionServiceApplicationTests.java` | Spring context smoke test updated to use `@EmbeddedKafka` (embedded Kafka broker). |
| [`DefaultExceptionProcessingService`](../services/exception-service/src/main/java/com/tcem/exception/service/DefaultExceptionProcessingService.java) | `service/DefaultExceptionProcessingService.java` | Updated to accept and wire `ExceptionMetricsService` — `recordReceived()` at start, `record(result)` before every return. |
| [`DefaultExceptionProcessingServiceTest`](../services/exception-service/src/test/java/com/tcem/exception/service/DefaultExceptionProcessingServiceTest.java) | `test/.../service/` | Updated to pass `ExceptionMetricsService` to `DefaultExceptionProcessingService` constructor. |

**E2E Test Scenarios (9 tests in `ExceptionServicePipelineIntegrationTest`):**

| Test | Scenario | Verified Assertions |
|---|---|---|
| 1 | Happy path — `MISSING_ECONOMIC_DATA` | 1 message on `exception.classified`; confidence=1.0; `ownerQueue=DATA_REMEDIATION`; ExceptionDocument saved in CouchDB; audit trail present; metrics correct |
| 2 | Manual review — `COUNTERPARTY_MATCHING_DISPUTE` | 1 message on `manual.review.required`; confidence < 0.6; metrics: `totalManualReview=1` |
| 3 | Duplicate suppression | First: classified. Second (same payload): suppressed. `capturedRecords.size()=1`; metrics: `totalDuplicates=1` |
| 4 | REPLAY bypass | First: classified. Second: REPLAY eventType → also classified. `capturedRecords.size()=2`; metrics: `totalDuplicates=0` |
| 5 | Malformed JSON | 1 message on `deadletter.trade`; `failedOnTopic=exception.created`; `failedInService=exception-service`; original payload preserved |
| 6 | Null payload | 1 message on `deadletter.trade`; metrics: `totalDeadLetter=1` |
| 7 | ExceptionId preservation | Inbound `exceptionId=EXC-PRESERVED-999` carried through to outbound `exception.classified` payload |
| 8 | REST API — GET /exceptions/{id} | 200 response with persisted `ExceptionDocument` after pipeline processing |
| 9 | REST API — GET /exceptions/metrics/summary | Correct `totalReceived=3`, `totalClassified=1`, `totalManualReview=1`, `totalDeadLetter=1`, `classificationRatePercent=66.67` |

**Architecture of integration test (follows ingestion-service pattern):**
```
InMemoryCouchDbOperations  ← replaces real CouchDB (in-memory, no Docker)
Capturing mock KafkaOperations ← intercepts all .send() calls into List<CapturedKafkaRecord>
Real processing components wired together:
  DefaultExceptionProcessingService
  ← ClassificationRulesEngine, RecommendationEngine, SlaCalculator, ExceptionIdGenerator
  ← ExceptionCouchDbRepository, AuditCouchDbRepository, IdempotencyRepository
  ← ExceptionClassifiedProducer, ManualReviewProducer, DeadLetterProducer
  ← ExceptionMetricsService
ExceptionCreatedConsumer.onMessage() ← test entry point (mirrors real Kafka consumer)
ExceptionQueryController ← REST API tested directly via method invocation
```

**Final verification command:**
```bash
mvn -f services/pom.xml -pl common-lib,exception-service -am test
```

**Full reactor build:**
```bash
mvn -f services/pom.xml verify
```

---

## 5. Context Recovery Guide

If session context is lost, read these files in order to resume:

### A. Architecture & Workflow
1. [`README.md`](../README.md) — System summary, port mappings.
2. [`docs/kafka-topics.md`](../docs/kafka-topics.md) — Exception-service consumes `exception.created`, produces `exception.classified`, `manual.review.required`, `deadletter.trade`.
3. [`docs/project-status.md`](../docs/project-status.md) — Per-teammate status.
4. [`docs/ingestion-service-progress.md`](../docs/ingestion-service-progress.md) — Reference for CouchDB and pipeline patterns.

### B. Shared Domain Contracts (`common-lib`)
1. [`ExceptionRecord.java`](../services/common-lib/src/main/java/com/tcem/common/model/exception/ExceptionRecord.java)
2. [`ExceptionEvent.java`](../services/common-lib/src/main/java/com/tcem/common/event/ExceptionEvent.java)
3. [`ExceptionCategory.java`](../services/common-lib/src/main/java/com/tcem/common/constants/ExceptionCategory.java) / [`ExceptionSubTheme.java`](../services/common-lib/src/main/java/com/tcem/common/constants/ExceptionSubTheme.java)
4. [`TopicNames.java`](../services/common-lib/src/main/java/com/tcem/common/constants/TopicNames.java)
5. [`IdempotencyKey.java`](../services/common-lib/src/main/java/com/tcem/common/idempotency/IdempotencyKey.java)
6. [`MessageHeader.java`](../services/common-lib/src/main/java/com/tcem/common/model/message/MessageHeader.java) / [`EventType.java`](../services/common-lib/src/main/java/com/tcem/common/constants/EventType.java)
7. [`DeadLetterEvent.java`](../services/common-lib/src/main/java/com/tcem/common/event/DeadLetterEvent.java)

### C. Implemented Exception Service Files (Phases 1–6)

1. [`application.yml`](../services/exception-service/src/main/resources/application.yml)

2. [`CouchDbOperations.java`](../services/exception-service/src/main/java/com/tcem/exception/couchdb/CouchDbOperations.java) / [`CouchDbClient.java`](../services/exception-service/src/main/java/com/tcem/exception/couchdb/CouchDbClient.java) — CouchDB operations interface and HTTP client, including the count-view query.

3. [`ExceptionDocument.java`](../services/exception-service/src/main/java/com/tcem/exception/model/document/ExceptionDocument.java) / [`AuditDocument.java`](../services/exception-service/src/main/java/com/tcem/exception/model/document/AuditDocument.java)

4. [`ExceptionCouchDbRepository.java`](../services/exception-service/src/main/java/com/tcem/exception/repository/ExceptionCouchDbRepository.java)

5. [`ClassificationRulesEngine.java`](../services/exception-service/src/main/java/com/tcem/exception/classifier/ClassificationRulesEngine.java) / [`ClassificationResult.java`](../services/exception-service/src/main/java/com/tcem/exception/classifier/ClassificationResult.java)

6. [`RecommendationEngine.java`](../services/exception-service/src/main/java/com/tcem/exception/recommendation/RecommendationEngine.java) / [`SlaCalculator.java`](../services/exception-service/src/main/java/com/tcem/exception/recommendation/SlaCalculator.java)

7. [`ExceptionClassifiedProducer.java`](../services/exception-service/src/main/java/com/tcem/exception/producer/ExceptionClassifiedProducer.java) / [`ManualReviewProducer.java`](../services/exception-service/src/main/java/com/tcem/exception/producer/ManualReviewProducer.java) / [`DeadLetterProducer.java`](../services/exception-service/src/main/java/com/tcem/exception/producer/DeadLetterProducer.java)

8. [`DefaultExceptionProcessingService.java`](../services/exception-service/src/main/java/com/tcem/exception/service/DefaultExceptionProcessingService.java) — Full 8-step pipeline (includes `ExceptionMetricsService`).

9. [`ExceptionCreatedConsumer.java`](../services/exception-service/src/main/java/com/tcem/exception/consumer/ExceptionCreatedConsumer.java) — `@KafkaListener` entry point.

10. [`InMemoryCouchDbOperations.java`](../services/exception-service/src/test/java/com/tcem/exception/couchdb/InMemoryCouchDbOperations.java) — Test double.

11. [`CouchDbClientTest.java`](../services/exception-service/src/test/java/com/tcem/exception/couchdb/CouchDbClientTest.java) — Tests CouchDB HTTP client operations, including the count-view query.

12. [`ExceptionDetailResponse.java`](../services/exception-service/src/main/java/com/tcem/exception/model/ExceptionDetailResponse.java) / [`ExceptionListResponse.java`](../services/exception-service/src/main/java/com/tcem/exception/model/ExceptionListResponse.java) / [`ExceptionMetricsSummary.java`](../services/exception-service/src/main/java/com/tcem/exception/model/ExceptionMetricsSummary.java) — REST DTOs.

13. [`ExceptionMetricsService.java`](../services/exception-service/src/main/java/com/tcem/exception/service/ExceptionMetricsService.java) — In-memory metrics counters; wired into `DefaultExceptionProcessingService`.

14. [`ExceptionQueryController.java`](../services/exception-service/src/main/java/com/tcem/exception/controller/ExceptionQueryController.java) — 5 REST endpoints.

15. [`ExceptionServicePipelineIntegrationTest.java`](../services/exception-service/src/test/java/com/tcem/exception/integration/ExceptionServicePipelineIntegrationTest.java) — 9 end-to-end pipeline tests.

16. [`ExceptionServiceApplicationTests.java`](../services/exception-service/src/test/java/com/tcem/exception/ExceptionServiceApplicationTests.java) — Spring context smoke test with `@EmbeddedKafka`.
---

## 6. Teammate Communication Contracts

### A. Upstream: Mahadev (`validation-service`) & Mayank (`ingestion-service`)
- **Topic Consumed:** `exception.created`
- **Kafka message key:** `tradeId`
- **Payload:** `ExceptionEvent` wrapping `ExceptionRecord`
- **Guarantees from upstream:** `category`, `subTheme`, `affectedFields`, `sourceMessageId`, `correlationId` populated.

### B. Downstream: Oren (`workitem-service`)
- **Topics Produced:** `exception.classified` (primary), `manual.review.required` (low-confidence / dispute)
- **Kafka message key:** `tradeId`
- **Guarantees exception-service provides:** All `ExceptionRecord` fields fully populated; `classificationConfidence` set (< 0.6 triggers manual review); `ownerQueue`, `slaDeadline`, `replayMayResolve` set; `messageHeader.sourceService="exception-service"`.

### C. Downstream: Gunjan (Frontend / React Operations UI)
- **REST Endpoints (Port 8084 / Nginx `/api/exception/*`):**
  - `GET /exceptions/{id}`, `GET /exceptions?...`, `GET /exceptions/metrics/summary`, `GET /metrics/exception-rate`

---

## 7. Progress Summary

| Phase | Title | Status | Tests |
|---|---|---|---|
| **Phase 1** | **CouchDB Client & Persistence Layer** | ✅ Completed | ~32 tests |
| **Phase 2** | **Classification Rules Engine (FR8)** | ✅ Completed | 22 tests |
| **Phase 3** | **Recommendation Engine & Producers (FR9)** | ✅ Completed | 31 tests |
| **Phase 4** | **Kafka Consumer Pipeline & Idempotency (FR7)** | ✅ Completed | 14 tests |
| **Phase 5** | **Operations REST APIs & Health (FR11)** | ✅ Completed | 22 tests |
| **Phase 6** | **End-to-End Testing & Verification** | ✅ Completed | 10 tests |

**Historical Test Summary:** 129 tests were recorded in the original verification on 2026-10-25.

**Latest Verification (November 2026):**

- Exception-service targeted test run: **134 tests, 0 failures, 0 errors — BUILD SUCCESS**.
- Full Maven reactor test run: **84 tests across 7 modules, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**.

**Status legend:** `⬜ Not Started` → `🟡 In Progress` → `✅ Completed`

---

## 8. Known Constraints & Decisions

1. **No re-derivation of category from scratch.** Upstream producers set `category` + `subTheme`. The engine confirms/enriches them.

2. **Idempotency key format:** `idempotency:{tradeId}:{messageId}:{schemaVersion}:{eventType}` per `IdempotencyKey.toStorageKey()`.

3. **REPLAY bypass:** When `messageHeader.eventType == REPLAY`, skip idempotency check (Step 4).

4. **Manual review threshold:** `classificationConfidence < 0.6` → `manual.review.required`. Matches `workitem-service` threshold.

5. **`COUNTERPARTY_MATCHING_DISPUTE` always routes to manual review (FR8):** `CounterpartyDisputeRule` intentionally caps confidence at ≤ 0.5 even when the sub-theme is fully identified — counterparty disputes require a human to adjudicate, regardless of classification certainty.

6. **SLA deadlines:** CRITICAL=+4h, HIGH=+24h, MEDIUM=+72h, LOW=+7d from `capturedAt`.

7. **ExceptionId preservation:** If inbound `ExceptionRecord.exceptionId` is non-null and non-blank, preserve it; otherwise generate via `ExceptionIdGenerator` (starts at EXC-30001).

8. **Fire-and-forget producers:** Kafka send failures logged as warnings but not propagated to caller.

9. **Replay identity is preserved:** Outbound `exception.classified` and `manual.review.required` headers keep the inbound `eventType` and `replayCount` (audit C3-02, commit `8b4435d`), because `workitem-service` uses them to detect a replay's outcome.

10. **CouchDB saves are non-fatal:** All save calls (`safePersistException`, `safeSaveAudit`, `safeRecordIdempotency`) catch exceptions internally — processing always reaches the routing step.

11. **CouchDB `exceptions` DB pre-provisioned** by `couchdb-init`. `ensureDatabaseExists` is idempotent.

12. **`ExceptionMetricsService` wired in Phase 6:** `DefaultExceptionProcessingService` calls `metricsService.recordReceived()` at the start of every `process()` call, and `metricsService.record(result)` before every return path — ensuring 100% metrics coverage regardless of pipeline outcome.

13. **Maven installation:** Apache Maven 3.9.9 ZIP downloaded to `%USERPROFILE%\Downloads\apache-maven-3.9.9-bin.zip`. Extract to a permanent location (e.g. `C:\tools\apache-maven-3.9.9`) and add `C:\tools\apache-maven-3.9.9\bin` to the system `PATH`. JDK 26 is at `C:\Program Files\Java\jdk-26.0.1`.

---

*Last updated: November 2026 — All 6 phases completed. Exception service implementation is complete. Latest targeted test run: 134 tests, 0 failures, 0 errors.*
