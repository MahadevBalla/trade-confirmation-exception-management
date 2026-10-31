# TCEM Presentation Readiness & Urgent Fixes Plan

**Author:** Mayank Chauhan (Team Lead)  
**Target Deadline:** 3 Days to Presentation / Evaluation  
**Status:** ✅ ALL 7 URGENT FIXES COMPLETED & COMMITTED (Branch `fix/code-consistency`)  
**Live Demo Guide:** [`live_demo_presentation_guide.md`](./live_demo_presentation_guide.md)  
**Related Report:** [`code_consistency_report.md`](./code_consistency_report.md)  

---

## 1. Executive Strategy: The 80/20 Rule for 3 Days

The TCEM multi-phase consistency audit identified **37 total architectural and code consistency issues**. 

**You do NOT need to fix all 37 issues to deliver a flawless presentation.**

Attempting to resolve all 37 items in 72 hours—especially large-scale rewrites like connecting the entire React frontend to 15+ live microservice REST endpoints—carries massive regression risk. Instead:
- **7 Quick Fixes (P0/P1)** resolve all container build breakers, Kafka data wipes, metric caps, and UI runtime crashes in **under 1 hour**.
- **A Decoupled Demo Strategy** showcases the rich React Operations Cockpit alongside live Kafka/CouchDB pipeline streaming without fragile point-to-point couplings.
- **The Remaining 30 Issues** are presented proactively on your **"Production Hardening Roadmap"** slide, demonstrating senior engineering foresight.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        3-DAY ACTION TIMELINE                           │
│                                                                        │
│  DAY 1: Apply 7 Must-Fix Code & Config Changes (~45–60 mins)           │
│  DAY 2: Rehearse the Golden Demo Script (Docker up + Seed + Kafka UI)  │
│  DAY 3: Finalize Slide Deck & Q&A Defense Talking Points               │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. The 7 "Must-Fix" Showstoppers (Total Fix Time: ~45–60 Mins)

These 7 items are strictly required to ensure that containers build cleanly, the system doesn't lose state on restarts, and the UI doesn't crash or display blatantly wrong numbers during the presentation.

---

### Fix 1: Add `frontend/.dockerignore` (Audit Issue C5-01) — ✅ [COMPLETED: `ef23e52`]
* **Severity:** 🔴 Critical / Blocker
* **File to Create:** `frontend/.dockerignore`
* **Why It's Urgent:** On macOS developer machines, `npm run build` or `npm install` creates Darwin ARM64 native binaries in `frontend/node_modules`. Without `.dockerignore`, `COPY . .` in `frontend/Dockerfile` copies host binaries into the Alpine Linux container, causing `docker compose up --build` or `./scripts/dev-up.sh` to fail with `exec format error`.
* **Action:** Create `frontend/.dockerignore` with the following content:

```dockerignore
node_modules
dist
.env*
.git
```

---

### Fix 2: Mount Kafka Data Volume in Docker Compose (Audit Issue C5-02) — ✅ [COMPLETED: `e181b58`]
* **Severity:** 🔴 Critical / State Corruption
* **File to Edit:** `docker-compose.yml`
* **Why It's Urgent:** CouchDB persists its state in `couchdb_data`, but Kafka currently has no volume. Whenever containers are restarted, Kafka wipes all topic offsets, while CouchDB retains previous trades and idempotency keys. This causes pipeline desynchronization and consumer stalls.
* **Action:**
1. Under the `kafka:` service block (around line 35), add a volume mount:
```yaml
  kafka:
    image: apache/kafka:3.8.0
    container_name: tcem-kafka
    volumes:
      - kafka_data:/var/lib/kafka/data
    networks:
      - tcem-network
```
2. At the very bottom of `docker-compose.yml`, declare the named volume:
```yaml
volumes:
  couchdb_data:
  kafka_data:
```

---

### Fix 3: Fix Confirmation Metrics Cap of 25 (Audit Issue C2-02) — ✅ [COMPLETED: `2c4b3aa`]
* **Severity:** 🔴 Critical / High Visibility Defect
* **File to Edit:** `services/validation-service/src/main/java/com/tcem/validation/repository/ConfirmationCouchDbRepository.java`
* **Why It's Urgent:** CouchDB Mango `_find` queries default to a limit of 25 records if unspecified. Calling `.size()` on the returned list means `GET /metrics/stp-rate` and dashboard counters will never exceed 25, even if 500 trades were ingested.
* **Action:** In `ConfirmationCouchDbRepository.java:L97-L102`, pass an explicit limit of `10000`:
```java
    /**
     * Counts total confirmations — used by metrics endpoint.
     */
    public long countAll() {
        Map<String, Object> selector = new HashMap<>();
        selector.put("docType", "CONFIRMATION");
        FindQueryRequest query = new FindQueryRequest(selector, 10000, null);
        return couchDb.find(database, query, ConfirmationDocument.class).size();
    }
```

---

