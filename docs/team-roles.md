# Team Roles & Work Division

Team size: 6. Suggested roles per the evaluation brief (a person can hold multiple roles):

| Role              | Responsibility   |
|-------------------|------------------|
| Team Lead         | Coordination     |
| Frontend Engineer | UI development   |
| Backend Engineer  | APIs             |
| Data Engineer     | Analytics        |
| QA Engineer       | Testing          |
| DevOps Engineer   | CI/CD            |
| Business Analyst  | Domain expertise |

## Evaluation weighting (for prioritizing effort)

| Category | Weight |
| --- | --- |
| Functional Completeness | 20% |
| Architecture & Design | 15% |
| Engineering Practices | 15% |
| Testing & Quality | 15% |
| Security | 10% |
| AI / Analytics | 10% |
| Documentation | 5% |
| Demonstration & Presentation | 10% |

Note: this is the evaluation-framework version of the weights. Section 15 of the capstone spec gives a second, slightly different weighting (Architecture 20%, Backend 20%, Frontend 15%, Data modelling 15%, Quality engineering 15%, Innovation/communication 15%) - worth reconciling with whoever is grading before finalizing priorities.

## Suggested initial mapping (6 people, 5 services + frontend)

This is a starting proposal - swap names in once roles are actually assigned.

| Person | Primary owns | Secondary role(s) |
| --- | --- | --- |
| Mayank | Team Lead + `ingestion-service` | Coordination, event contracts |
| Bhavik | `enrichment-service` | Business Analyst (reference data modelling) |
| Mahadev | `validation-service` | Backend Engineer |
| Kapish | `exception-service` | Data Engineer (classification/recommendation logic) |
| Oren | `workitem-service` | DevOps Engineer (CI/CD, Docker Compose upkeep) |
| Gunjan | `frontend` | Frontend Engineer + QA Engineer (test coverage across services) |

Things to decide as a team before assigning tasks:

1. Who owns the shared `common-lib` schema (FpML-inspired JSON model) - this needs agreement from everyone since all services depend on it. Suggest doing this as a group design session before anyone starts service-specific work, using `docs/kafka-topics.md` and spec Section 10-11 as the base.
2. Who's the point of contact for merge conflicts / PR reviews (Team Lead's job).
3. Sprint/milestone cadence - map to MVP (Section 8) first, stretch goals
   (Section 9) only after MVP is demoable end-to-end.
