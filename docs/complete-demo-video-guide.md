# TCEM Platform — Complete End-to-End Demo Video Recording Guide

> **Full-Application Feature Walkthrough, Recording Script & Copy-Paste Command Playbook**  
> **Target Output:** Complete 15–22 minute product & engineering demo video covering **100% of implemented features** (UI Demo & Live modes, all 5 Spring Boot microservices, all 10 Kafka topics, all 9 CouchDB databases, 10 trade scenarios, SHA-256 fingerprinting, 4-eyes manual review, DLQ replay, cancellations, schema routing, automated test suites, and NFR stress benchmarks).

---

## 1. Recording Setup & Prerequisites

### System & Tooling Requirements
Ensure the following tools are available before starting your screen recorder:
* **Docker Desktop / Docker Engine** (running, with at least 4–6 GB RAM allocated for containers)
* **CLI Tools:** `jq`, `curl`, `python3`, `git`
* **Build Tools (for Scene 4 verification):** JDK 21+, Maven 3.9+, Node.js 20+, npm 10+

### Recommended Screen Layout (for Clean Video Editing)
Prepare two workspaces / windows side-by-side or easily switchable:
1. **Browser Window (3 Pre-Pinned Tabs in Order):**
   * **Tab 1 — Operations Cockpit (Frontend):** `http://localhost:3000`
   * **Tab 2 — Apache Kafka UI:** `http://localhost:8080`
   * **Tab 3 — CouchDB Fauxton:** `http://localhost:5984/_utils` *(Credentials: `admin` / `password`)*
2. **Terminal Window (Zoomed font 14–16pt):** Working directory at repository root (`trade-confirmation-exception-management/`).

---

## 2. Master Feature Coverage Checklist

Use this matrix before wrapping recording to guarantee not a single implemented capability is missed:

| Layer | Features to Demonstrate on Camera | Scene |
|---|---|---|
| **DevOps & Gateway** | `./scripts/dev-up.sh --all`, non-root Alpine containers (`mem_limit: 768m`), `kafka-init` (10 topics × 3 partitions), `couchdb-init` (9 DBs + reduce view), `./scripts/smoke-test.sh --all` (15 checks), Nginx `/api/*` gateway (`:3000`) | Scene 0 & 3 |
| **Navbar Global Controls** | `Demo` ↔ `Live` mode toggle, connection status indicator (`connected`/`connecting`/`partial`/`offline`), event-driven scenario injector (`Ingest Synthetic Batch` / `Publish Scenario`), `Auto Stream` / `Stop Stream` (`4.5s` loop), 3-role switcher (`Operations Specialist`, `Supervisor / Lead`, `System Administrator`), live `STP %` badge, toast alerts | Scene 1 & 2 |
| **8 Frontend UI Screens** | 1. **Operations Overview** (3 quick-sim buttons, 6 clickable KPIs, STP segmented bar, 6-category root-cause chart, SLA aging buckets, clickable Live Kafka Topic Stream)<br>2. **Trade Lifecycle** (search, 6 status filters, 4-tab Trade Detail Modal: Overview/Economics/Provenance, Canonical FpML JSON, Confirmation Note w/ SHA-256 fingerprint & Duplicate Suppression + Copy/Download, Per-trade Audit History)<br>3. **Exception Queue** (search, Category/Impact/7-Queue filters, quick resolve, Exception Detail Modal w/ Deterministic Confidence, Recommendation card, linked work item, Resolve form)<br>4. **Manual Review** (Bilateral Term Discrepancy table e.g. `1.082` vs `1.085`, Approve & Generate Confirmation, Reject to DLQ)<br>5. **Work Items Tracker** (search, 7-queue & status filters, `+ New Work Item` modal across 5 item types, `Mark Done` / `Reopen` toggles)<br>6. **Dead-Letter Queue** (`PENDING_REPLAY`/`REPLAY_SUCCEEDED`/`PERMANENTLY_FAILED`, expandable diagnostics w/ Java stack trace & original JSON, `Replay Message`)<br>7. **Audit Trail Explorer** (search, 5 microservice filter pills, `REPLAY` & role-actor badges)<br>8. **Event Topology Map** (5 microservices `:8081–:8085`, 10 Kafka topics table, CouchDB schemas) | Scene 1 |
| **Live Pipeline & Edge Scenarios** | `VALID_STANDARD_STP`, `MISSING_ECONOMIC_STRIKE` (showing edge vs STP `EconomicFieldsRule` boundary & `held-trade-*`), `MISSING_REFDATA_COUNTERPARTY`, `TEMPLATE_ISSUE_MISSING`, `BUSINESS_RULE_NON_STP` (`PTY-CP-103`), `DUPLICATE_TRADE` (idempotency guard), `MALFORMED_JSON`, 500-trade `seed-data.sh` | Scene 2A–2B |
| **Advanced Backend Flows** | Non-confirmable vs confirmable `AMEND_TRADE` SHA-256 fingerprinting, `SchemaVersionRouter` `SCHEMA_VERSION_UNSUPPORTED` (`9.9`), Live 4-Eyes Manual Review approval auto-confirming held trade, Live self-healing DLQ replay loop, Live `CANCEL_TRADE` cascading work-item cancellation | Scene 2C–2G |
| **Test Suites & NFR Proof** | `mvn -f services/pom.xml verify` (**514 tests**), `npm run lint` + `npm run build`, `bash ./scripts/pipeline-test-safe.sh`, `./scripts/test-nfr.sh`, `benchmark_suite.py`, `stress_test.py` (`1M/day` & `100k/hr` NFR headroom) | Scene 4 |