### Fix 4: Align TypeScript Enums with Backend (Audit Issue C1-02) — ✅ [COMPLETED: `c43263d`]
* **Severity:** 🔴 Critical / UI Filtering Failure
* **File to Edit:** `frontend/src/types/tcem.ts`
* **Why It's Urgent:** Backend microservices produce `IRS`, `NOVATION`, and `COMPRESSION`. Frontend only defined `IR_SWAP` and lacked the event types, causing filter mismatches and deserialization gaps.
* **Action:** In `frontend/src/types/tcem.ts:L10-L17`, update `ProductType` and `EventType`:
```typescript
export type ProductType = 'FX_OPTION' | 'EQUITY_SWAP' | 'IR_SWAP' | 'IRS' | 'FX_FORWARD' | 'UNKNOWN';

export type EventType =
  | 'NEW_TRADE'
  | 'AMEND_TRADE'
  | 'CANCEL_TRADE'
  | 'REPLAY'
  | 'NOVATION'
  | 'COMPRESSION'
  | 'HEARTBEAT';
```

---

### Fix 5: Support Dual ID/Status Field Names on DLQ Entries (Audit Issue C4-02) — ✅ [COMPLETED: `370fa98`]
* **Severity:** 🔴 Critical / UI Runtime Crash
* **File to Edit:** `frontend/src/types/tcem.ts`
* **Why It's Urgent:** Backend `DlqController` serializes fields as `id` and `dlqStatus`. Frontend `DeadLetterEvent` expected `dlqId` and `status`. Navigating to the DLQ tab or triggering a replay will pass `undefined` and throw an error.
* **Action:** In `frontend/src/types/tcem.ts:L279-L294`, add the backend field aliases:
```typescript
export interface DeadLetterEvent {
  id?: string;
  dlqId?: string;
  dlqStatus?: string;
  status?: 'PENDING_REPLAY' | 'REPLAYING' | 'REPLAY_SUCCEEDED' | 'PERMANENTLY_FAILED';
  messageHeader: MessageHeader;
  originalMessageHeader: MessageHeader;
  originalPayload: string;
  failedOnTopic: string;
  failedInService: string;
  failureReason: string;
  exceptionClass?: string;
  stackTraceExcerpt?: string;
  failedAt: string;
  replayCount: number;
  replayable: boolean;
  tradeId: string;
}
```

---

### Fix 6: Display Enriched Counterparty Legal Entity Name (Audit Issue C4-03) — ✅ [COMPLETED: `d00ccb6`]
* **Severity:** 🟠 High / Demo Quality
* **File to Edit:** `frontend/src/components/trades/TradeDetailModal.tsx`
* **Why It's Urgent:** Reference Data Enrichment (FR4) is a core selling point of the platform. However, the trade detail modal was reading `p.partyName`, causing it to fall back to raw party IDs like `PTY-CP-101` instead of `Goldman Sachs International`.
* **Action:** In `TradeDetailModal.tsx:L146`, read `p.legalEntityName`:
```tsx
<span className="font-semibold text-slate-200">{(p as any).legalEntityName || p.partyName || p.partyId}</span>
```

---

### Fix 7: Provision `enrichment-idempotency` Database (Audit Issues C3-06 & C5-07) — ✅ [COMPLETED: `d30e283`]
* **Severity:** 🟡 Low-Medium / Startup Warning
* **Files to Edit:** `scripts/init-couchdb.sh` and `docker-compose.yml`
* **Why It's Urgent:** `enrichment-service` utilizes an isolated `enrichment-idempotency` CouchDB database. If missing, startup logs throw warnings or require elevated auto-create permissions.
* **Action:**
1. In `scripts/init-couchdb.sh:L19-L27`, add `"enrichment-idempotency"` to `APP_DBS`:
```bash
APP_DBS=(
  "trades"
  "confirmations"
  "exceptions"
  "work-items"
  "audit-events"
  "stp-metrics"
  "dlq-entries"
  "enrichment-idempotency"
)
```
2. In `docker-compose.yml:L170`, add the database to the `couchdb-init` container:
```bash
provision_db "enrichment-idempotency"
```

---

## 3. The 3-Day Presentation Strategy (Decoupled Demo Architecture)

### Why You Should NOT Rewire the Frontend Right Now (Issue C4-01)
The frontend (`AppContext.tsx`) currently runs with rich, pre-loaded mock simulations (`INITIAL_TRADES`, `INITIAL_EXCEPTIONS`, live-event streaming ticker, SLA countdowns, and rule evaluation). 

Attempting to replace this with 15+ live REST hooks in 3 days introduces risk of:
- CORS or proxy gateway routing failures
- Asynchronous race conditions breaking table re-renders
- Empty screens if CouchDB has no seeded data during the demo

