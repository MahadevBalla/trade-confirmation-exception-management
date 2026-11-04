# TCEM 100% Live Demo Presentation Guide & Script

**Platform:** Trade Confirmation Exception Management (TCEM)  
**Format:** 100% Live Screen-Shared Demonstration (NO SLIDES / NO PPT)  
**Total Duration:** ~15 Minutes (12 mins demo + 3 mins Q&A)  
**Target Audience:** Evaluators, Enterprise Architects, and Business Stakeholders  
**Branch:** `feat/frontend-live-data` (7 critical showstoppers and 20 Sprint 2 fixes resolved; the UI runs on real data)  

---

> **The UI now shows real backend data by default, after signing in.** Check that the navbar badge says **LIVE**. Clicking a scenario button publishes a real trade to Kafka, and approve, reject, replay and resolve all call the backend, with your role enforced by the API. **DEMO DATA** is the old in-browser simulation: use it only as a fallback if the stack fails, and say so if you do. Some speaking scripts below were written for the simulation, so each presenter should rehearse their act against the live screens.

## 1. Pre-Flight Setup & Environment Checklist (Done 10 mins Before Demo)

Before beginning the live presentation, ensure the environment is clean, healthy, and pre-warmed:

### 1.1 Terminal & Docker Startup
Run in terminal:
```bash
# 1. Clean boot all 5 microservices, infrastructure, and frontend gateway
./scripts/dev-up.sh --all

# 2. Verify all 7 containers are healthy
docker compose ps
```
*Expected status:* `tcem-kafka`, `tcem-couchdb`, `tcem-kafka-ui`, `tcem-ingestion`, `tcem-enrichment`, `tcem-validation`, `tcem-exception`, `tcem-workitem`, and `tcem-frontend` are all **Up (healthy)**.

### 1.2 Browser Tabs to Pre-Open (In Order):
1. **Tab 1 — Operations Cockpit (UI):** `http://localhost:3000` (Main UI on Dashboard view).
2. **Tab 2 — Kafka UI (Event Streams):** `http://localhost:8080` (Navigated to Topics view showing all 10 topics).
3. **Tab 3 — CouchDB Fauxton (Persistence):** `http://localhost:5984/_utils` (Navigated to Databases).
4. **Tab 4 (Split-Screen / Terminal):** Clean terminal window ready to run `./scripts/seed-data.sh --generate 5`.

---

## 2. Team Service & Demo Role Distribution

All 6 team members will actively participate, speaking to the exact service domain and functional requirement (FR) they own:

| Presenter | Service / Domain | Core Focus in Live Demo | Demo Screen |
|:---|:---|:---|:---|
| **Mayank Chauhan** (Team Lead) | `ingestion-service` | Ingestion architecture, trade generator, edge validation (FR1, FR2, FR3) | Terminal + Kafka UI (`trade.raw`, `trade.accepted`) |
| **Bhavik** | `enrichment-service` | Reference data enrichment, LEI mapping, template resolution (FR4) | Kafka UI (`trade.enriched`) + Trade Detail Modal |
| **Mahadev** | `validation-service` | STP rules engine, SHA-256 fingerprinting, confirmation generation (FR5, FR6) | Kafka UI (`stp.validated`, `confirmation.generated`) + CouchDB |
| **Kapish** | `exception-service` | Exception taxonomy, SLA countdown, recommendation rules engine (FR7, FR8, FR9) | Frontend (`Exceptions` Queue & Rule Recommendation Cards) |
| **Oren** | `workitem-service` + DevOps | Work item lifecycle, immutable audit trail, DLQ replay console (FR10, FR11, FR12) | Frontend (`Work Items`, `Audit Trail`, `Dead-Letter Queue`) |
| **Gunjan** | `frontend` & QA | Executive metrics, aging charts, service topology & Sprint 2 roadmap | Frontend (`Dashboard`, `Service Topology`, Summary) |

---

## 3. Step-by-Step Live Demo Script (15 Minutes)

---

### Act 1: Executive Overview & Ingestion Pipeline (Mayank) — 2.5 Mins
* **Presenter:** Mayank Chauhan
* **Screen:** Terminal & Kafka UI (`http://localhost:8080`)

#### Speaking Script:
> *"Hello everyone. Welcome to the live demonstration of TCEM—our enterprise-grade Trade Confirmation and Exception Management platform designed for high-volume OTC derivatives.*
>
> *In post-trade processing, unconfirmed trades represent systemic operational and credit risk. When trade confirmations fail STP—due to economic discrepancies, invalid LEIs, or template mismatches—they enter costly manual reconciliation queues.*
> 
> *TCEM solves this with a fully reactive, event-driven microservices architecture powered by Apache Kafka, Spring Cloud Stream, and Apache CouchDB.*
>
> *I’ll kick off our live demonstration by triggering our synthetic trade generation pipeline."*

