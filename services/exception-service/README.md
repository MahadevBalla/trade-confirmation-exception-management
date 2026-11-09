# Exception Service (`exception-service`)

> **Exception Taxonomy, Classification Rules Engine, SLA Tracking & Recommendations for the Trade Confirmation Exception Management Platform**  
> **Service Port:** `8084` · **Author / Lead:** Kapish · **Runtime:** Java 21 / Spring Boot 3.3 · **Storage:** Apache CouchDB · **Streaming:** Apache Kafka

---

## 1. Overview & Purpose

In financial post-trade operations, exceptions cannot simply be dumped into generic error logs. Under **FR7, FR8, and FR9**, the **`exception-service`** turns raw validation and ingestion failures into structured, actionable operational intelligence:

1. **Captures Exceptions (FR7):** Subscribes to `exception.created` from `validation-service` (STP failures) and `ingestion-service` (schema and parsing failures).
2. **Classifies Root Causes (FR8):** Evaluates failures against a deterministic rules engine:
   - Categorizes issues across standard taxonomies (`MISSING_ECONOMIC_DATA`, `MISSING_STATIC_REFERENCE_DATA`, `TEMPLATE_ISSUE`, `BUSINESS_RULE_FAILURE`, `TECHNICAL_PROCESSING_FAILURE`, `COUNTERPARTY_MATCHING_DISPUTE`).
   - Computes a `classificationConfidence` score (1.0 for deterministic rule matches; < 0.6 routes to manual review).
3. **Dynamic SLA Calculation:** Assigns real-time regulatory SLA countdown deadlines based on operational impact:
   - **`CRITICAL`:** +4 Hours
   - **`HIGH`:** +24 Hours
   - **`MEDIUM`:** +72 Hours
   - **`LOW`:** +7 Days
4. **Actionable Recommendations (FR9):** Generates human-readable remediation instructions, sets the target operational queue (`DATA_REMEDIATION`, `OPERATIONS_REVIEW`, `TECHNOLOGY_SUPPORT`, `REFERENCE_DATA`, `TEMPLATE_MAINTENANCE`), flags replay feasibility (`replayMayResolve`), and estimates resolution difficulty.
5. **Downstream Routing:**
   - Confidence $\ge 0.6$ &rarr; Published to `exception.classified` (triggers generic work items).
   - Confidence $< 0.6$ or counterparty dispute &rarr; Published to `manual.review.required` (routes to 4-eyes review queue).
   - Fatal unparseable errors &rarr; Published to `deadletter.trade` (FR12).

---

## 2. Event Flow & Contracts

```
[Kafka: exception.created]
          │
          ▼
╔═══════════════════════════════════════════════════════════╗
║                   exception-service                       ║
║  ┌─────────────────────────┐  ┌────────────────────────┐  ║
║  │ Ingestion & Idempotency │  │ Classification Rules   │  ║
║  │ (exceptions DB)         │  │ Engine (Confidence)    │  ║
║  └─────────────────────────┘  └────────────────────────┘  ║
║               │                            │              ║
║               ▼                            ▼              ║
║  ┌─────────────────────────┐  ┌────────────────────────┐  ║
║  │ Recommendation Engine   │  │ SLA Deadline Calculator│  ║
║  │ (Remediation Hints)     │  │ (+4h to +7d)           │  ║
║  └─────────────────────────┘  └────────────────────────┘  ║
║               │                            │              ║
║               ▼                            ▼              ║
║       CouchDB: exceptions & audit-events persistence      ║
╚═══════════════════════════════════════════════════════════╝
          │                                  │
    (Confidence >= 0.6)                (Confidence < 0.6 / Dispute)
          │                                  │
          ▼                                  ▼
[Kafka: exception.classified]       [Kafka: manual.review.required]
          │                                  │
          ▼                                  ▼
   Workitem Service                   Manual Review Queue
```

---

## 3. Operations REST APIs (Port 8084)

The service exposes comprehensive REST query endpoints for the React Operations UI (FR11):

| Endpoint | Method | Description |
| --- | --- | --- |
| `/exceptions/{id}` | GET | Returns a single exception detail document and its complete CouchDB audit trail. |
| `/exceptions?tradeId=&status=&category=&page=&size=` | GET | Returns a paginated list of exceptions with multi-field filtering. |
| `/exceptions/{id}/audit` | GET | Returns the chronological audit trail for a specific exception ID. |
| `/exceptions/metrics/summary` | GET | Returns in-memory operational metrics (total received, classified, manual review, duplicates, DLQ). |
| `/metrics/exception-rate` | GET | Returns classification rate percentage for the executive dashboard. |
| `/actuator/health` | GET | Spring Boot Actuator health check (includes CouchDB connectivity). |

*Note: Through the Nginx API gateway, these endpoints are mapped under `/api/exception/*`.*

---

## 4. Configuration Properties

| Property | Default Value | Environment Variable |
| --- | --- | --- |
| Server Port | `8084` | `SERVER_PORT` |
| Kafka Bootstrap | `localhost:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| Consumer Group | `exception-service-group` | `SPRING_KAFKA_CONSUMER_GROUP_ID` |
| CouchDB URL | `http://localhost:5984` | `COUCHDB_URL` |
| Exceptions DB | `exceptions` | `COUCHDB_EXCEPTIONS_DB` |
| Audit DB | `audit-events` | `COUCHDB_AUDIT_DB` |

---

## 5. Automated Testing

```bash
mvn -f services/pom.xml -pl common-lib,exception-service -am test
```
* **Coverage:** 134 passing unit and integration tests covering taxonomy classification rules, SLA calculations, recommendation enrichment, CouchDB persistence, and REST endpoints.
