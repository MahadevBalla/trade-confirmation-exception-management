# Running the Website: Live Data and Demo Backup

The website has two modes. The badge at the top of every page shows which one you're in.

| Badge | What you see | Needs the backend? | Use it for |
|---|---|---|---|
| **LIVE** (default) | Real trades from Kafka and CouchDB. Buttons really change things. Needs a sign-in. | Yes | The demo, testing, normal use |
| **DEMO DATA** | A simulation running in your browser, with invented data. No sign-in. | No | A backup if the backend fails |

The two never mix, and the site never switches between them by itself.

## Use it with real data

### 1. Start everything

You need Docker and `jq`. From the repo root:

```bash
cp .env.example .env        # first time only
./scripts/dev-up.sh --all
```

The first build takes several minutes. It finishes by running a smoke test across all the services.

If you have run an older version of this project before, reset first so old data doesn't mix with the new:

```bash
./scripts/dev-down.sh --reset
```

This deletes all your local trades, so don't run it once your demo data is loaded.

### 2. Sign in and load the data

1. Go to **http://localhost:3000** and sign in. For the demo, use `admin`, which can do everything.
2. Check the top bar shows **LIVE**, **Backend: Connected** and your name.
3. Click **Load 500 seed trades** on the banner, **once**.
4. Wait about a minute for the numbers to stop changing.

| Username | Password | Can do |
|---|---|---|
| `ops` | `ops-demo` | View everything, publish trades, load the seed, resolve exceptions, work on work items |
| `supervisor` | `supervisor-demo` | All of that, plus approve or reject manual reviews |
| `admin` | `admin-demo` | All of that, plus replay from the Dead-Letter Queue |

These are demo accounts for synthetic data. To change the passwords, set `TCEM_OPS_PASSWORD`, `TCEM_SUPERVISOR_PASSWORD` and `TCEM_ADMIN_PASSWORD` in `.env` before starting, and export them when running the scripts in `scripts/`.

To show role control, sign in as `ops` and open **Manual Review**: the buttons are disabled, and the screen says why. Your session lasts until you close the tab or click **Sign out**.

You should end up with:

| Measure | Value |
|---|---|
| Total trades | 485 (the seed includes duplicates) |
| Confirmed | 383 (79% STP) |
| Open exceptions and active work items | 102 each |
| Manual review queue | 17 |
| Dead-letter queue | 0 |

> **Only seed once.** Seeding again doesn't add trades, but every message counts as a duplicate and raises a new exception. Work items jump from about 100 to about 400. If this happens, reset and start again (see [Start again from clean](#start-again-from-clean)).

The 79% is below the 85% target on the dashboard because the seed deliberately includes trades with problems.

### 3. What you can do

Screens refresh about every 3 seconds. Every action below changes real data.

