# Phase 4.5 — Acceptance Tests (bravoscribe-acceptance-tests) Implementation Plan

## Context

Phases 1–4 are complete and verified (Infrastructure, User Service, Journal Service,
Notification Service). This module is Level 3 of the three-level testing strategy
(SYSTEM_SPEC.md §4.1) — Cucumber + Rest Assured scenarios that spin up all three
services via Testcontainers and assert business behavior that spans service boundaries.

It is not a new phase in the original 8-phase plan, but the CI pipeline
(`.github/workflows/ci.yml`) already gates an `acceptance-tests` job on all three
services existing — which is now true. This module closes that gap.

---

## Readiness Check — We Are Good to Go ✅

| Check | Status | Notes |
|---|---|---|
| Spec complete | ✅ | `SYSTEM_SPEC.md` §4.1 — Level 3 section |
| Feature files | ✅ | 4 files already committed under `src/test/resources/features/` |
| `pom.xml` | ✅ | Cucumber, Rest Assured, Testcontainers (postgres, kafka), JUnit 5 all present |
| Prerequisite services | ✅ | User, Journal, Notification services all implemented with Dockerfiles |
| CI wiring | ✅ | `acceptance-tests` job already exists in `ci.yml`, gated correctly |
| `mvnw` / `.mvn/` wrapper | ⚠️ | Missing — CI step runs `./mvnw test` in this directory, will fail as-is |
| `CucumberRunner.java` | ⚠️ | Missing |
| `config/ServiceConfig.java` | ⚠️ | Missing — only `.gitkeep` |
| Step definitions | ⚠️ | Missing — only `.gitkeep` |
| SendGrid stub strategy | ⚠️ | Not resolved — needs WireMock (see Design Decisions) |

---

## Design Decisions (resolved before implementation)

### D1 — Image tags for Testcontainers: match CI, not the ACR path in SYSTEM_SPEC.md

`SYSTEM_SPEC.md`'s `ServiceConfig.java` example pulls
`journalacr.azurecr.io/user-service:test` — that's the cloud/ACR convention, not
available locally or in CI. `docker-compose.yml` doesn't tag app images at all
(`build:` with no `image:` key). The CI job already builds:

```
bravoscribe/user-service:test
bravoscribe/journal-service:test
bravoscribe/notification-service:test
```

**Decision:** `ServiceConfig.java` targets these exact tags. Local runs require the
same `docker build -t bravoscribe/<svc>:test services/<svc>` commands the README
should document (currently only says "build service images", no exact command).

### D2 — Notification assertions: WireMock stub for SendGrid, assert via MongoDB

`SendGridConfig.java` currently hardcodes `.baseUrl("https://api.sendgrid.com")`
(services/notification-service/.../config/SendGridConfig.java:24) — no property, no
override hook. Using a real SendGrid account in CI is unnecessary and flaky.

**Decision:**
1. Externalize the notification-service base URL:
   `@Value("${notification.sendgrid.base-url:https://api.sendgrid.com}")` —
   one-line change, default unchanged, zero regression risk to the existing
   15/15 green tests.
2. In this module, run a WireMock container (network alias `wiremock`), stub
   `POST /v3/mail/send` → `202` with a fake `X-Message-Id` header.
3. Point the notification-service container at it via
   `NOTIFICATION_SENDGRID_BASE_URL=http://wiremock:8080`.
4. Assert success by querying the `email_logs` MongoDB collection for
   `status: "sent"` — the same signal `NotificationConsumerIT` already uses
   internally, not a new pattern.

**Scope note:** step D2.1 (the `@Value` change) touches notification-service, not
just this module. Call this out explicitly when implementing — it's a one-line,
backward-compatible diff, but it's outside `bravoscribe-acceptance-tests/`.

---

## What's New vs. Per-Service Test Modules (key differences)