### The Winning Two-Tier Demo Flow:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        THE PRESENTATION WORKFLOW                       │
├──────────────────────────────────────┬─────────────────────────────────┤
│   TIER 1: OPERATIONS COCKPIT (UI)    │   TIER 2: STREAMING BACKEND     │
│   Host: http://localhost:3000        │   Host: http://localhost:8080   │
├──────────────────────────────────────┼─────────────────────────────────┤
│ • Showcase high-level dashboards     │ • Open Kafka UI (8080)          │
│ • Demonstrate exception queues & SLA │ • Run `./scripts/seed-data.sh`  │
│ • Walk through trade detail modals   │ • Show events crossing topics:  │
│ • Review Rule Engine recommendations │   raw -> accepted -> enriched   │
│ • Inspect Service Topology visualizer│   -> validated -> confirmation  │
│                                      │ • Inspect live CouchDB document │
└──────────────────────────────────────┴─────────────────────────────────┘
```

**How to Explain This to Evaluators:**
> *"We implemented a decoupled, contract-first design. The React frontend features an interactive operational harness allowing operations specialists to validate UX, exception triage, and recommendation workflows. Simultaneously, our Spring Boot microservices run a live, fully reactive Kafka event pipeline. The Nginx API gateway proxies are already in place for full synchronization in our hardening phase."*

---

## 4. The Golden Demo Script (Step-by-Step)

Follow this exact sequence during your live demonstration:

### Step 1: Boot the Entire Stack Cleanly
```bash
./scripts/dev-up.sh --all
```
Verify that all 5 backend microservices, CouchDB, Kafka, Kafka UI, and Frontend are healthy.

### Step 2: Showcase the Frontend Operations Experience
1. Open `http://localhost:3000`.
2. **Dashboard Overview:** Highlight the STP Rate widget, active exceptions counter, and SLA countdown timers.
3. **Trades View:** Open a trade modal (e.g. `TRD-10001`) and show the enriched counterparty legal entity names and economics.
4. **Exception Queue:** Demonstrate filtering by exception category (`ECONOMIC_MISMATCH`, `INVALID_LEI`) and owner queue.
5. **Rule Engine Recommendations:** Click on an exception to reveal the deterministic recommendation cards (confidence score, suggested remediation, difficulty rating).
6. **Service Topology:** Navigate to the topology map to show microservice boundaries and topic connections.

### Step 3: Demonstrate the Live Distributed Event Pipeline
1. Open **Kafka UI** at `http://localhost:8080` in a browser tab.
2. In your terminal, run:
   ```bash
   ./scripts/seed-data.sh --generate 5
   ```
3. In Kafka UI, click on the topics to show real-time message flow:
   - `trade.raw` (5 messages received)
   - `trade.accepted` (5 messages validated by ingestion)
   - `trade.enriched` (5 messages enriched with party data)
   - `stp.validated` (STP evaluation results)
   - `confirmation.generated` (Legal confirmation documents produced)
4. Open **CouchDB Fauxton** at `http://localhost:5984/_utils` and show documents in `confirmations` and `trades`.

---

## 5. Safe Demo Guardrails (What to Avoid Clicking Live)

To prevent triggering unresolved edge cases during your 15-minute presentation:

| Action | Safe Demo Behavior ✅ | Pitfall to Avoid ❌ | Reason |
| :--- | :--- | :--- | :--- |
| **Trade Ingestion** | Ingest clean new trades (`--generate 5`). | Do not ingest `CANCEL_TRADE` events. | Validation service drops cancels silently without closing work items (C2-04). |
| **Trade Types** | Showcase FX Options and Equity Swaps. | Do not demo trade amendments with counterparty changes. | `ConfirmationFingerprint` party predicate bug (C1-01). |
| **Manual Review** | Walk through the Manual Review Queue UI and recommendation cards. | Do not try to show confirmation output from manual review in backend. | `validation-service` does not yet consume `workitem.linked` (C3-01). |
| **DLQ Management** | Show the DLQ screen, payload preview, and stack traces. | Do not click replay multiple times consecutively on the same item. | Multi-attempt replays (> 1) share idempotency keys (C3-03). |

---

## 6. How to Defend Remaining Findings in Q&A (Roadmap Slide)

Include a **"System Audit & Sprint 2 Hardening Roadmap"** slide in your presentation. Presenting these points proactively turns audit findings into proof of senior architectural maturity:

1. **End-to-End Replay Header Propagation:**
   - *"Our comprehensive architecture audit identified that replay headers must be strictly copied through all downstream event envelopes to achieve zero-touch DLQ item auto-closure."*
2. **Manual Review Loop Completion:**
   - *"In Sprint 2, a dedicated consumer in validation-service will subscribe to `workitem.linked` to issue legally binding confirmations upon human approval."*
3. **Global Idempotency Key Standardization:**
   - *"We are upgrading our per-service idempotency stores into a unified key structure that includes the replay count, enabling infinite idempotent replay retries."*
4. **Schema Version Evolution:**
   - *"We are expanding the SchemaVersionRouter pattern established in enrichment-service to all downstream consumers to support seamless backward compatibility for older payload revisions."*
