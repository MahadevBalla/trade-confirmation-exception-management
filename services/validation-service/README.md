# Validation Service (`validation-service`)

> **STP Rules Engine, Legal Confirmation Generation & SHA-256 Fingerprinting for the Trade Confirmation Exception Management Platform**  
> **Service Port:** `8083` · **Author / Lead:** Mahadev Balla · **Runtime:** Java 21 / Spring Boot 3.3 · **Storage:** Apache CouchDB · **Streaming:** Apache Kafka

---

## 1. Overview & Purpose

The **`validation-service`** acts as the core decision-making and confirmation issuance authority of the TCEM platform:

1. **Straight-Through Processing (STP) Rules Engine (FR5):** Consumes `trade.enriched` and evaluates non-short-circuiting business validation rules across:
   - **Economic Terms (`EconomicFieldsRule`):** Strike, notional amount, currencies, option type, buy/sell side, and date sanity (`tradeDate <= expiryDate <= settlementDate`).
   - **Business Eligibility (`BusinessEligibilityRule`):** Validates counterparty active status and product STP eligibility flags.
   - **Template Resolution (`TemplateResolutionRule`):** Verifies the availability of an applicable legal confirmation template.
2. **STP Audit Publication:** Publishes an evaluation outcome to `stp.validated` with `STP_PASSED` or `STP_FAILED` diagnostics (also handles `CANCEL_TRADE` with `cancelled=true`).
3. **Legal Confirmation Issuance (FR6):**
   - Renders deterministic, monospace plain-text ISDA confirmation notices based on the resolved template.
   - Computes a deterministic SHA-256 fingerprint over confirmable contract terms via `ConfirmationFingerprint`.
   - On amendments (`AMEND_TRADE`), checks the previous confirmation fingerprint: if confirmable fields are unchanged, duplicate confirmation issuance is suppressed ("nothing changed = don't resend").
   - Persists confirmation documents to CouchDB `confirmations` database with status `ALLEGED`.
   - Publishes `ConfirmationEvent` to `confirmation.generated`.
4. **Exception Routing (FR7):** Maps STP failures to structured `ExceptionRecord` entries and dispatches them to `exception.created`.
5. **Held Trade & 4-Eyes Manual Review Flow:**
   - When a trade fails STP, its enriched payload is retained in CouchDB as a held trade (`docType=HELD_TRADE`).
   - When an operator approves a manual review in `workitem-service`, a `workitem.linked` event arrives with `reviewDecision=APPROVED`.
   - `ManualReviewApprovalConsumer` retrieves the held trade and automatically issues the legal confirmation!

---

## 2. Event Flow & Contracts

```
[Kafka: trade.enriched]
          │
          ▼
╔═════════════════════════════════════════════════════════════════╗
║                      validation-service                         ║
║  ┌─────────────────────────┐     ┌───────────────────────────┐  ║
║  │ Validation Idempotency  │     │   STP Rules Engine (FR5)  │  ║
║  │ (validation-idempotency)│ ──► │ - EconomicFieldsRule      │  ║
║  └─────────────────────────┘     │ - BusinessEligibilityRule │  ║
║                                  │ - TemplateResolutionRule  │  ║
║                                  └───────────────────────────┘  ║
║                                                │                ║
║                      ┌─────────────────────────┴────────────┐   ║
║                      ▼                                      ▼   ║
║           [STP Passed: FR6]                         [STP Failed]║
║        Confirmation Generator                    Exception Map  ║
║        SHA-256 Fingerprinting                    CouchDB Held   ║
╚═════════════════════════════════════════════════════════════════╝
          │                      │                    │
     (STP Passed)            (Decision)          (Exceptions)
          │                      │                    │
          ▼                      ▼                    ▼
[confirmation.generated]  [stp.validated]    [exception.created]
          │                                           │
          ▼                                           ▼
   Workitem Service                            Exception Service
```

---

## 3. Operations REST APIs (Port 8083)

| Endpoint | Method | Description |
| --- | --- | --- |
| `/confirmations/{id}` | GET | Returns a single confirmation document by `confirmationId` (404 if not found). |
| `/trades/{tradeId}/confirmations` | GET | Returns all confirmation versions for a trade, ordered newest-first. |
| `/trades/{tradeId}/confirmation` | GET | Returns the latest active confirmation document for a trade. |
| `/metrics/confirmation-count` | GET | Returns total count of persisted confirmations for operations dashboards. |
| `/actuator/health` | GET | Spring Boot Actuator health check (includes CouchDB connectivity). |

*Note: Through the Nginx API gateway, these endpoints are mapped under `/api/validation/*`.*

---

## 4. Configuration Properties

| Property | Default Value | Environment Variable |
| --- | --- | --- |
| Server Port | `8083` | `SERVER_PORT` |
| Kafka Bootstrap | `localhost:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| Consumer Group | `validation-service-group` | `SPRING_KAFKA_CONSUMER_GROUP_ID` |
| CouchDB URL | `http://localhost:5984` | `COUCHDB_URL` |
| Confirmations DB | `confirmations` | `COUCHDB_CONFIRMATIONS_DB` |
| Idempotency DB | `validation-idempotency` | `COUCHDB_VALIDATION_IDEMPOTENCY_DB` |

---

## 5. Automated Testing

```bash
mvn -f services/pom.xml -pl common-lib,validation-service -am test
```
* **Coverage:** 145 passing unit and embedded-Kafka integration tests covering STP rule evaluation, fingerprint deduplication, plain-text rendering, held-trade manual reviews, and CouchDB persistence.