| Aspect | Per-service Level 2 tests | This module (Level 3) |
|---|---|---|
| Scope | One service, real DB/Kafka via Testcontainers | All 3 services as black-box containers |
| What's tested | HTTP + DB behavior of one service | Business flows spanning service boundaries |
| Containers started | Postgres/Kafka/Mongo/Redis only | + user-service, journal-service, notification-service, wiremock images |
| App code under test | Runs in-process (Spring context) | Runs as packaged Docker images — no Spring context in this module |
| Auth | N/A or direct service calls | Full JWT issuance/verification round-trip via real HTTP |
| Runs | Every commit | Before merge to main (slower, full stack) |

---

## Package Structure

```
bravoscribe-acceptance-tests/
├── mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties   ← copy from journal-service
├── pom.xml                                                  ← already present
└── src/test/
    ├── java/com/bravoscribe/acceptance/
    │   ├── CucumberRunner.java
    │   ├── config/
    │   │   └── ServiceConfig.java       Testcontainers orchestration — postgres,
    │   │                                kafka, mongo, redis, wiremock, all 3 services
    │   └── steps/
    │       ├── UserSteps.java           registration, login, deactivation, password reset
    │       ├── JournalSteps.java        entry creation, stats, export
    │       ├── NotificationSteps.java   email_logs assertions (welcome/reset/confirmation)
    │       └── CommonSteps.java         shared auth header handling, generic status assertions
    └── resources/features/              ← already present, unchanged
        ├── user-journal-flow.feature
        ├── account-lifecycle.feature
        ├── security.feature
        └── export-flow.feature
```

---

## Implementation Order

### 1. Maven wrapper
Copy `mvnw`, `mvnw.cmd`, `.mvn/` from journal-service (same pattern as Phase 3/4).
**Critical:** immediately run `git update-index --chmod=+x bravoscribe-acceptance-tests/mvnw`
— missed once before on journal-service's mvnw (see commit `f801d54`).

### 2. `CucumberRunner.java`
Same shape as `services/journal-service/.../api/CucumberRunner.java`:
```java
package com.bravoscribe.acceptance;

import org.junit.platform.suite.api.*;
import static io.cucumber.junit.platform.engine.Constants.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber-reports/cucumber.json")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.bravoscribe.acceptance")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/resources/features")
public class CucumberRunner {}
```

### 3. `config/ServiceConfig.java` — Testcontainers orchestration