---

## 3. Scene 0 — Clean Cold Boot & Infrastructure Verification (~2 mins)

Start with a clean slate so every database counter, topic offset, and UI badge starts deterministically.

### Step 0.1 — Clean Reset & Full Stack Startup
Run in terminal:
```bash
# Wipe old container volumes and start all 9 containers (Kafka, Kafka UI, CouchDB, 5 microservices, Nginx frontend)
./scripts/dev-down.sh --reset
./scripts/dev-up.sh --all
```
**What to point out on screen:**
1. Point out `.env.example` auto-copy if `.env` was missing.
2. Highlight one-shot initializers completing cleanly (`tcem-kafka-init` provisioning all 10 topics with `PartitionCount: 3`, and `tcem-couchdb-init` creating `_users`, `_replicator`, `_global_changes` + 9 application databases + `exceptions/_design/exception_counts`).
3. Show the automated **15-check Smoke Test** passing at the bottom (`SMOKE TEST PASSED! (15 checks passed)`).

### Step 0.2 — Inspect Container Hardening & Resource Caps
```bash
# Verify healthy status of all continuous containers
docker compose ps

# Verify cgroup memory allocation (768 MiB ceiling per JVM service, G1GC, non-root execution)
docker stats --no-stream --format "table {{.Name}}\t{{.Status}}\t{{.MemUsage}}\t{{.MemPerc}}"
```

---

## 4. Scene 1 — Operations Cockpit Complete UI Tour (Demo Mode) (~6 mins)

Open **Tab 1** (`http://localhost:3000`). The app boots into **Demo data** mode with curated realistic scenarios (`TRD-10001` through `TRD-10010`) so you can showcase every UI widget, interaction, and edge scenario crisply before firing live Kafka batches.

### Step 1.1 — Navbar Global Controls & Role Switcher
Point out every item across the top bar:
1. **Data Mode Pill (`Demo data • Switch to live`):** Explain dual operating modes — isolated in-browser simulation (`demo`) vs real backend polling (`live`). Keep it in **Demo** for Scene 1.
2. **Event Intake Menu (`Ingest Synthetic Batch`):** Emphasize that **there is no manual trade-entry form** anywhere in the UI (mirrors institutional FIX/Kafka feeds). Open the dropdown to show the 4 scenario presets (`Clean STP`, `Missing Economic Strike`, `Technical Timeout (DLQ)`, `Counterparty Matching Dispute`). Click `1. Clean STP Happy Path` to trigger a toast alert and live KPI update.
3. **`Auto Stream` Button:** Toggle **`Auto Stream`** ON briefly (watch synthetic trades auto-arrive every 4.5s with live toasts and ticker cards), then click **`Stop Stream`**.
4. **Role Switcher (`Role: Operations Specialist`):** Switch to **`Supervisor / Lead`** and **`System Administrator`** so viewers see how subsequent Audit Trail actions tag `USER-SUPERVISOR` / `USER-SYSTEM_ADMIN` as `actorUserId`.
5. **Live STP Badge & Left Sidebar Navigation:** Point out live badge counts on `Trade Lifecycle`, `Exception Queue`, `Manual Review` (pulsing amber badge), `Work Items Tracker`, `Dead-Letter Queue`, and the SLA target box (`< 10s STP`, `1M trades/day`, `FpML-JSON 1.0`).