#### Actions on Screen:
1. In terminal, run:
   ```bash
   ./scripts/seed-data.sh --generate 5
   ```
2. Switch immediately to **Kafka UI** (`http://localhost:8080/ui/clusters/tcem-capstone-cluster-01/topics`).
3. Click on **`trade.raw`**: show 5 incoming raw FpML payloads.
4. Click on **`trade.accepted`**: show that `ingestion-service` validated the mandatory envelope headers, structural JSON format, and enforced CouchDB-backed idempotency.
5. *"Notice that our ingestion engine filters duplicates in O(1) time and assigns a canonical correlation ID. Now, these accepted trades flow seamlessly to Bhavik for Reference Data Enrichment."*

---

### Act 2: Reference Data Enrichment (Bhavik) — 2.0 Mins
* **Presenter:** Bhavik
* **Screen:** Kafka UI (`trade.enriched`) & Frontend (`http://localhost:3000` -> Trades Tab)

#### Speaking Script:
> *"Thanks Mayank. When trades arrive on `trade.accepted`, they contain raw internal counterparty codes and basic economics, but they lack the reference data required for legal confirmation and regulatory reporting.*
>
> *This is where `enrichment-service` executes Functional Requirement 4 (FR4).*
> 
> *As you can see here in Kafka UI on the `trade.enriched` topic, our service enriches the trade payload in-flight against our reference datasets:*
> 1. *It maps raw party IDs like `PTY-CP-101` to official Legal Entity Identifiers (LEI), country codes, and enriched legal entity names: `Meridian Capital Partners Ltd`.*
> 2. *It resolves the confirmable template version based on counterparty product eligibility (e.g. `CONF-TMPL-FXO-STD-v2.1`).*
> 3. *It preserves envelope idempotency across the processing boundary.*
>
> *Now let's switch to the Operations Cockpit on port 3000. In the Trades list, when we open trade `TRD-10001`, you can see the enriched legal entity name displayed right here in the contract parties block, confirming that reference data is immediately available to operations specialists.*
>
> *Once enriched, the trade is emitted to `trade.enriched` for Mahadev's validation engine."*

---

### Act 3: STP Rules Engine & Legal Confirmation (Mahadev) — 2.5 Mins
* **Presenter:** Mahadev
* **Screen:** Kafka UI (`stp.validated`, `confirmation.generated`) & CouchDB Fauxton (`http://localhost:5984/_utils`)

#### Speaking Script:
> *"Thanks Bhavik. On `trade.enriched`, the trade enters the core decisioning core: `validation-service`, covering FR5 and FR6.*
>
> *Our STP rules engine evaluates three critical pillars:*
> 1. *Business Eligibility: Verifies counterparty active status and product permissioning.*
> 2. *Template Resolution: Validates confirmable legal template availability.*
> 3. *Economic Completeness: Checks strike rates, notional currency pairs, and expiry date sanity.*
>
> *Let's look at the Kafka topics:*
> - *Every trade publishes an evaluation outcome to `stp.validated` for real-time auditability.*
> - *For trades passing STP, `validation-service` generates a legal confirmation record and emits it to `confirmation.generated`.*
>
> *Crucially, we implemented an algorithmic SHA-256 confirmation fingerprinting engine (`ConfirmationFingerprint`). If a trade amendment arrives with no confirmable field changes, our fingerprint engine detects identical state and suppresses redundant legal confirmation notices, eliminating confirmation fatigue.*
>
> *If we look into CouchDB under the `confirmations` database, you can see the generated confirmation document, its canonical fingerprint, and its status set to `ALLEGED`. The confirmation is locked, audited, and ready for counterparty matching.*
>
> *Now, what happens when a trade FAILS STP? Kapish will show how exceptions are captured and classified."*

---

### Act 4: Exception Taxonomy & Recommendation Engine (Kapish) — 2.5 Mins
* **Presenter:** Kapish
* **Screen:** Frontend (`http://localhost:3000` -> Exceptions Tab)

