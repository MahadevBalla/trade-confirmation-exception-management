# TCEM Platform — Operations Frontend

> **Executive Operations Cockpit, Exception Remediation Console & Distributed Stream Visualizer for the Trade Confirmation & Exception Management (TCEM) Platform**  
> Built with **React 19** · **TypeScript 5.8** · **Vite 8** · **Tailwind CSS v4** · **Nginx Gateway (Port 3000)**

---

## 1. Operating Modes

The navigation bar displays the current environment status and allows operators to switch operational modes:

- **Live Mode (Default in Production / Docker):** Connects to backend microservices via the Nginx reverse proxy. Displays real-time data from CouchDB and Kafka streams (trades, confirmations, exceptions, work items, DLQ entries, audit trails). User actions (manual review sign-offs, DLQ replays, work-item updates, scenario injections) execute against backend REST APIs.
- **Demo Mode (In-Browser Simulation):** Enables offline demonstration and UI capability testing using realistic in-memory mock datasets without requiring backend microservices or Docker containers.

---

## 2. Key Modules & Functional Capabilities

### 1. Event-Driven Intake (Zero Manual Trade Entry Blotters)
- Trade intake is strictly asynchronous and event-driven, mirroring Tier-1 institutional trading desks.
- Operators do not key in trades through manual blotter forms.
- An **Ingest Synthetic Batch** controller and scenario generator allows operators to inject clean or edge-case trades into Kafka topics (`trade.raw`, `trade.accepted`, `trade.enriched`, `stp.validated`, `confirmation.generated`, `exception.created`, `deadletter.trade`).

### 2. Operations Overview & Executive Cockpit
- **Executive KPIs**: Real-time counters for Total Ingested Trades, Straight-Through Processing Rate (STP %), Open Exceptions, Active Work Items, DLQ Queue Depth, and SLA Breach Risk (<2 hours remaining).
- **STP vs. Exception Distribution Funnel**: Visual distribution across Confirmed (STP Passed), Open Exceptions, Manual Review, and Dead-Letter Queue items.
- **Root-Cause Taxonomy Analysis**: Categorical distribution across standard classifications:
  - Missing Economic Data
  - Missing Static Reference Data
  - Template Issue
  - Business Rule Failure
  - Technical Processing Failure
  - Counterparty Matching Dispute
  - Legal Documentation Gap
- **SLA Aging Profile**: Aging distribution buckets (`<2h`, `2–6h`, `6–24h`, `>24h`).
- **Live Kafka Topic Stream Ticker**: Real-time event log with topic-coded pills and timestamps.

### 3. Trade Lifecycle Explorer & Canonical FpML Inspector
- Filterable and searchable table of canonical trade records.
- **Deep Economic Inspector**:
  - Parties block (Bank vs. Counterparty roles and LEI identifiers)
  - Economics block (Notional, currency, buy/sell, option style, strike, underlying currency pair, premium)
  - Reference data enrichment provenance (`fieldProvenance`, derivation method, STP eligibility flags)
- **Canonical FpML JSON Viewer**: Syntax-highlighted view of the exact FpML-JSON message contract.

### 4. Legal Confirmation Viewer & SHA-256 Fingerprinting
- Monospace plain-text ISDA Master Confirmation presentation rendered deterministically.
- **SHA-256 Fingerprint Display**: Highlights the cryptographic hash calculated over confirmable contract terms.
- **Duplicate Suppression Indicator**: Displays when an amendment has an unchanged fingerprint, preventing redundant legal confirmation notices.

### 5. Exception Remediation & Recommendation Console
- Comprehensive Exception Queue filterable by category, assigned queue, severity, and regulatory SLA deadline.
- **Recommendation Engine**:
  - Implements the 4-factor decision model: Operational Impact, SLA Risk, Category Volume, and Resolution Difficulty.
  - Transparent rule engine generating human-readable recommended actions, optimal owner queue (`DATA_REMEDIATION`, `OPERATIONS_REVIEW`, `TEMPLATE_MAINTENANCE`, `REFERENCE_DATA`, `TECHNOLOGY_SUPPORT`), and replay feasibility flag.
- One-click work-item creation and linkage.

### 6. Four-Eyes Manual Review & Bilateral Reconciliation Screen
- Dedicated workflow for trades routed to `MANUAL_REVIEW` / `manual.review.required`.
- **Bilateral Term Reconciliation Tool**: Side-by-side comparison between internal bank booking terms and counterparty reported affirmation, highlighting discrepancies (e.g. Strike rate mismatch).
- **Supervisor Actions**:
  - Four-eyes sign-off with manual override rationale &rarr; triggers `validation-service` to immediately generate the legal confirmation.
  - Rejection to Dead-Letter Queue with logged diagnostic justification.

### 7. Remediation Work-Item Tracker
- Operational defect and remediation ticket management (`WI-40001` format).
- Ticket statuses: `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`.
- Full linkage between work items, underlying exceptions, and affected trades.

### 8. Dead-Letter Queue (DLQ) & Controlled Replay Console
- Inspects failed messages on `deadletter.trade` with full diagnostic Java stack trace excerpts.
- **Controlled Replay Action**:
  - Resets idempotency keys for the replay attempt.
  - Increments `replayCount`.
  - Sets message `eventType = REPLAY`.
  - Republishes payload to upstream topic and tracks re-enrichment.

### 9. Immutable Cross-Service Audit Ledger
- Unified timeline view of state transitions across all 5 microservices (`ingestion-service`, `enrichment-service`, `validation-service`, `exception-service`, `workitem-service`).
- Captures status transitions, service names, ISO timestamps, detail messages, actor IDs, and replay flags.

### 10. Distributed Topology & Stream Visualizer
- Interactive architecture diagram displaying all 5 microservices, 10 Kafka topics with live message counters, and CouchDB document store schemas.

### 11. Multi-Role Authorization Simulator
- Instant persona switcher to test role-based views and actions:
  - **Operations Specialist**: Day-to-day exception inspection and data fix workflow.
  - **Supervisor / Lead**: Four-eyes review authorization, queue management, SLA escalations.
  - **System Administrator**: Topology, DLQ replay, and infrastructure monitoring.

---

## 3. Local Development & Build

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
Opens the Vite development server at `http://localhost:5173`.

### Run Linter
```bash
npm run lint
```
Checks rules with 0 errors and 0 warnings using Oxlint.

### Production Build
```bash
npm run build
```
Type-checks with `tsc -b` and builds production bundle in `dist/`.

---

## 4. Docker Deployment
The frontend is containerized using a multi-stage `Dockerfile` and configured with `nginx.conf`:

```bash
docker compose up -d --build frontend
```
The application will be accessible at `http://localhost:3000`.
