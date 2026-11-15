# Enrichment Service (`enrichment-service`)

> **Enterprise Reference Data Enrichment, Legal Entity Resolution & Template Matching Engine for the TCEM Platform**  
> **Service Port:** `8082` · **Runtime:** Java 21 / Spring Boot 3.3 · **Storage:** Apache CouchDB 3.3 · **Streaming:** Apache Kafka 3.8

---

## 1. Overview & Purpose

In Over-The-Counter (OTC) derivative post-trade workflows, raw trades accepted by the edge ingestion pipeline contain essential transaction economics and internal party identifiers (e.g. `PTY-CP-101`), but lack the verified regulatory and legal reference metadata necessary for legal confirmation generation and trade reporting.

The **`enrichment-service`** provides the automated reference data enrichment layer between edge ingestion and downstream business validation:
1. Subscribes to the `trade.accepted` topic.
2. Evaluates CouchDB `enrichment-idempotency` to prevent duplicate enrichment under Kafka redeliveries (automatically bypassed for explicit `REPLAY` messages).
3. Performs in-flight joins against local reference catalogs:
   - **Counterparties (`counterparties.json`):** Resolves official Legal Entity Identifiers (LEI), ISO country codes, and registered legal entity names.
   - **Instruments (`instruments.json`):** Validates underlying instrument definitions, base/quote conventions, and currency pair specifications.
   - **Templates (`templates.json`):** Matches product types and trading jurisdictions to confirmable master document templates (e.g. `CONF-TMPL-FXO-STD-v2.1`).
   - **Product Permissions (`counterparty-products.json`):** Determines counterparty and product Straight-Through Processing (STP) eligibility flags.
4. Emits canonical enriched trade events to `trade.enriched` or safely routes unparseable payloads to `deadletter.trade`.

---

## 2. Event Flow & Contracts

```
[Kafka: trade.accepted]
          │
          ▼
╔═══════════════════════════════════════════════════════╗
║                 enrichment-service                    ║
║  ┌─────────────────────────┐  ┌────────────────────┐  ║
║  │ Enrichment Idempotency  │  │ Reference Catalog  │  ║
║  │ (enrichment-idempotency)│  │ (In-Memory JSONs)  │  ║
║  └─────────────────────────┘  └────────────────────┘  ║
║                             │                         ║
║                             ▼                         ║
║              TradeEnricher (Reference Mapping)        ║
╚═══════════════════════════════════════════════════════╝
          │                                  │
    (Enriched)                           (Fatal Error)
          │                                  │
          ▼                                  ▼
[Kafka: trade.enriched]             [Kafka: deadletter.trade]
          │                                  │
          ▼                                  ▼
   Validation Service                Workitem DLQ Store
```

### Upstream Contract
* **Topic Consumed:** `trade.accepted`
* **Message Key:** `tradeId`
* **Payload:** `TradeEvent` (schema `capstone-fpml-json-1.0`)

### Downstream Contract
* **Topic Produced:** `trade.enriched`
* **Message Key:** `tradeId`
* **Payload:** `TradeEvent` with populated `enrichmentMetadata` and enriched `trade.parties`.

---

## 3. Configuration & Ports

| Property | Default Value | Environment Variable |
| --- | --- | --- |
| Server Port | `8082` | `SERVER_PORT` |
| Kafka Bootstrap | `localhost:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| Consumer Group | `enrichment-service-group` | `SPRING_KAFKA_CONSUMER_GROUP_ID` |
| CouchDB URL | `http://localhost:5984` | `COUCHDB_URL` |
| Idempotency DB | `enrichment-idempotency` | `COUCHDB_ENRICHMENT_IDEMPOTENCY_DB` |
| Kafka Send Timeout | `10000ms` | `ENRICHMENT_KAFKA_SEND_TIMEOUT_MS` |

---

## 4. Endpoints & Health Check

* **Health Check:** `GET http://localhost:8082/actuator/health`
* **Application Info:** `GET http://localhost:8082/actuator/info`

---

## 5. Automated Tests

```bash
mvn -f services/pom.xml -pl common-lib,enrichment-service -am test
```
* **Coverage:** 16 passing unit and consumer tests covering reference catalog joins, party enrichment, idempotency deduplication, replay bypass, and DLQ dispatch.