### Step 1.2 — Operations Overview Tab
On **Operations Overview**:
1. Click **`Simulate Clean STP`**, **`Simulate Missing Strike`**, and **`Simulate DLQ Failure`** in the top banner to show instant telemetry reactivity.
2. Walk through all **6 clickable KPI Cards** (`Total Ingested Trades`, `Straight-Through Rate (STP)`, `Open Exceptions`, `Active Work Items`, `Dead-Letter Queue (DLQ)`, `SLA Breach Risk`).
3. Highlight the **STP vs. Exception Distribution** segmented bar and breakdown counts.
4. Highlight **Exceptions by Root Cause Category** (6 categories mapped to default queues) and click `View all ->` or go back to highlight **Exception SLA Aging Profile** (`< 2h`, `2–6h`, `6–24h`, `> 24h`) and escalation policy.
5. Scroll through the **Live Kafka Topic Stream** at the bottom; click any color-coded topic card (e.g. `confirmation.generated`) to demonstrate instant deep-linking into that trade's inspector.

### Step 1.3 — Trade Lifecycle, Confirmation Note & SHA-256 Fingerprinting
Navigate to **Trade Lifecycle**:
1. Type `EUR` or `Meridian` in the **Search bar**, then click through all 6 status filter pills (`All Trades`, `Confirmed (STP)`, `Exceptions`, `Manual Review`, `Work Item Linked`, `Dead Letter (DLQ)`). Reset to `All Trades`.
2. **Inspect `TRD-10001` (Happy Path):** Click the row (or Eye button) to open `TradeDetailModal`:
   * **Tab 1 (`Overview & Economics`):** Show `BANK` (`Apex Investment Banking AG`) & `COUNTERPARTY` (`Meridian Capital Partners Ltd`), FX Option Economics block, and `Reference Data Enrichment & Provenance` (`TMPL-FX-OPT-001 v1.2.0`, Counterparty STP `ELIGIBLE`, Product STP `ELIGIBLE`).
   * **Tab 2 (`Canonical FpML JSON`):** Show formatted `capstone-fpml-json-1.0` contract.
   * **Tab 3 (`Confirmation Note`):** Highlight plain-text ISDA notice, **SHA-256 Fingerprint**, **`Copy Text`**, and click **`Download Note`** to show the generated `.txt` confirmation file download.
   * **Tab 4 (`Audit History`):** Show chronological `RECEIVED -> ACCEPTED -> ENRICHED -> STP_PASSED -> CONFIRMED` timeline. Close modal.
3. **Inspect `TRD-10008` (Duplicate Suppression Stretch Feature):** Open **Confirmation Note** tab and point out the **`DUPLICATE SUPPRESSED`** cyan badge and matching **`Previous Fingerprint`** hash (`internalPricingRef` changed in v2 without altering confirmable fields). Close modal.
4. Briefly open **`TRD-10002`** (Strike highlighted `MISSING (Exception)`) and **`TRD-10004`** (Template `FX_EXOTIC_TARF_V3` unmatched).

