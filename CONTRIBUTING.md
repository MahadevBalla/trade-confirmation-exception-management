# Contributing / Git Workflow

Team of 6. Task ownership, service boundaries, and implementation status are documented in [`docs/project-status.md`](./docs/project-status.md).

## Branching

- `main` is always deployable/demo-able. Never commit directly to it.
- One feature branch per task, cut from `main`:

  ```txt
  git checkout main
  git pull
  git checkout -b feat/<short-description>
  # e.g. feat/enrichment-service-couchdb-client
  # e.g. feat/frontend-exception-queue-view
  # e.g. fix/validation-fingerprint-deduplication
  ```

- Use descriptive, task-oriented branch names. Recommended prefixes are `feat/`, `fix/`, `test/`, `refactor/`, `docs/`, and `chore/`, depending on the type of work.
- Keep branches scoped to one service or one concern where possible - it makes review easier and avoids stepping on teammates working in other services.

## Commits

- Small, focused commits with clear messages. Follow commit conventions:

  ```txt
  feat(ingestion): add trade.raw consumer
  feat(validation): implement STP rules
  fix(validation): suppress duplicate confirmations
  test(validation): cover confirmation fingerprinting
  docs: update Kafka topic list
  chore(deps): bump spring-kafka version
  ```

## Pull Requests

1. Push your branch and open a PR against `main`.
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