Containers needed (per D1/D2 above and actual env var contracts read from each
service's `application.yml`):

```java
@TestConfiguration
public class ServiceConfig {

    static Network network = Network.newNetwork();

    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("journal")            // matches infra/docker-compose.yml
            .withInitScript("init-schemas.sql")      // creates users/journal schemas + scoped users
            .withNetwork(network).withNetworkAliases("postgres");

    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"))
            .withNetwork(network).withNetworkAliases("kafka");

    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7")
            .withNetwork(network).withNetworkAliases("redis");

    static MongoDBContainer mongo =
        new MongoDBContainer("mongo:7")
            .withNetwork(network).withNetworkAliases("mongo");

    static GenericContainer<?> wiremock =
        new GenericContainer<>("wiremock/wiremock:3.9.1")
            .withNetwork(network).withNetworkAliases("wiremock")
            .withExposedPorts(8080);
            // stub mappings for POST /v3/mail/send loaded from classpath resources

    static GenericContainer<?> userService =
        new GenericContainer<>("bravoscribe/user-service:test")
            .withNetwork(network).withNetworkAliases("user-service")
            .withExposedPorts(8081)
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/journal?currentSchema=users")
            .withEnv("SPRING_DATASOURCE_USERNAME", "user_svc")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "user_svc_password")
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
            .withEnv("JWT_PRIVATE_KEY", TEST_JWT_PRIVATE_KEY)   // generated test-only RSA pair
            .withEnv("JWT_PUBLIC_KEY", TEST_JWT_PUBLIC_KEY)
            .dependsOn(postgres, kafka);

    static GenericContainer<?> journalService =
        new GenericContainer<>("bravoscribe/journal-service:test")
            .withNetwork(network).withNetworkAliases("journal-service")
            .withExposedPorts(8082)
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/journal?currentSchema=journal")
            .withEnv("SPRING_DATASOURCE_USERNAME", "journal_svc")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "journal_svc_password")
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
            .withEnv("REDIS_URL", "redis://redis:6379")
            .withEnv("JWT_PUBLIC_KEY", TEST_JWT_PUBLIC_KEY)     // verify-only, no private key
            .dependsOn(postgres, kafka, redis);

    static GenericContainer<?> notificationService =
        new GenericContainer<>("bravoscribe/notification-service:test")
            .withNetwork(network).withNetworkAliases("notification-service")
            .withExposedPorts(8083)
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
            .withEnv("REDIS_URL", "redis://redis:6379")
            .withEnv("MONGODB_URI", "mongodb://mongo:mongo@mongo:27017/bravoscribe?authSource=admin")
            .withEnv("NOTIFICATION_SENDGRID_BASE_URL", "http://wiremock:8080")   // D2
            .withEnv("SENDGRID_API_KEY", "test-key")
            .dependsOn(kafka, redis, mongo, wiremock);

    static {
        Startables.deepStart(postgres, kafka, redis, mongo, wiremock).join();
        Startables.deepStart(userService, journalService, notificationService).join();
    }
}
```

**Open item to confirm during implementation:** whether Mongo's shared auth DB name
(`bravoscribe`) needs a root-user init script analogous to `init-schemas.sql`, or
whether `MongoDBContainer`'s default credentials are sufficient — check against
notification-service's own `NotificationConsumerIT` setup first, reuse whatever it
already does rather than inventing a new Mongo bootstrap.

### 4. Step definitions

| File | Responsibilities |
|---|---|
| `CommonSteps.java` | `Given all services are running` (health-check polling), shared JWT/bearer header state, generic `Then I receive status {int}` |
| `UserSteps.java` | Register, login, deactivate (as admin), password reset request/confirm — calls `user-service` REST API directly via Rest Assured |
| `JournalSteps.java` | Create entry, fetch stats, export, soft-delete assertions against Postgres `journal` schema |
| `NotificationSteps.java` | Poll `email_logs` in MongoDB for a matching `status: "sent"` document within a timeout (scenarios use "within 5 seconds" language already) |

Reuse the Rest Assured + JWT patterns already proven in
`services/user-service/.../api/steps/` and `services/journal-service/.../api/steps/`
rather than reinventing auth handling.

### 5. Notification-service change (D2.1)

Single-line diff in `services/notification-service/.../config/SendGridConfig.java`:
```java
@Value("${notification.sendgrid.base-url:https://api.sendgrid.com}")
private String baseUrl;

@Bean
public RestClient sendGridClient() {
    return RestClient.builder()
        .baseUrl(baseUrl)
        ...
```
Run notification-service's own `mvnw clean verify` afterward to confirm the existing
15/15 tests are still green (default value unchanged, so they should be unaffected).

---

## Verification (Definition of Done)

1. `bravoscribe-acceptance-tests/mvnw` executable, wrapper present
2. Prerequisite: `docker build -t bravoscribe/<svc>:test services/<svc>` for all 3 services
3. `mvn test -pl bravoscribe-acceptance-tests` (or `./mvnw test` from within the module)
   starts all containers and runs all 4 feature files
4. All scenarios in `user-journal-flow.feature`, `account-lifecycle.feature`,
   `security.feature`, `export-flow.feature` pass
5. `account-lifecycle.feature`'s "sends a welcome email" scenario passes via the
   WireMock + `email_logs` assertion path — no real SendGrid account or network
   call involved
6. `notification-service`'s own `mvnw clean verify` still shows 15/15 green after
   the `SendGridConfig` change
7. CI: push a branch, confirm the `acceptance-tests` job in `ci.yml` goes green
   end-to-end (currently untestable — this is the first time all 3 services +
   this module exist together)