### Step 1.4 — Exception Queue & Deterministic Recommendation Engine
Navigate to **Exception Queue**:
1. Demonstrate all 4 filter controls: search box, **Category** dropdown (`All Categories` through `Matching Dispute`), **Impact** dropdown (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`), and the **7 Queue Pills** (`Data Remediation`, `Operations Review`, `Template Maintenance`, `Reference Data`, `Technology Support`, `Specialist Review`, `Rules Owner`).
2. Click **`EXC-30001`** (`TRD-10002`, Missing Strike) to open `ExceptionDetailModal`:
   * Point out **Failure Trigger** (`ERR_ECON_MISSING_STRIKE`, `MISSING_STRIKE`, affected field `strike`).
   * Highlight **Recommendation & Classification Engine** card (`Confidence: 100% (Deterministic)`), human-readable action text, 4 metrics (`Owner Queue`, `Resolution Difficulty`, `Replay Feasibility: Replay May Resolve`, `SLA Deadline`).
   * Point out **Linked Work Item Ticket** (`WI-40001`).
   * Click **`Resolve Exception`**, enter sample resolution notes, and click **`Confirm Resolution`** — watch exception status turn `RESOLVED` and trade turn `CONFIRMED`.

### Step 1.5 — Four-Eyes Manual Review & Bilateral Reconciliation
Navigate to **Manual Review**:
1. Select **`TRD-10007`** in the left list (`Bilateral Discrepancy` — note `TRD-10005` Policy Non-STP is also listed).
2. Show the **Bilateral Term Discrepancy Reconciliation** table highlighting Internal Bank strike (`1.082`) vs Counterparty Affirmation (`1.085`) with the red **`DISCREPANCY`** badge.
3. Click **`Approve & Generate Confirmation`**, enter supervisor override notes (e.g., `"Verbal confirmation agreed at 1.0850"`), and submit **`Sign-off & Generate Confirmation`**.
4. Next, select **`TRD-10005`** (`PTY-CP-103` Non-STP), click **`Reject to DLQ`**, enter rejection rationale, and submit **`Confirm Rejection`**.
5. Finally, use the Navbar menu **`Ingest Synthetic Batch -> 4. Counterparty Matching Dispute`** to inject a fresh dispute trade back into Manual Review.

### Step 1.6 — Work Items Tracker
Navigate to **Work Items Tracker**:
1. Demonstrate Queue dropdown (all 7 queues) and Status dropdown (`OPEN`, `IN_PROGRESS`, `DONE`).
2. Click **`+ New Work Item`**, fill in Title, Trade ID (e.g., `TRD-10004`), Type (`TEMPLATE_FIX`), Queue (`TEMPLATE_MAINTENANCE`), Priority (`MEDIUM`), Description, and submit **`Create Work Item`**.
3. Demonstrate **`Mark Done`** and **`Reopen`** toggles, plus clicking a blue `Trade ID` link to jump back to Trade Lifecycle.

### Step 1.7 — Dead-Letter Queue (DLQ) & Replay Console
Navigate to **Dead-Letter Queue**:
1. Expand **`DLQ-90001`** (`TRD-10006` timeout on `trade.accepted` in `enrichment-service`):
   * Show the **Replay Protocol Contract** box (`eventType = REPLAY`, `replayCount` increment).
   * Show the **Diagnostic Stack Trace Excerpt** and **Original Failing Message Payload**.
2. Click **`Replay Message`** — watch the spinner, toast confirmation, badge transition to **`REPLAY_SUCCEEDED`**, and Replay Attempts increment.

### Step 1.8 — Audit Trail Explorer & Event Topology Map
1. Navigate to **Audit Trail Explorer**: filter by `workitem-service` and `validation-service`, search `TRD-10007`, and point out the cyan **`REPLAY`** badges and **`USER-SUPERVISOR` / `USER-SYSTEM_ADMIN`** actor IDs from your earlier actions.
2. Navigate to **Event Topology Map**: walk viewers through the 5-step microservice pipeline (`:8081`–`:8085`), the **10 Kafka Topics** table with live message counts, and the **Apache CouchDB Document Store Schemas** grid.

---

## 5. Scene 2 — Live Mode & Real Microservice Event Flows (~8 mins)

Now switch the entire application from in-memory simulation to the **real containerized microservices**.

### Step 2.1 — Switch UI to Live Mode & Inspect Guardrails
1. In the Navbar, click **`Switch to live`**.
2. Point out the indicator changing to **`API: connected`** (green dot) and **`Live Polling`** (5s refresh against `/api/trades`, `/api/validation/confirmations`, `/api/exceptions`, `/api/workitems`, `/api/dlq`, `/api/audit`), with `Auto Stream` safely disabled.
3. Click **`Publish Scenario -> 4. Counterparty Matching Dispute`** in Live mode to show the honest UI toast notice (`Scenario unavailable in live mode — Switch to demo mode to run this simulation`), then trigger **`1. Clean STP Happy Path`**. Wait ~5 seconds for the poll cycle, open **Trade Lifecycle** (`All Trades` filter — note that live `trades` docs store the ingestion status `ACCEPTED` while downstream confirmation status lives in `confirmations` and `audit-events`), click the newly generated trade, and open its **Confirmation Note** tab to show the backend `ALLEGED` confirmation plus the **`CONFIRMED`** entry in **Audit Trail Explorer**.

### Step 2.2 — Multi-Scenario Backend Batch & 500-Trade Seed (`ingestion-service`)
In terminal, generate live multi-scenario batches and inspect service metrics APIs:
```bash
# 1. List all 10 deterministic test scenarios implemented in ingestion-service
curl -s http://localhost:8081/trades/scenarios | jq

# 2. Inject a 50-trade batch across all 10 scenarios (70% valid STP, 30% edge cases)
./scripts/seed-data.sh --generate 50

# 3. Check live metrics across all microservices
sleep 3
curl -s http://localhost:8081/metrics/stp-rate | jq
curl -s http://localhost:8081/trades/metrics/summary | jq
curl -s http://localhost:8083/metrics/confirmation-count | jq
curl -s http://localhost:8084/exceptions/metrics/summary | jq
curl -s http://localhost:8084/metrics/exception-rate | jq
curl -s http://localhost:8085/workitems/summary | jq

# 4. (Optional) Load the bundled 500-trade seed dataset
./scripts/seed-data.sh
```
**What to point out:** Switch briefly back to UI (`Trade Lifecycle` / `Exception Queue`) to show all real backend trades, exceptions, and `WI-` work items rendered live from CouchDB! Point out enriched counterparty names (`Meridian Capital Partners Ltd`, `Vanguard Global Hedging Corp`, `Nordic Sovereign Holdings`).

### Step 2.3 — Edge Validation vs. STP Business Validation & Edge Failure Scenarios
Show how different failures split cleanly between `ingestion-service` (schema/syntax) and `validation-service` (economics/eligibility/templates):
```bash
# A) Missing Economic Strike (absent value passes ingestion schema, reaches enrichment, caught by validation-service EconomicFieldsRule)
curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=MISSING_ECONOMIC_STRIKE&sequence=5101&publishToKafka=true" | jq '{scenario, tradeId: .tradePayload.trade.tradeId}'

# B) Unregistered Counterparty (PTY-CP-UNKNOWN-999 -> caught downstream)
curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=MISSING_REFDATA_COUNTERPARTY&sequence=5102&publishToKafka=true" | jq '{scenario, tradeId: .tradePayload.trade.tradeId}'

# C) Missing Confirmation Template Hint (FX_OPTION_NONEXISTENT_TEMPLATE -> TEMPLATE_ISSUE)
curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=TEMPLATE_ISSUE_MISSING&sequence=5105&publishToKafka=true" | jq '{scenario, tradeId: .tradePayload.trade.tradeId}'

# D) Malformed JSON (breaks ingestion parse -> immediate deadletter.trade, non-replayable)
curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=MALFORMED_JSON&sequence=5106&publishToKafka=true" | jq

# E) Duplicate Trade Suppression (send DUPLICATE_TRADE twice -> 2nd emits DUPLICATE_MESSAGE exception)
curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=DUPLICATE_TRADE&sequence=1&publishToKafka=true" > /dev/null
curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=DUPLICATE_TRADE&sequence=1&publishToKafka=true" > /dev/null

sleep 3
curl -s "http://localhost:8084/exceptions?tradeId=TRD-005101" | jq '.exceptions[] | {exceptionId, category, subTheme, ownerQueue, recommendedAction, slaDeadline}'
curl -s -u admin:password "http://localhost:5984/confirmations/held-trade-TRD-005101" | jq '{_id, docType}'
```

### Step 2.4 — SHA-256 Amendment Fingerprinting & Duplicate Suppression (`AMEND_TRADE`)
Prove both sides of `ConfirmationFingerprint`: non-confirmable changes suppress re-issuance, while confirmable changes emit version 2:
```bash
# 1. Create clean v1 trade TRD-005001 (PTY-CP-101, STP-eligible)
curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=VALID_STANDARD_STP&sequence=5001&publishToKafka=true" > /dev/null
sleep 3
echo "--- Confirmations after v1 (expect 1) ---"
curl -s "http://localhost:8083/trades/TRD-005001/confirmations" | jq '[.[] | {confirmationId, tradeVersion, confirmationFingerprint, status}]'

# 2. Publish AMEND_TRADE changing ONLY non-confirmable 'internalPricingRef' (v2)
NOCHANGE_AMEND=$(curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=VALID_STANDARD_STP&sequence=5001&publishToKafka=false" \
  | jq -c '.tradePayload | .messageHeader.eventType="AMEND_TRADE" | .messageHeader.messageId="MSG-AMEND-NOCHANGE-5001" | .trade.tradeVersion=2 | .trade.fxOptionEconomics.internalPricingRef="PRC-NONCONFIRMABLE-999"')
printf '%s\t%s\n' "TRD-005001" "$NOCHANGE_AMEND" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic trade.raw --property parse.key=true --property "key.separator=$(printf '\t')"
sleep 3
echo "--- Confirmations after non-confirmable amend (STILL 1 - suppressed!) ---"
curl -s "http://localhost:8083/trades/TRD-005001/confirmations" | jq 'length'

# 3. Publish confirmable AMEND_TRADE (+10% notional via SyntheticTradeGenerator)
curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=AMEND_TRADE&sequence=5001&publishToKafka=true" > /dev/null
sleep 3
echo "--- Confirmations after confirmable amend (NOW 2 versions w/ previousConfirmationFingerprint) ---"
curl -s "http://localhost:8083/trades/TRD-005001/confirmations" | jq '[.[] | {confirmationId, tradeVersion, confirmationFingerprint, previousConfirmationFingerprint}]'
```

### Step 2.5 — Schema Version Routing Guard (`SCHEMA_VERSION_UNSUPPORTED`)
Demonstrate `SchemaVersionRouter` rejecting unknown schema versions in `enrichment-service`:
```bash
BAD_SCHEMA=$(curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=VALID_STANDARD_STP&sequence=5201&publishToKafka=false" \
  | jq -c '.tradePayload | .messageHeader.schemaVersion="capstone-fpml-json-9.9" | .messageHeader.messageId="MSG-BADSCHEMA-5201"')
printf '%s\t%s\n' "TRD-005201" "$BAD_SCHEMA" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic trade.raw --property parse.key=true --property "key.separator=$(printf '\t')"
sleep 3
curl -s "http://localhost:8085/dlq?size=20" | jq '[.content[] | select(.tradeId == "TRD-005201") | {_id, failedInService, dlqStatus, replayable, failureReason}]'
```

### Step 2.6 — Real 4-Eyes Manual Review Approval & Held-Trade Confirmation (`PTY-CP-103`)
Counterparty sequence `9403` (`(9403-1)%4 == 2`) selects `PTY-CP-103` (`stpEligible: false`), failing `BusinessEligibilityRule` and storing `held-trade-TRD-009403` in CouchDB:
```bash
# 1. Inject non-STP trade TRD-009403
curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=BUSINESS_RULE_NON_STP&sequence=9403&publishToKafka=true" > /dev/null
sleep 4

# 2. Confirm it has NO confirmation yet (404), but enriched payload is held in CouchDB
curl -s -o /dev/null -w "Before review HTTP status: %{http_code}\n" http://localhost:8083/trades/TRD-009403/confirmation

# 3. Create a MANUAL_REVIEW work item (you can also do this in UI: Work Items -> + New Work Item)
WI_ID=$(curl -s -X POST http://localhost:8085/workitems -H 'Content-Type: application/json' \
  -d '{"tradeId":"TRD-009403","type":"MANUAL_REVIEW","title":"4-Eyes Risk Waiver Review","description":"Verify Pacific Rim Asset Management policy waiver","ownerQueue":"OPERATIONS_REVIEW","priority":"HIGH"}' | jq -r .workItemId)
echo "Created Manual Review Work Item: $WI_ID"

# 4. Inspect $WI_ID in Live UI ("Work Items Tracker"), then approve via backend 4-eyes review endpoint
#    (Note: ManualReviewQueue UI reconciles Demo MANUAL_REVIEW trades as shown in Scene 1.5, because live ingestion
#     stores trades with status=ACCEPTED; backend review runs via /workitems/{id}/review and auto-confirms the held trade.)
curl -s -X POST "http://localhost:8085/workitems/$WI_ID/review" -H 'Content-Type: application/json' \
  -d '{"decision":"APPROVE","notes":"Supervisor waiver approved for demo","reviewer":"supervisor-1"}' | jq '{workItemId, status, reviewDecision}'
sleep 4

# 5. Verify confirmation was automatically generated from CouchDB held-trade snapshot
curl -s http://localhost:8083/trades/TRD-009403/confirmation | jq '{confirmationId, tradeId, status, confirmationFingerprint}'
```
**Live UI Interaction:**
1. Open **Work Items Tracker** in **Tab 1 (`http://localhost:3000`)** to show `$WI_ID` transitioned to `DONE`.
2. Open **Trade Lifecycle -> `TRD-009403` -> Confirmation Note** to show the newly generated `ALLEGED` ISDA confirmation and SHA-256 fingerprint, plus **`CONFIRMED`** in **Audit Trail Explorer**!

### Step 2.7 — Live Self-Healing Dead-Letter Queue (DLQ) & Controlled Replay Loop
Simulate a transient CouchDB failure in `validation-service`, watch it trap cleanly in DLQ, restore CouchDB, and replay from the **Live UI**:
```bash
# 1. Temporarily delete validation-idempotency DB to trigger a real persistence failure
curl -s -u admin:password -X DELETE http://localhost:5984/validation-idempotency | jq

# 2. Inject clean trade TRD-009501 -> dead-lettered cleanly by validation-service
curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=VALID_STANDARD_STP&sequence=9501&publishToKafka=true" > /dev/null
sleep 4

# 3. Restore validation-idempotency database so replay will succeed
curl -s -u admin:password -X PUT http://localhost:5984/validation-idempotency | jq
```
**Live UI Interaction:**
1. Open **Dead-Letter Queue** tab in Live Mode (`http://localhost:3000`).
2. Find the entry for **`TRD-009501`** (`status: PENDING_REPLAY`, `failedOnTopic: trade.enriched`). Expand it to show the real Java stack trace from `validation-service` and preserved payload.
3. Click **`Replay Message`**!
4. Wait ~5s for Live polling to refresh: show **`REPLAY_SUCCEEDED`** in DLQ, confirm `TRD-009501` generated its `ALLEGED` confirmation in **Trade Lifecycle**, show the linked `DATA_FIX` ticket auto-closed to `DONE` in **Work Items Tracker**, and show the `REPLAY` audit trail in **Audit Trail Explorer**!

### Step 2.8 — Trade Cancellation Event Cascading (`CANCEL_TRADE`)
Show that cancelling an unresolved trade cancels all its open work items without generating a confirmation:
```bash
# Cancel TRD-005101 (created in Step 2.3 with an open DATA_FIX work item)
echo "--- Work items BEFORE cancel ---"
curl -s "http://localhost:8085/workitems?tradeId=TRD-005101" | jq '[.content[] | {workItemId, status}]'

CANCEL_MSG=$(curl -s -X POST "http://localhost:8081/trades/generate/scenario?scenario=MISSING_ECONOMIC_STRIKE&sequence=5101&publishToKafka=false" \
  | jq -c '.tradePayload | .messageHeader.eventType="CANCEL_TRADE" | .messageHeader.messageId="MSG-CANCEL-5101"')
printf '%s\t%s\n' "TRD-005101" "$CANCEL_MSG" | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic trade.raw --property parse.key=true --property "key.separator=$(printf '\t')"
sleep 3

echo "--- Work items AFTER cancel (expect CANCELLED) ---"
curl -s "http://localhost:8085/workitems?tradeId=TRD-005101" | jq '[.content[] | {workItemId, status, resolvedAt}]'
echo "--- Audit trail for TRD-005101 ---"
curl -s "http://localhost:8081/trades/TRD-005101/audit" | jq '[.[] | {stage, status, details}]'
```

---

## 6. Scene 3 — Infrastructure Deep-Dive (Kafka UI, CouchDB Fauxton & Health) (~2 mins)

1. **Apache Kafka UI (`http://localhost:8080` — Tab 2):**
   * Open **Topics**: show all **10 topics** (`trade.raw`, `trade.accepted`, `trade.enriched`, `stp.validated`, `confirmation.generated`, `exception.created`, `exception.classified`, `workitem.linked`, `manual.review.required`, `deadletter.trade`) — each showing `Partitions: 3`.
   * Open **Messages** on `confirmation.generated` or `workitem.linked`: show partition key (`tradeId`) and preserved `messageHeader` (`correlationId`, `eventType`, `replayCount`).
   * Open **Consumers**: show the isolated consumer groups across all services (`ingestion-service-group`, `enrichment-service-group`, `validation-service-group`, `validation-service-group-manual-review`, `exception-service-group`, `workitem-service-group*`).
2. **Apache CouchDB Fauxton (`http://localhost:5984/_utils` — Tab 3):**
   * Show the **9 application databases** (`trades`, `confirmations`, `exceptions`, `work-items`, `audit-events`, `stp-metrics`, `dlq-entries`, `enrichment-idempotency`, `validation-idempotency`).
   * Open `confirmations` (point out `CONF-*` docs alongside `held-trade-*` snapshots) and `audit-events`.
3. **Spring Boot Actuator Health Check Loop (Terminal):**
   ```bash
   for port in 8081 8082 8083 8084 8085; do
     echo -n "Port $port -> "
     curl -s "http://localhost:$port/actuator/health" | jq -c '{status, couchdb: .components.couchDbHealthIndicator.status // .components}'
   done
   ```

---

## 7. Scene 4 — Automated Tests, CI/CD & NFR Performance Benchmarks (~3 mins)

### Step 4.1 — Full Backend Reactor & Frontend Build Verification
```bash
# Runs all 514 unit, repository, and embedded-Kafka integration tests across 6 Maven modules
mvn -f services/pom.xml verify

# Runs frontend Oxlint (0 errors/warnings) + TypeScript check & Vite bundle
npm --prefix frontend run lint && npm --prefix frontend run build
```

### Step 4.2 — End-to-End Pipeline Automated Test Script
```bash
# Note: invoke via `bash` (safe non-destructive suite vs full destructive DLQ suite)
bash ./scripts/pipeline-test-safe.sh
# Or full CI regression suite (includes temporary DB deletion DLQ replay check):
bash ./scripts/pipeline-test.sh
```

### Step 4.3 — NFR Latency & 1M Trades/Day Stress Benchmarks
```bash
# 1. Automated SLA script (single-trade latency <= 10s, daily 1M/day baseline, peak 100k/hr burst, container memory)
./scripts/test-nfr.sh

# 2. Multi-sample statistical STP latency profiler (min, p50, mean, p90, p95, max)
python3 scripts/benchmark_suite.py --mode latency --samples 20

# 3. Sustained rate zero-backpressure test (50 trades/sec continuously = 4.3x daily & 1.8x peak hourly target)
python3 scripts/stress_test.py --mode sustained --rate 50 --duration 20

# 4. Chunked high-volume stress test (5,000 trades in memory-safe 500-trade batches)
python3 scripts/stress_test.py --count 5000 --batch-size 500
```
Point out that full benchmark methodology and code traceability live in [`docs/reports/stress_testing_report.md`](reports/stress_testing_report.md), and mention the 3 automated GitHub Actions pipelines in `.github/workflows/` (`backend-ci.yml`, `frontend-ci.yml`, `smoke-test.yml`).

---

## 8. Scene 5 — Clean Teardown & Closing Summary (~30 secs)

```bash
# Stop containers while preserving persistent Kafka & CouchDB volumes
./scripts/dev-down.sh

# Or reset everything back to a completely clean state
./scripts/dev-down.sh --reset
```

---

## Quick Troubleshooting Tips During Recording
* **Live UI hasn't shown a new backend trade yet?** Wait up to **5 seconds** (`AppContext.tsx` polls backend REST APIs every 5,000 ms).
* **Running `pipeline-test.sh` or `pipeline-test-safe.sh` fails with `Permission denied`?** Run it explicitly with `bash ./scripts/pipeline-test.sh` (or `bash ./scripts/pipeline-test-safe.sh`), since those two scripts currently have file mode `100644` in git.
* **Want deterministic small numbers (`TRD-000001` etc.) cleanly on camera?** Always start Scene 0 with `./scripts/dev-down.sh --reset && ./scripts/dev-up.sh --all`.
* **Running `mvn verify` offline/sandboxed:** Ensure Maven runs in your normal host shell with access to the local JDK 21+ runtime and `~/.m2` cache.