| To show | Do this | What happens |
|---|---|---|
| A trade passing straight through | **Publish Clean STP** | Confirmed within a few seconds, with enriched party names |
| A trade with a problem | **Publish Missing Strike** | An exception and a work item appear |
| Resolving a problem | **Exception Queue**, then the tick on a row | Its work item closes and the exception shows `RESOLVED` |
| A human decision | **Ingest Synthetic Batch** > **4. Non-Eligible Counterparty**, then **Manual Review** | Approve creates a confirmation; reject sends the trade to the dead-letter queue |
| A message that can't be processed | **Publish Malformed Message** | A dead-letter entry appears, and it can't be replayed |
| Recovering from a failure | See [Replaying a failure](#replaying-a-failure) | The entry reaches `REPLAY_SUCCEEDED` |
| The full history | **Audit Trail Explorer**, or click any trade | Real events from each service |
| Trades arriving constantly | **Auto Stream** | Publishes a real trade every few seconds until you stop it |

> **Auto Stream adds real trades,** so it changes your numbers. Remember to stop it.

Your signed-in name is what gets recorded against a review, not anything sent by the browser.

### Replaying a failure

A failure that can be replayed needs a real problem to occur, so there's a script to cause one. Run it shortly before you need it:

```bash
./scripts/demo-dlq-failure.sh
```

It briefly removes a database so that one trade fails, then restores the database. Within a few seconds the entry appears on the **Dead-Letter Queue** screen. Signed in as `admin`, click **Replay Message** and it succeeds.

The malformed message button won't work for this, because invalid JSON can never succeed.

### Seeing the backend directly

| Tool | Address | Login |
|---|---|---|
| Kafka UI | http://localhost:8080 | None |
| CouchDB Fauxton | http://localhost:5984/_utils | `admin` / `password`, from `.env` |

### Start again from clean

```bash
./scripts/dev-down.sh --reset
./scripts/dev-up.sh --all
```

Then load the seed once.

## Use demo data as a backup

Demo mode is there so you can still show the screens if the backend fails on the day. It doesn't prove anything about the pipeline, so **tell your audience when you're using it.**

### Switching during a demo

No restart is needed, and it takes effect immediately.

- **If the backend is down:** a red **Backend unreachable** banner appears. Click **Switch to demo data**. If you aren't signed in yet, the login screen says the backend can't be reached. Click **Use demo data instead**.
- **At any other time:** click the **LIVE** badge at the top of the page. It changes to **DEMO DATA**.

To go back, click the **DEMO DATA** badge. If you're still signed in and the backend is healthy, your real data reappears within a few seconds; otherwise sign in first. Switching doesn't affect anything stored in the backend.

### Starting with no backend at all

If the backend won't start, run only the website:

```bash
docker compose up -d --build --no-deps frontend
```

Or, with Node 20+ installed and no Docker:

```bash
cd frontend
npm install
npm run dev      # then open http://localhost:5173
```

The site opens on the login screen. Click **Use demo data instead**.

### How demo mode differs

| | LIVE | DEMO DATA |
|---|---|---|
| Data | Real, from the backend | 10 invented trades, in your browser only |
| Sign-in | Required, and your role limits what you can do | None. The role selector is only for show |
| Buttons | Publish to Kafka and call the APIs | Only change what's on your screen |
| Refreshing the page | Keeps everything | **Loses your changes** and resets to the starting data, but stays in demo mode |
| Other people's screens | Show the same data | See nothing you did |
| "Kafka: Online" and service status | Replaced by real checks | **Invented**, always shown as healthy |
| Manual review | Shows the real reason for review | Shows an illustrative counterparty comparison |
| Kafka UI and CouchDB | Show the same trades | Show nothing from the simulation |

So in demo mode, don't open Kafka UI or Fauxton to back up what's on screen, because the two won't match.

The site remembers your choice per browser. If someone finds it showing demo data unexpectedly, they only need to click the badge.

## Demo day checklist

**Before you start:**

- [ ] `./scripts/dev-down.sh --reset`, then `./scripts/dev-up.sh --all`
- [ ] Load the seed **once**, and confirm the numbers match the table above
- [ ] Signed in as `admin`, and the badge says **LIVE**, in the browser you'll present from
- [ ] Publish one clean trade and see it confirmed
- [ ] Decide who makes the call to switch to demo data, and what they'll say

**During the demo:**

- [ ] Run `./scripts/demo-dlq-failure.sh` just before showing replay
- [ ] Stop **Auto Stream** if you started it

**If something fails:** give it about 30 seconds, because the site retries on its own and recovers without a reload. If it doesn't, click **Switch to demo data** and say that you're now showing the simulation.

## Troubleshooting

| What you see | What it means | What to do |
|---|---|---|
| **Backend unreachable** | No service is responding | Run `docker compose ps`, then `./scripts/dev-up.sh --all`. Use demo data if you can't wait. |
| **Some data is out of date** | One service is down. The rest still works, and the banner lists what couldn't refresh. | The **Event Topology Map** shows which service. Run `docker compose logs <service>`, then `docker compose restart <service>`. |
| **Wrong username or password** | The credentials don't match | Check the accounts above, or the values in `.env` if they were changed |
| The login says the backend cannot be reached | The sign-in check got no answer | Start the backend, or click **Use demo data instead** |
| Approve, reject or replay is greyed out | Your role can't do that | Sign out, then sign in as `supervisor` (reviews) or `admin` (replay) |
| **No trades yet** | The system is working, but it's empty | Click **Load 500 seed trades** |
| Work items are in the hundreds | The seed was run more than once | Start again from clean |
| Numbers keep rising | **Auto Stream** is on, or the seed is still flowing | Stop it, or wait about a minute |
| Trades show demo IDs such as `TRD-10001` | You're in demo mode | Click the **DEMO DATA** badge |
| A trade isn't confirmed straight away | Screens refresh about every 3 seconds | Wait a few seconds, or check its Audit History |
| `dev-up.sh` fails with "port is already allocated" | Something else is using a port (3000, 5984, 8080-8085 or 9092) | Stop that program, then try again |
| Replay button is missing | That entry can't be replayed, such as a malformed message or a rejected review | Use `./scripts/demo-dlq-failure.sh` to create one that can |

## Related

- [`reports/live_demo_presentation_guide.md`](reports/live_demo_presentation_guide.md): the presentation script
- [`../scripts/README.md`](../scripts/README.md): all the scripts
- [`../frontend/README.md`](../frontend/README.md): how the two modes are built
