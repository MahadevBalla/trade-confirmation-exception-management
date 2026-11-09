# TCEM Platform – Operations Frontend

The operations dashboard for the **Trade Confirmation Exception Management Platform** (Capstone Specification §5, §7 FR11, §8 MVP, §9 Stretch).

Built with **React 19**, **TypeScript 5.8+**, **Vite 8**, and **Tailwind CSS v4**.

## Data Modes

The navbar shows the current data source and lets operators switch between:

- **Demo:** the original in-browser simulation, with mock data and local-only actions.
- **Live:** polls the proxied service APIs for trades, confirmations, exceptions, work items, DLQ entries, and audit events. Supported review, replay, work-item, and scenario actions are sent to the backend; API failures are surfaced in the UI and retried.

Automatic synthetic streaming and the manual-dispute simulation are demo-only. Live mode shows connection status and never silently falls back to mock records.

---

## 🚀 Key Features & Architectural Compliance

### 1. Event-Driven Workflow (No UI Trade Entry)
- Aligns strictly with **FR1** and implementation guardrails (§17): trade intake is entirely event-driven.
- Operators do **not** enter trades through a blotter form.
- The UI provides an **Ingest Synthetic Batch** controller and automated stream simulator that injects realistic synthetic trade events across Apache Kafka topics (`trade.raw`, `trade.accepted`, `trade.enriched`, `stp.validated`, `confirmation.generated`, `exception.created`, `deadletter.trade`).

### 2. Operations Overview Dashboard (FR11)
- **Executive KPIs**: Total Ingested Trades, Straight-Through Rate (STP %), Open Exceptions, Active Work Items, DLQ Queue Count, and SLA Breach Risk (<2h remaining).
- **STP vs. Exception Distribution Funnel**: Visual breakdown of Confirmed (STP Passed), Open Exceptions, Manual Review, and Dead-Letter Queue items.
- **Root-Cause Taxonomy Analysis (Spec §12)**: Distribution across the 6 MVP categories:
  - Missing Economic Data
  - Missing Static Reference Data
  - Template Issue
  - Business Rule Failure
  - Technical Processing Failure
  - Counterparty Matching Dispute
  - Legal Documentation Gap (Stretch)
- **SLA Aging Profile**: Aging distribution buckets (`<2h`, `2–6h`, `6–24h`, `>24h`).
- **Live Kafka Topic Stream Ticker**: Real-time event log with topic-coded pills and timestamps.

### 3. Trade Lifecycle View & FpML-JSON Model (FR2, FR3, FR11, Spec §10)
- Searchable and filterable table of canonical trade records.
- **Deep Economic Inspector**:
  - Parties block (FpML `PartyTradeIdentifier` pattern: Bank vs Counterparty)
  - Economics block (Notional, currency, buy/sell, option style, strike, underlying pair, premium)
  - Non-Economic terms (Jurisdiction, confirmation method, template hint)
  - Reference data enrichment provenance (`fieldProvenance`, derivation method, STP eligibility flags)
- **Canonical FpML JSON Viewer**: Syntax-highlighted view of the exact FpML-inspired JSON message contract.

### 4. Confirmation Generator & SHA-256 Fingerprinting (FR6, Spec §9 Stretch)
- Formatted ISDA Master Confirmation presentation (Plain text / HTML representation).
- **SHA-256 Fingerprint**: Evaluates confirmable fields to detect amendments with no confirmable changes.
- **Duplicate Suppression Indicator**: Flags when an amendment has an unchanged fingerprint and prevents redundant confirmations.

### 5. Exception Management & Recommendation Engine (FR7, FR8, FR9)
- Comprehensive Exception Queue filterable by category, assigned owner queue, severity, and SLA deadline.
- **Recommendation & Classification Engine**:
  - Implements the 4-factor decision model: Operational Impact, SLA Risk, Category Volume, and Resolution Difficulty.
  - Transparent rule engine generating human-readable recommended actions, optimal owner queue (`DATA_REMEDIATION`, `OPERATIONS_REVIEW`, `TEMPLATE_MAINTENANCE`, `REFERENCE_DATA`, `TECHNOLOGY_SUPPORT`), and replay feasibility flag.
- One-click resolution and work-item linkage.

### 6. Four-Eyes Manual Review & Reconciliation Screen (FR9, §8 MVP)
- Specialized screen for trades routed to `MANUAL_REVIEW` / `manual.review.required`.
- **Bilateral Term Reconciliation Tool**: Side-by-side comparison between internal bank booking terms and counterparty reported affirmation, highlighting discrepancies (e.g. Strike rate mismatch).
- **Supervisor Actions**:
  - Four-eyes sign-off with manual override reason &rarr; emits `CONFIRMED` confirmation note.
  - Rejection to Dead-Letter Queue with logged diagnostic rationale.

### 7. Generic Work Item Tracker (FR10)
- Mock ticket tracking service (`WI-40001` format) representing defect and data remediation tasks.
- Ticket statuses: `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`.
- Create and resolve work items linked to exceptions and trades.

### 8. Dead-Letter Queue & Controlled Replay Console (FR12)
- Inspects failed messages on `deadletter.trade` with full diagnostic Java stack trace excerpts.
- **Controlled Replay Action**:
  - Clears idempotency key.
  - Increments `replayCount`.
  - Sets message `eventType = REPLAY`.
  - Republishes payload to upstream topic and triggers re-enrichment.

### 9. Immutable Audit Trail Explorer (FR3, Spec §13 Auditability)
- Filterable log of state transitions across all 5 microservices (`ingestion-service`, `enrichment-service`, `validation-service`, `exception-service`, `workitem-service`).
- Captures status transitions, service name, timestamp, detail message, actor user ID, and replay flags.

### 10. Service Topology Map (Spec §5)
- Visual topology diagram displaying the 5 microservices, 10 Kafka topics with live message counters, and CouchDB document store schemas.

### 11. Multi-Role Switcher (Spec §9 & §13 Security)
- Quick switcher between:
  - **Operations Specialist**: Day-to-day exception inspection and data fix workflow.
  - **Supervisor / Lead**: Four-eyes review authorization, queue management, SLA escalations.
  - **System Administrator**: Topology, DLQ replay, and infrastructure monitoring.

---

## 🛠️ Local Development & Build

### Prerequisites
- Node.js 20+
- npm 10+

### Install Dependencies
```bash
npm install
```

### Run Development Server
```bash
npm run dev
```
Opens the application at `http://localhost:5173`.

### Run Lint (Oxlint)
```bash
npm run lint
```
Checks rules with 0 errors and 0 warnings.

### Production Build
```bash
npm run build
```
Type-checks with `tsc -b` and builds production bundle in `dist/`.

---

## 🐳 Docker Deployment
The frontend is pre-configured with a multi-stage `Dockerfile` and `nginx.conf`:

```bash
docker compose up -d --build frontend
```
Available at `http://localhost:3000`.