#### Speaking Script:
> *"Thanks Mahadev. In high-volume environments, exceptions cannot simply be dumped into a generic error log. Under FR7, FR8, and FR9, `exception-service` turns raw validation failures into actionable intelligence.*
>
> *Here on the Exceptions Console:*
> 1. *Taxonomy & Classification: Every exception is classified into our standard taxonomy—such as `ECONOMIC_MISMATCH`, `INVALID_LEI`, or `TEMPLATE_NOT_FOUND`—with an associated confidence score (e.g., 95%).*
> 2. *Automated Routing: Exceptions are routed to specific specialist queues: Data Remediation, Operations Review, or Reference Data.*
> 3. *SLA Deadlines: Notice the real-time SLA countdown timers. Each category has a defined resolution SLA, automatically highlighting items at risk of breach.*
>
> *Now let's click on an exception:*
> *Look at these Recommendation Cards on the right panel. Our deterministic rule engine evaluates the failure symptoms and generates an actionable remediation guide for the operator:*
> - *Suggested Resolution Action (e.g., 'Verify strike rate against Bloomberg pricing source').*
> - *Estimated resolution difficulty rating.*
> - *Rule confidence score.*
>
> *This drastically reduces Mean Time to Resolution (MTTR) for operations teams.*
>
> *Next, Oren will walk through how these exceptions become managed Work Items and how our Dead-Letter Queue handles recovery."*

---

### Act 5: Work Items, Audit Trail & DLQ Replay Console (Oren) — 2.5 Mins
* **Presenter:** Oren
* **Screen:** Frontend (`http://localhost:3000` -> Work Items, Audit Trail, and Dead-Letter Queue Tabs)

#### Speaking Script:
> *"Thanks Kapish. Once an exception is classified, it triggers `workitem-service`, implementing FR10, FR11, and FR12.*
>
> *First, Work Item Tracker (FR10):*
> *Here you see every ticket assigned to an owner queue with status tracking from `OPEN` to `IN_PROGRESS` and `DONE`. Work items can be assigned to operators and tracked against team workloads.*
>
> *Second, Immutable Audit Trail (FR11):*
> *Under the Audit Trail tab, every lifecycle transition—from raw message receipt, through enrichment and validation, to work item creation—is captured with timestamp, actor, service name, and previous/next states. This ensures 100% compliance with MiFID II and CFTC audit standards.*
>
> *Third, Dead-Letter Queue & Controlled Replay (FR12):*
> *Let's navigate to the Dead-Letter Queue Console. Technical and unrecoverable downstream failures are isolated on `deadletter.trade`.*
> *Operators can inspect the full error payload, exception class, and diagnostic stack traces.*
> *A replay sets the message's event type to `REPLAY`, increments its replay count and republishes it to the topic it failed on. Services skip their duplicate check for replays, and the DLQ entry closes when the message reaches the next stage, all without service downtime.*
>
> *Finally, Gunjan will walk us through our Executive Operations Cockpit, Service Topology, and our production hardening roadmap."*

---

### Act 6: Operations Cockpit, Topology & Roadmap (Gunjan) — 2.0 Mins
* **Presenter:** Gunjan
* **Screen:** Frontend (`http://localhost:3000` -> Dashboard & Service Topology Tabs)

#### Speaking Script:
> *"Thanks Oren. To tie the entire ecosystem together, our React 19 frontend provides executive operations leadership with a single pane of glass:*
>
> 1. *Executive Metrics (FR3): Real-time STP rate gauge (79% on the seed data, which deliberately includes edge cases, against an 85% target), active exceptions counter, and aged items breakdown.*
> 2. *Live Stream Event Ticker: Real-time visual heartbeat of Kafka events pulsing across microservices.*
> 3. *Interactive Service Topology: On the Topology tab, operators and system architects can visualize the physical decoupling of our 5 Spring Boot microservices, the 10 Kafka topics connecting them, and the underlying CouchDB document databases.*
>
> *To prepare for enterprise production readiness, our team also completed a comprehensive 37-finding architectural consistency audit. We eliminated all critical containerization showstoppers, hardened our Kafka persistence volumes, and aligned our data contracts.*
>
> *Everything you have seen on this screen is live: real trades, flowing through Kafka and CouchDB. We have also completed end-to-end replay tracking, confirmation generation from manual reviews, and full-pipeline CI coverage. Our roadmap covers scaling this UI beyond demo volumes.*
>
> *We are proud to present TCEM as a complete, scalable, and resilient trade confirmation exception management platform. Thank you, and we now welcome your questions."*

---

## 4. The Golden Guardrails: What to Show vs. What NOT to Show

To guarantee a 100% smooth, glitch-free presentation, every team member must strictly follow these demo boundaries:

| Area | Safe to Show Live ✅ | DO NOT Click / Show ❌ | Technical Reason (From Audit Report) |
|:---|:---|:---|:---|
| **Trade Generation** | Ingest clean synthetic batches (`--generate 5`). | Do not replay a `CANCEL_TRADE`. | Replay overwrites the event type, so a replayed cancel runs through STP. |
| **Trade Types** | Showcase FX Options and Equity Swaps. | Avoid counterparty-change amendments on trades confirmed before the fingerprint fix when running on an old CouchDB volume. | The party-path bug (C1-01) is fixed, but fingerprints stored before the fix lack party IDs, so the first amend of such a trade re-confirms once. A freshly booted stack is unaffected. |
| **Manual Review** | Approve or reject a real trade from the queue, then show its confirmation or dead-letter entry. | Do not describe a bilateral reconciliation in live mode. | The backend has no counterparty affirmation data, so that table only exists in demo mode. Live mode shows the real reason for review. |
| **DLQ Console** | Run `./scripts/demo-dlq-failure.sh`, then click Replay Message on the new entry. | Do not use the malformed-message button to demonstrate replay. | Malformed messages are real dead letters but cannot be replayed. |
| **Frontend State** | Refresh freely: live data comes from the backend. Work items and queues update within about 3 seconds. | Do not switch to DEMO DATA without saying so. | Demo mode is a simulation and resets on refresh (C4-01). |


### 4.1 Before You Present

The full steps, a demo day checklist and troubleshooting are in [`live-and-demo-data-guide.md`](../live-and-demo-data-guide.md).

1. On a fresh stack, sign in as `admin` / `admin-demo`, then click **Load 500 seed trades** on the banner, then wait about a minute. Expect 485 distinct trades, 383 confirmed (79% STP) and 17 in manual review.
2. To show replay, run `./scripts/demo-dlq-failure.sh` just before that part of the demo. It creates a real, replayable failure within a few seconds, and the entry appears on the Dead-Letter Queue screen.
3. Use **Publish Clean STP** and the other buttons to show new trades arriving. Results appear within a few seconds.

---

## 5. Proactive Q&A Defense (Turning Audit Findings into Strengths)

When evaluators ask technical architectural questions, use these senior engineering answers:

### Q1: "How do you prevent duplicate trades during high-volume spikes or network retries?"
> **Answer (Mayank):**  
> *"We implement a multi-tiered idempotency strategy. Ingestion checks a CouchDB-backed `IdempotencyKey` formatted as `idempotency:{tradeId}:{messageId}:{schemaVersion}:{eventType}` before accepting the trade. Downstream, `enrichment-service`, `validation-service` and `exception-service` each maintain an isolated idempotency collection (`validation-service` uses `validation-idempotency`, so a redelivered message for a trade that failed STP is skipped instead of raising a second exception). On top of that, `validation-service` uses SHA-256 fingerprinting so amend messages with no confirmable change never generate duplicate confirmations."*

### Q2: "What happens if a downstream service fails during event processing?"
> **Answer (Oren):**  
> *"All consumer pipelines are wrapped in dead-letter routing. Unparseable payloads or unrecoverable processing exceptions are published to `deadletter.trade` with full diagnostic stack traces. Our `workitem-service` captures these into a dedicated DLQ repository, allowing operations engineers to diagnose and safely replay messages with incremented retry counts."*

### Q3: "What is your roadmap for schema changes and backward compatibility?"
> **Answer (Mahadev / Gunjan):**  
> *"Our architectural audit flagged schema version routing as a key evolution item. `enrichment-service` and `validation-service` now both route incoming payloads through `SchemaVersionRouter`: supported versions are parsed (with a reserved migration path for 1.1) and unsupported versions go to the dead-letter queue with a `SCHEMA_VERSION_UNSUPPORTED` reason instead of being silently mis-parsed. In our next hardening sprint we extend the same pattern to the remaining consumers."*

---

## 6. Quick Cheat Sheet for Demo Day

```
┌────────────────────────────────────────────────────────────────────────┐
│                        DEMO TIMELINE CHEAT SHEET                       │
├─────────┬───────────────────┬──────────────────────────────────────────┤
│ 00:00   │ Mayank            │ Architecture Overview + Seed 5 Trades   │
│ 02:30   │ Bhavik            │ Kafka `trade.enriched` + LEI Party Data  │
│ 04:30   │ Mahadev           │ STP Rules, Fingerprint & CouchDB Confirm │
│ 07:00   │ Kapish            │ Exception Taxonomy & Recommendation Card │
│ 09:30   │ Oren              │ Work Items, Audit Trail & DLQ Replay     │
│ 12:00   │ Gunjan            │ Dashboard, Service Topology & Roadmap    │
│ 14:00   │ All Team          │ Q&A Defense                              │
└─────────┴───────────────────┴──────────────────────────────────────────┘
```

