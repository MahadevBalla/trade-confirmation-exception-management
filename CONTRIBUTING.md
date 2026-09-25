# Contributing / Git Workflow

Team of 6, no task assignments yet - this is the process to follow once they exist.

## Branching

- `main` is always deployable/demo-able. Never commit directly to it.
- One feature branch per task, cut from `main`:

  ```txt
  git checkout main
  git pull
  git checkout -b <yourname>/<short-description>
  # e.g. priya/enrichment-service-couchdb-client
  # e.g. rahul/frontend-exception-queue-view
  ```

- Keep branches scoped to one service or one concern where possible - it makes review easier and avoids stepping on teammates working in other services.

## Commits

- Small, focused commits with a clear message. Conventional-commit style is encouraged but not enforced:

  ```txt
  feat(ingestion): add trade.raw consumer skeleton
  fix(frontend): correct exception queue empty state
  docs: update kafka topic list
  chore(deps): bump spring-kafka version
  ```

## Pull Requests

1. Push your branch, open a PR against `main`.
2. Fill in the PR template (`.github/PULL_REQUEST_TEMPLATE.md`).
3. At least one other team member reviews before merge - doesn't have to be the Team Lead for routine changes, but cross-service changes (anything touching `common-lib` or `docker-compose.yml`) should get the Team Lead's eyes.
4. CI (`.github/workflows/`) must pass - backend reactor build for Java changes, frontend build+lint for UI changes.
5. Squash-merge or regular merge, either is fine - just keep `main`'s history readable. Delete the branch after merging.

## Working across services safely

- Each backend service is its own Maven module under `services/`, sharing one parent reactor (`services/pom.xml`) and one shared library (`services/common-lib`). If your task doesn't touch `common-lib`, you generally won't get merge conflicts with someone else's service.
- Changes to `common-lib` (shared DTOs, event contracts, enums) affect everyone - propose these in a team discussion first, not silently in a feature branch, since every service will need to update in lockstep.
- Changes to `docker-compose.yml` (new topics, new env vars, new services) should be called out in the PR description so everyone knows to `docker compose up -d --build` again locally.

## Definition of done (per task)

Loosely mapped to the evaluation framework so nothing falls through:

- [ ] Code builds and the relevant service's tests pass locally
- [ ] Added/updated unit tests for new logic (validation, classification, etc.)
- [ ] No hard-coded secrets/credentials (`.env` only, never committed)
- [ ] Docs updated if you changed a topic, an API shape, or a service boundary
- [ ] PR description explains *what* and *why*, not just *what changed*
