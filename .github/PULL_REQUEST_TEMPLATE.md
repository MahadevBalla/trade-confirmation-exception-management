## What does this PR do?

<!-- One or two sentences. Which service(s)/screen(s) does this touch? -->

## Why

<!-- Link to the task/issue if there is one. -->

## How to verify

<!-- Steps for the reviewer: which service to run, what endpoint/screen to check. -->

## Checklist

- [ ] Builds locally (`mvn -f services/pom.xml verify` and/or `npm run build` in `frontend/`)
- [ ] Tests added/updated where it makes sense
- [ ] No secrets committed (check `.env` isn't staged)
- [ ] Docs (`docs/`, `README.md`) updated if this changes topics, APIs, or service boundaries
- [ ] Does NOT touch `common-lib` or `docker-compose.yml` without a heads-up to the team

## Notes for reviewers

<!-- Anything you're unsure about, or specific areas you want a second look on. -->
