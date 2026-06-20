# bravoscribe-acceptance-tests

Level 3 cross-service acceptance tests for Bravoscribe.

## What this module tests

Business scenarios that span multiple services — User Service + Journal Service.
These tests require all services to be running via Testcontainers.

## How to run

```bash
# From repo root — requires Docker
mvn test -pl bravoscribe-acceptance-tests

# Run a specific feature
mvn test -pl bravoscribe-acceptance-tests -Dcucumber.filter.tags="@smoke"
```

## Prerequisites

- Docker running locally
- Service images built: `mvn package -DskipTests` in each service
- Or pulled from ACR: `docker pull journalacr.azurecr.io/user-service:test`

## Structure

```
src/test/
├── java/com/bravoscribe/acceptance/
│   ├── config/
│   │   └── ServiceConfig.java     ← Testcontainers orchestration
│   └── steps/
│       ├── UserSteps.java         ← User Service step definitions
│       ├── JournalSteps.java      ← Journal Service step definitions
│       └── CommonSteps.java       ← Shared steps (auth, assertions)
└── resources/features/
    ├── user-journal-flow.feature  ← Registration + journaling scenarios
    ├── account-lifecycle.feature  ← Deactivation, password reset effects
    ├── security.feature           ← Cross-service security scenarios
    └── export-flow.feature        ← Export zip creation, date range, empty 404
```

## See also

- `SYSTEM_SPEC.md` section 4.1 — full testing strategy
- `services/user-service/SPEC.md` — Level 2 API tests for User Service
- `services/journal-service/SPEC.md` — Level 2 API tests for Journal Service
