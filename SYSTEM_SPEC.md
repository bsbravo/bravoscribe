# System Specification — Bravoscribe

> **Version:** 2.0.0
> **Last updated:** 2026-06-04
> **Purpose:** Authoritative specification for the whole system. Provide this file to any AI or developer to understand, regenerate, or extend the system. For deep details on each service, see the `SPEC.md` inside each service folder.

---

## 1. System Overview

**Name:** Bravoscribe
**Description:** A personal journaling application where authenticated users can write a journal entry for the current day and edit entries from previous days. Each entry belongs to one user and is private.
**Primary users:** End users via React web app and Android mobile app. Angular back-office for admin management.
**Business domain:** Personal productivity / journaling

---

## 2. Learning Goals

> **Important for AI assistants:** This project is intentionally built with a specific technology stack for learning purposes. Do NOT suggest consolidating, replacing, or removing any technology listed below — even if a simpler alternative exists. Every technology is here on purpose.

| Technology | Learning goal |
|---|---|
| **Angular** | Build a real Angular app with NgRx, lazy loading, and Angular Material |
| **React** | Build a real React app with Zustand, TanStack Query, and shadcn/ui |
| **Android (Jetpack Compose)** | Build a native Android app with MVVM, Hilt, Room, and Retrofit |
| **Azure** | Learn cloud deployment: App Service, PostgreSQL, Redis, Event Hubs, Key Vault, APIM, Static Web Apps (with built-in CDN) |
| **AI** | Use AI tools (Claude, Claude Code, Copilot) at every layer for code generation, architecture, and review |
| **Java 25** | Use the latest Java LTS features (records, sealed classes, pattern matching, virtual threads) |
| **Spring Boot** | Build production-grade microservices with Spring Boot 4.1 and Spring Security 7 |

### What this means in practice
- React and Angular are **both kept** even though one app could handle both — they serve different learning goals
- Android is kept even though the web app covers the same features — it is a deliberate native mobile learning exercise
- Azure-specific services are used over generic ones (e.g. Event Hubs over self-hosted Kafka) to learn the Azure ecosystem
- Java 25 features should be used where appropriate — records for DTOs, sealed classes for domain errors, virtual threads for I/O

---

## 3. Architecture

### 3.1 Style
- **Pattern:** Microservices
- **Sync communication:** REST/JSON over HTTP — OpenAPI 3.1 spec per service
- **Async communication:** Azure Event Hubs (Kafka-compatible API) for events between services
- **Auth:** JWT RS256 — issued by User Service, verified by each Spring Boot service (local) and APIM (cloud)

### 3.2 Components

```
LOCAL DEV
┌─────────────────────────────────────────────────────────────────┐
│                            Clients                              │
│   React (Web)      │  Angular (Back-office)  │   Android        │
│   (end users)      │  (admins)               │   (end users)    │
└────────┬───────────┴───────────┬─────────────┴──────┬───────────┘
         └─────────────────────▼────────────────────┘
                      nginx (localhost:8080)
                 JWT validation · Routing · CORS
         ┌────────────────┬───────────────────────────┐
         ▼                ▼                           ▼
   User Service     Journal Service           Notification
   (Auth · Users)   (Entries · Tags)            Service
         │                │                    │        │
     PostgreSQL       PostgreSQL            Redis     Redis
     (users)          (journal)           (cache)  (idempotency)
         ▲                ▲
         │                │
    Angular only      React + Android only
    (user mgmt)       (journal entries)

                 └──────────┬────────────┘
                       Kafka (local)

CLOUD (dev / staging / production)
┌─────────────────────────────────────────────────────────────────┐
│                            Clients                              │
│   React (Web)      │  Angular (Back-office)  │   Android        │
│   (end users)      │  (admins)               │   (end users)    │
└────────┬───────────┴───────────┬─────────────┴──────┬───────────┘
         └─────────────────────▼────────────────────┘
                  Azure API Management (APIM)
             JWT validation · Routing · Rate limiting · CORS
         ┌────────────────┬───────────────────────────┐
         ▼                ▼                           ▼
   User Service     Journal Service           Notification
   (App Service)    (App Service)              Service
         │                │                           │
   Azure DB for     Azure DB for              Azure Cache
   PostgreSQL       PostgreSQL                 for Redis
   (users)          (journal)
         ▲                ▲
         │                │
    Angular only      React + Android only
    (user mgmt)       (journal entries)

                 └──────────┬────────────┘
                       Azure Event Hubs

PRIVACY RULE
  Angular (admin) → User Service only
                  → Journal Service: BLOCKED (entries are private)
  React + Android → User Service (auth) + Journal Service (entries)
  All clients     → Notification Service: BLOCKED (internal only)
```

### 3.3 Service index

| Service | Port (local) | Spec |
|---|---|---|
| nginx (local gateway) | 8080 | `infra/nginx/nginx.conf` |
| User Service | 8081 | `services/user-service/SPEC.md` |
| Journal Service | 8082 | `services/journal-service/SPEC.md` |
| Notification Service | 8083 | `services/notification-service/SPEC.md` |

> In cloud environments (dev/staging/production) nginx is replaced by **Azure API Management (APIM)**. There is no Spring Cloud Gateway service — APIM and nginx cover all gateway concerns.

### 3.4 Frontend index

| App | Users | Spec |
|---|---|---|
| React Web | End users — write and read journal entries | `frontend-react/SPEC.md` |
| Angular Web | Admins — user management back-office | `frontend-angular/SPEC.md` |
| Android | End users — native mobile journal | `android/SPEC.md` |

---

## 4. Shared Backend Conventions

All Spring Boot services follow these rules without exception.

| Concern | Decision |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1 |
| Build | Maven |
| DTOs | Java records |
| Domain errors | Sealed classes |
| Concurrency | Virtual threads (`spring.threads.virtual.enabled=true`) |
| Pagination defaults | `page=0&size=31` — default page size 31, max page size 100 (enforced server-side: `Math.min(size, 100)`), sort always server-controlled (never accept client sort param) |
| Max request size | 1MB — configured via `spring.servlet.multipart.max-request-size` and `server.tomcat.max-http-form-post-size` |
| DB migrations | Flyway |
| Validation | Jakarta Bean Validation on DTOs only — never on JPA entities |
| Error format | `ProblemDetail` RFC 9457 via `@RestControllerAdvice` |
| Logging | SLF4J + structured JSON (Logback) |
| Metrics | Micrometer + Prometheus |
| Tracing | OpenTelemetry → Azure Application Insights |
| API docs | Springdoc OpenAPI at `/v3/api-docs` |
| Unit tests | JUnit 5 + Mockito |
| Coverage | JaCoCo — 90% service layer (user-service) · 80% service layer (journal-service, notification-service) |
| Flyway repair | Local: `mvn flyway:repair` manually · Cloud: `repair-on-migrate: true` in application.yml |
| Actuator | `health`, `info`, `metrics` exposed — `/actuator/health` used by Azure App Service health probe |
| Integration tests (single-service) | Testcontainers + JUnit 5 |
| API tests (single-service) | Cucumber + Rest Assured + Testcontainers |
| Acceptance tests (cross-service) | Cucumber + Rest Assured + Docker Compose (`bravoscribe-acceptance-tests` module) |

### Testing strategy — three levels

> See **section 4.1** for the full testing strategy — repository structure,
> Maven dependencies, CI/CD pipeline, and complete Cucumber scenarios.

### Test module structure

```
bravoscribe/
├── services/
│   ├── user-service/
│   │   ├── src/test/java/com/bravoscribe/userservice/
│   │   │   ├── unit/                    ← Level 1
│   │   │   │   └── UserServiceTest.java
│   │   │   ├── integration/             ← Level 1+
│   │   │   │   └── UserRepositoryIT.java
│   │   │   └── api/                     ← Level 2
│   │   │       ├── CucumberRunner.java
│   │   │       └── steps/
│   │   │           ├── AuthSteps.java
│   │   │           └── CommonSteps.java
│   │   └── src/test/resources/features/ ← feature files (NOT in java tree)
│   │       ├── auth.feature
│   │       └── password-reset.feature
│   └── journal-service/
│       ├── src/test/java/com/bravoscribe/journalservice/
│       │   ├── unit/
│       │   ├── integration/
│       │   └── api/
│       │       ├── CucumberRunner.java
│       │       └── steps/
│       │           └── JournalSteps.java
│       └── src/test/resources/features/ ← feature files (NOT in java tree)
│           ├── entries.feature
│           ├── tags.feature
│           └── export.feature
└── bravoscribe-acceptance-tests/              ← Level 3
    └── src/test/
        ├── java/com/bravoscribe/acceptance/
        │   ├── CucumberRunner.java
        │   ├── config/
        │   │   └── ServiceConfig.java   ← Testcontainers orchestration
        │   └── steps/
        │       ├── UserSteps.java
        │       ├── JournalSteps.java
        │       └── CommonSteps.java
        └── resources/features/
            ├── user-journal-flow.feature
            ├── account-lifecycle.feature
            ├── security.feature
            └── export-flow.feature
```

### Maven dependencies (all services + bravoscribe-acceptance-tests)

```xml
<!-- Rest Assured -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>

<!-- Cucumber -->
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-java</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-spring</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-junit-platform-engine</artifactId>
    <scope>test</scope>
</dependency>
```

### CI/CD pipeline — test stages

> See **section 4.1** for the authoritative CI/CD pipeline definition.

### bravoscribe-acceptance-tests — Testcontainers orchestration

> See **section 4.1** for the authoritative `ServiceConfig.java` orchestration code
> including `@TestConfiguration`, schema init script, and `Startables.deepStart()`.

### Cross-service acceptance scenarios (bravoscribe-acceptance-tests)

> See **section 4.1** for the authoritative Level 3 Gherkin scenarios
> including all 4 scenarios with correct soft-delete behavior.

### Java 25 usage guidelines
- **Records** — use for all DTOs (request and response). Example: `public record CreateEntryRequest(LocalDate entryDate, String title, String content) {}`
- **Sealed classes** — use for domain error hierarchies. Example: `public sealed class JournalError permits EntryNotFound, DuplicateEntry, UnauthorizedAccess {}`
- **Pattern matching** — use `switch` expressions with pattern matching for error handling and type dispatch
- **Virtual threads** — enabled globally via `spring.threads.virtual.enabled=true`; no manual `Executor` configuration needed

## 4.1 Testing strategy

Three levels of testing with clear boundaries and responsibilities.

```
Level 1 — Unit tests (per service, fast)
  Tools:   JUnit 5 + Mockito
  Tests:   Business logic in isolation — no network, no DB
  Runs:    Every commit

Level 2 — API tests (per service, medium)
  Tools:   Cucumber + Rest Assured + Testcontainers
  Tests:   Full HTTP flow against one service with real DB and Kafka
  Runs:    Every commit

Level 3 — Acceptance tests (cross-service, slow)
  Module:  bravoscribe-acceptance-tests/
  Tools:   Cucumber + Rest Assured + Docker Compose (all services)
  Tests:   Business scenarios spanning multiple services
  Runs:    Before merge to main
```

### Repository structure with tests

```
/
├── services/
│   ├── user-service/
│   │   └── src/test/java/com/bravoscribe/userservice/
│   │       ├── unit/                  ← Level 1: JUnit 5 + Mockito
│   │       │   └── UserServiceTest.java
│   │       ├── integration/           ← Level 2 (infra): Testcontainers
│   │       │   └── UserRepositoryIT.java
│   │       └── api/                   ← Level 2 (API): Cucumber + Rest Assured
│   │           ├── CucumberRunner.java
│   │           └── steps/
│   │               ├── AuthSteps.java
│   │               └── CommonSteps.java
│   └── src/test/resources/
│       └── features/               ← Cucumber features (NOT in java tree)
│           ├── auth.feature
│           └── password-reset.feature
│   └── journal-service/
│       └── src/test/java/com/bravoscribe/journalservice/
│           ├── unit/
│           ├── integration/
│           └── api/
│               ├── CucumberRunner.java
│               └── steps/
│                   └── JournalSteps.java
│   └── src/test/resources/
│       └── features/               ← Cucumber features (NOT in java tree)
│           ├── entries.feature
│           ├── tags.feature
│           └── export.feature
└── bravoscribe-acceptance-tests/            ← Level 3: cross-service
    └── src/test/
        ├── java/com/bravoscribe/acceptance/
        │   ├── CucumberRunner.java
        │   ├── config/
        │   │   └── ServiceConfig.java  ← Testcontainers orchestration
        │   └── steps/
        │       ├── UserSteps.java
        │       ├── JournalSteps.java
        │       └── CommonSteps.java
        └── resources/features/
            ├── user-journal-flow.feature
            ├── account-lifecycle.feature
            ├── security.feature
            └── export-flow.feature
```

### Maven dependencies (add to each service pom.xml)

```xml
<!-- Rest Assured -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>

<!-- Cucumber -->
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-java</artifactId>
    <version>7.15.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-spring</artifactId>
    <version>7.15.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-junit-platform-engine</artifactId>
    <version>7.15.0</version>
    <scope>test</scope>
</dependency>
```

### CI/CD pipeline test stages

```yaml
# .github/workflows/test.yml
jobs:
  unit-and-api-tests:            # Level 1 + 2 — every commit
    run: mvn test

  acceptance-tests:              # Level 3 — before merge to main
    needs: [unit-and-api-tests]
    run: mvn test -pl bravoscribe-acceptance-tests
```

### Level 2 — Cucumber scenarios per service

**User Service (`auth.feature`):**
```gherkin
Feature: Authentication

  Scenario: Successful registration
    When I register with name "Bruno" email "bruno@email.com" password "password123"
    Then I receive status 201
    And the response contains my email and a generated id

  Scenario: Cannot register with duplicate email
    Given a user exists with email "bruno@email.com"
    When I register with email "bruno@email.com" password "password123"
    Then I receive status 409

  Scenario: Cannot register with password shorter than 8 characters
    When I register with email "bruno@email.com" password "short"
    Then I receive status 400

  Scenario: Login with wrong password returns 401
    Given a user exists with email "bruno@email.com" password "password123"
    When I login with email "bruno@email.com" password "wrongpassword"
    Then I receive status 401

  Scenario: Login with unknown email returns same 401 as wrong password
    When I login with email "unknown@email.com" password "anypassword"
    Then I receive status 401

  Scenario: Deactivated user cannot login
    Given a user exists with email "bruno@email.com"
    And the user is deactivated
    When I login with email "bruno@email.com" password "password123"
    Then I receive status 401

  Scenario: Password reset flow end to end
    Given a user exists with email "bruno@email.com"
    When I request a password reset for "bruno@email.com"
    Then I receive status 204
    When I confirm the reset with a valid token and new password "newpassword123"
    Then I receive status 204
    And I can login with the new password "newpassword123"
```

**Journal Service (`entries.feature`):**
```gherkin
Feature: Journal entries

  Background:
    Given I am logged in

  Scenario: Create entry for today
    When I create an entry for today with content "Had a great day"
    Then I receive status 201
    And the entry is saved with today's date

  Scenario: Cannot create two entries for the same day
    Given I have an entry for today
    When I create another entry for today
    Then I receive status 409

  Scenario: Cannot create entry for a future date
    When I create an entry for tomorrow
    Then I receive status 400

  Scenario: Cannot read another user's entry
    Given another user has an entry
    When I try to read that entry
    Then I receive status 404

  Scenario: Cannot edit another user's entry
    Given another user has an entry
    When I try to update that entry
    Then I receive status 404

  Scenario: Search returns matching entries
    Given I have an entry with content "Azure decisions for brazilsouth"
    When I search for "Azure"
    Then the response contains 1 entry

  Scenario: Search query longer than 200 characters returns 400
    When I search with a query of 201 characters
    Then I receive status 400

  Scenario: Export returns zip for valid date range
    Given I have 3 entries this month
    When I export entries for this month
    Then I receive a zip file
    And the zip contains a markdown file

  Scenario: Export returns 404 when no entries in range
    When I export entries for a range with no entries
    Then I receive status 404
```

### Level 3 — Cross-service Cucumber scenarios

**`bravoscribe-acceptance-tests/resources/features/user-journal-flow.feature`:**
```gherkin
Feature: User registration and journaling flow

  Background:
    Given all services are running

  Scenario: New user registers and writes first entry
    When I register with email "bruno@email.com" and password "P@ssword123"
    Then I receive status 201
    And I login with those credentials
    And I create a journal entry with content "My first entry"
    Then the entry is saved successfully
    And my stats show totalEntries: 1 and currentStreak: 1

  Scenario: Deactivated user loses access to their journal
    Given I am registered and logged in as "bruno@email.com"
    And I have 3 journal entries
    When an admin deactivates my account
    Then I cannot login anymore
    And my existing entries are soft-deleted

  Scenario: Password reset does not affect journal entries
    Given I am registered and logged in as "bruno@email.com"
    And I have a journal entry for today
    When I reset my password via the reset flow
    And I login with the new password
    Then my journal entry is still accessible

  Scenario: Account deactivation publishes event consumed by journal service
    Given I am registered and logged in as "bruno@email.com"
    And I have 2 journal entries
    When an admin deactivates my account via the User Service API
    Then within 5 seconds all my journal entries have deleted=true in the database
    And I cannot login with my credentials anymore
```

**`bravoscribe-acceptance-tests/config/ServiceConfig.java` (Testcontainers orchestration):**
```java
@TestConfiguration
public class ServiceConfig {

    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17")
            .withInitScript("init-schemas.sql");

    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    static GenericContainer<?> userService =
        new GenericContainer<>("journalacr.azurecr.io/user-service:test")
            .withExposedPorts(8081)
            .dependsOn(postgres, kafka);

    static GenericContainer<?> journalService =
        new GenericContainer<>("journalacr.azurecr.io/journal-service:test")
            .withExposedPorts(8082)
            .dependsOn(postgres, kafka);

    static {
        Startables.deepStart(userService, journalService).join();
    }
}
```

### Package structure (per service)
```
src/main/java/com/bravoscribe/[service]/
├── config/           # Spring configuration
├── controller/       # @RestController classes
├── dto/              # Request & Response records
├── entity/           # JPA entities
├── repository/       # Spring Data JPA interfaces
├── service/          # Business logic
├── exception/        # Sealed error classes + @RestControllerAdvice
├── security/         # JWT filter, SecurityConfig
└── [Service]Application.java
```

---

## 5. Security

- All endpoints require `Authorization: Bearer <jwt>` except:
  - `POST /api/users/register`
  - `POST /api/users/login`
  - `POST /api/users/refresh` (uses httpOnly cookie, no Bearer token)
  - `POST /api/users/password-reset/request`
  - `PUT /api/users/password-reset/confirm`
- JWT signed with **RS256** (asymmetric); services hold public key only
- Passwords hashed with **BCrypt** strength 12
- HTTPS enforced in all non-local environments (Azure manages TLS)
- CORS: allowlist of known frontend origins per environment
- Rate limiting at APIM per endpoint:
  - `POST /api/users/login` — 100 req/min per IP
  - `POST /api/users/register` — 10 req/hour per IP
  - `POST /api/users/password-reset/request` — 10 req/hour per IP
  - All other endpoints — 500 req/min per authenticated user
- Secrets via **Azure Key Vault** — never committed to repo
- Journal entries are private: service layer enforces `userId` ownership on every query

---

## 6. Infrastructure

### 6.1 Azure services

| Component | Azure Service | Notes |
|---|---|---|
| Container hosting | **Azure App Service** (containers) | One App Service per microservice |
| Container registry | **Azure Container Registry (ACR)** | All images pushed here |
| PostgreSQL | **Azure Database for PostgreSQL Flexible Server** | One shared server, separate schemas per service (cost decision — see §12) |
| Redis | **Azure Cache for Redis** | Basic tier (sufficient for current scale) |
| Message queue | **Azure Event Hubs** (Kafka-compatible API) | Replaces standalone Kafka |
| Secrets | **Azure Key Vault** | All env vars and connection strings |
| API Gateway (cloud) | **Azure API Management (APIM)** | In front of all App Services |
| Frontend hosting | **Azure Static Web Apps** | React + Angular — built-in CDN included, no separate CDN needed |
| Monitoring | **Azure Monitor + Application Insights** | Logs, metrics, traces |
| Email delivery log | **Azure Cosmos DB for MongoDB API** (serverless) | Notification Service only — see decision #22 |

### 6.2 Azure region
- **Primary region:** `brazilsouth` (São Paulo)
- **Reason:** Closest Azure region to Brazilian users — lowest latency

### 6.3 Environments

| Environment | Gateway | Container orchestration | Notes |
|---|---|---|---|
| `local` | nginx | **Docker Compose** | Full stack locally — one command |
| `dev` | Azure APIM | Azure App Service | Auto-deploy on push to `dev` branch |
| `staging` | Azure APIM | Azure App Service | Mirror of production |
| `production` | Azure APIM | Azure App Service | Manual promotion from staging |

### 6.4 Docker Compose scope

> **Docker Compose is used for local development only.** It is never referenced in CI/CD pipelines or Azure infrastructure.

Docker Compose starts the local infrastructure stack:
- PostgreSQL (users + journal databases)
- Redis
- MongoDB (notification-service email log)
- Kafka + Zookeeper
- nginx (gateway on port 8080)
- All three Spring Boot services

**What is shared between local and cloud:** the `Dockerfile` per service. The same image Docker Compose builds locally is the one GitHub Actions builds and pushes to ACR for Azure App Service to run. The only difference between environments is the environment variables — local uses `.env` files, cloud uses Azure Key Vault.

```
Local:  docker-compose build → runs container on your machine
Cloud:  GitHub Actions builds same Dockerfile → pushes to ACR
                                              → App Service pulls and runs it
```

This guarantees the artifact is identical in every environment.

### 6.5 CI/CD
- **Pipeline:** GitHub Actions
- **Flow:** push → build → docker build → push to ACR → deploy to App Service
- **Full pipeline spec:** `infra/azure/SPEC.md`

### 6.6 Why App Service instead of AKS
The system starts with low traffic and a small user base. App Service is simpler to operate, cheaper at low scale, and supports containers natively. Migration to AKS is straightforward when horizontal scaling becomes necessary — the Docker images remain the same.

---

## 7. Observability

Every service must implement all three pillars — logs, metrics, and traces — from day one. Not after launch.

### 7.1 The three pillars

| Pillar | Tool | Sink | What it answers |
|---|---|---|---|
| **Logs** | SLF4J + Logback (structured JSON) | Azure Application Insights | What happened and when |
| **Metrics** | Micrometer + Prometheus | Azure Application Insights | How the system is performing |
| **Traces** | OpenTelemetry | Azure Application Insights | How a request flowed across services |

All three are ingested by **Azure Application Insights** (`journal-insights-[env]`). Connection string injected via Key Vault.

### 7.2 Logging rules (all services)

**Rule 1 — Never log sensitive data**
```java
// ❌ Never log passwords, JWT tokens, or journal content
log.info("Login — email: {} password: {}", email, password);

// ✅ Log IDs and outcomes only
log.info("Login successful — userId: {}", userId);
```

No passwords, no JWT tokens, no journal entry content, no personal data.

**Rule 2 — Always include userId**
Every log line must be traceable to a user. Use MDC (Mapped Diagnostic Context) to attach userId automatically to every log in a request:
```java
MDC.put("userId", userId);  // set once in JWT filter
// all subsequent logs in the request automatically include userId
```

**Rule 3 — Structured JSON always**
Logback outputs JSON in all environments — local and cloud. Application Insights ingests JSON natively and makes every field searchable.

```json
{
  "timestamp": "2026-06-06T20:00:00Z",
  "level": "WARN",
  "service": "journal-service",
  "message": "Unauthorized access attempt",
  "userId": "abc-123",
  "entryId": "xyz-789",
  "traceId": "def-456"
}
```

**Rule 4 — Use the right log level**
| Level | Use for |
|---|---|
| `ERROR` | Exceptions that break a request or require immediate attention |
| `WARN` | Expected failures — invalid credentials, unauthorized access, duplicate entries |
| `INFO` | Key business events — user registered, entry created, export generated |
| `DEBUG` | Implementation detail — only enabled locally, never in production |

### 7.3 Log events per service

**User Service**
```
INFO  User registered — userId: {id}
INFO  User logged in — userId: {id}
INFO  User logged out — userId: {id}
INFO  Token refreshed — userId: {id}
WARN  Failed login — email: {email} reason: invalid_password
WARN  Failed login — email: {email} reason: user_not_found
WARN  Refresh token expired — userId: {id}
WARN  Refresh token revoked — userId: {id}
ERROR JWT signing failed — {exception}
```

**Journal Service**
```
INFO  Entry created — userId: {id} entryDate: {date}
INFO  Entry updated — userId: {id} entryId: {id}
INFO  Entry deleted — userId: {id} entryId: {id}
INFO  Export generated — userId: {id} from: {date} to: {date} entries: {count}
WARN  Duplicate entry attempt — userId: {id} entryDate: {date}
WARN  Unauthorized access attempt — requestUserId: {id} entryUserId: {id}
WARN  Export range exceeded — userId: {id} days: {count}
ERROR Export generation failed — userId: {id} — {exception}
```

**Notification Service**
```
INFO  Email sent — event: {topic} userId: {id}
WARN  Duplicate event skipped — topic: {topic} eventId: {id}
ERROR Email delivery failed — userId: {id} provider: sendgrid — {exception}
ERROR Kafka consumer error — topic: {topic} — {exception}
```

### 7.4 Metrics (Micrometer)

Spring Boot Actuator + Micrometer exposes these automatically at `/actuator/metrics`:

| Metric | Type | Notes |
|---|---|---|
| `http.server.requests` | Timer | Request count, duration, status per endpoint |
| `jvm.memory.used` | Gauge | JVM heap usage |
| `hikaricp.connections.active` | Gauge | DB connection pool usage |
| `kafka.consumer.fetch.latency` | Timer | Kafka consumer lag |

**Custom metrics to add:**
| Metric | Service | Type |
|---|---|---|
| `journal.entries.created` | Journal Service | Counter |
| `journal.exports.generated` | Journal Service | Counter |
| `users.registered` | User Service | Counter |
| `notifications.sent` | Notification Service | Counter |
| `notifications.failed` | Notification Service | Counter |

### 7.5 Distributed tracing (OpenTelemetry)

Every HTTP request gets a `traceId` that follows it across all services. When a request hits APIM → Journal Service → PostgreSQL, the entire chain appears as one trace in Application Insights.

Configure in each service:
```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0   # 100% in dev, reduce to 0.1 in production
```

### 7.6 Application Insights alerts

Set these up from day one in Azure Portal:

| Alert | Condition | Action |
|---|---|---|
| High error rate | HTTP 5xx > 5% of requests for 5 min | Email notification |
| Failed login spike | `WARN Failed login` > 10 per min per IP | Email notification |
| Service down | No heartbeat for 2 min | Email notification |
| Slow response | p95 latency > 2000ms for 5 min | Email notification |

### 7.7 Local observability

Locally, logs appear in the terminal as structured JSON. To make them readable during development, add the `logstash-logback-encoder` with a console pattern for local profile:

```yaml
# application-local.yml
logging:
  pattern:
    console: "%d{HH:mm:ss} [%highlight(%-5level)] %cyan(%logger{20}) - %msg%n"
```

Production always uses JSON. Local uses human-readable format.

---

## 8. Azure PostgreSQL connection notes

Azure Database for PostgreSQL Flexible Server requires:
- SSL enabled (`sslmode=require`)
- Connection string format:
```
jdbc:postgresql://[server].postgres.database.azure.com:5432/[db]?sslmode=require
```

---

## 9. Environment Variables

All secrets stored in **Azure Key Vault**, injected via Managed Identity. Never hardcoded or committed.

**Common to all services:**
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://[server].postgres.database.azure.com:5432/[db]?sslmode=require
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=                     # from Key Vault
JWT_PUBLIC_KEY=                                 # from Key Vault
KAFKA_BOOTSTRAP_SERVERS=[ns].servicebus.windows.net:9093
KAFKA_SASL_JAAS_CONFIG=                         # from Key Vault
APPLICATIONINSIGHTS_CONNECTION_STRING=          # from Key Vault
SPRING_THREADS_VIRTUAL_ENABLED=true
```

---

## 10. AI Regeneration Prompts

### Regenerate a service
```
Using SYSTEM_SPEC.md and services/[name]/SPEC.md, generate the complete
Spring Boot project for [Service Name]. Follow all conventions in
SYSTEM_SPEC.md sections 4 and 5 exactly. Use Java 25 features: records
for all DTOs, sealed classes for domain errors, virtual threads enabled.
Include: entities, DTOs (records), repositories, services, controllers,
@RestControllerAdvice with sealed error handling, SecurityConfig, Flyway
migration V1, and JUnit 5 unit tests for the service layer.
```

### Regenerate a frontend
```
Using SYSTEM_SPEC.md and [frontend]/SPEC.md, generate the full project
scaffold. Include: routing, auth flow (login/register with JWT), base API
client from the OpenAPI spec, and all screens/pages listed in the SPEC.
```

### Add a new microservice
```
Using SYSTEM_SPEC.md, add a new microservice called [Name]. It is
responsible for [description]. Entities: [list]. Endpoints: [list].
Events published: [list]. Events consumed: [list]. Follow all conventions
in SYSTEM_SPEC.md section 4. Also update docker-compose.yml and API
Gateway routes.
```

### Add a feature to an existing service
```
Using SYSTEM_SPEC.md and services/[name]/SPEC.md, add [feature] to
[Service]. Follow existing conventions including Java 25 features.
Include controller, service, DTOs (records), repository changes, Flyway
migration, and unit tests.
```

### Generate Azure infrastructure
```
Using SYSTEM_SPEC.md and infra/azure/SPEC.md, generate the Terraform
configuration for the full Azure infrastructure in the brazilsouth region.
Include: Resource Group, ACR, App Service Plan, one App Service per
microservice, Azure Database for PostgreSQL Flexible Server, Azure Cache
for Redis, Azure Event Hubs, Azure Key Vault, and Azure Static Web Apps.
```

---

## 11. Diagrams (Eraser AI)

Diagrams live in `docs/diagrams/` and are maintained in **Eraser AI** (eraser.io). Each diagram is linked to the relevant spec section so it updates when the spec changes.

> **Eraser AI workflow:**
> 1. Open [eraser.io](https://eraser.io) and create a new diagram
> 2. Use the prompt below to generate the diagram
> 3. Edit visually as needed
> 4. Export as SVG → save to `docs/diagrams/`
> 5. Commit: `git add docs/diagrams/ && git commit -m "docs: update [diagram name] diagram"`

### Diagram inventory

The diagrams follow the **C4 model** — two levels of zoom for the architecture, then detailed diagrams per concern.

| # | Diagram | File | Type | Spec section | Status |
|---|---|---|---|---|---|
| 1 | System architecture — Context | `docs/diagrams/architecture-c4-context.svg` | C4 L1 | SYSTEM_SPEC.md §3.2 | ⬜ todo |
| 2 | System architecture — Container | `docs/diagrams/architecture-c4-container.svg` | C4 L2 | SYSTEM_SPEC.md §6.1 | ✅ done |
| 3 | JWT auth flow | `docs/diagrams/auth-flow-sequence.svg` | Sequence | SYSTEM_SPEC.md §5 | ⬜ todo |
| 4 | Journal Service ER | `docs/diagrams/journal-service-er.svg` | ER | `services/journal-service/SPEC.md` | ⬜ todo |
| 5 | User Service ER | `docs/diagrams/user-service-er.svg` | ER | `services/user-service/SPEC.md` | ⬜ todo |
| 6 | Event flow (Event Hubs topics) | `docs/diagrams/event-flow.svg` | Event flow | SYSTEM_SPEC.md §3.1 | ⬜ todo |
| 7 | CI/CD pipeline | `docs/diagrams/cicd-pipeline.svg` | Pipeline | `infra/azure/SPEC.md` | ⬜ todo |

**When to use which:**
- Show diagram 1 (Context) to stakeholders and non-technical people
- Show diagram 2 (Container) to developers and DevOps — it shows all Azure resources
- Show diagrams 3–7 when working on a specific concern

Update status to ✅ done when the diagram is exported and committed.

> **Known issues in diagram 2 (to fix in next iteration):**
> - Android App arrow goes directly into the Resource Group instead of through APIM
> - "produce journal.*" label is incorrectly placed on ACR — should be on journal-service → Event Hubs
> - Telemetry arrows to App Insights are missing from user-service and journal-service

### Eraser AI prompts

**Diagram 1 — System architecture (C4 Context)**
```
Draw a C4 Context diagram for the Bravoscribe journaling system.
Actors: End User (uses React web app and Android app),
Admin (uses Angular back-office).
Systems: React Web App, Angular Back-office, Android App,
API Gateway (Azure APIM), User Service (Spring Boot),
Journal Service (Spring Boot), Notification Service (Spring Boot).
Datastores: PostgreSQL (users), PostgreSQL (journal), Redis.
Messaging: Azure Event Hubs.
Show all connections with labels.
```

**Diagram 2 — System architecture (C4 Container)**
```
Draw a C4 Container diagram for the Bravoscribe journaling system
hosted on Azure brazilsouth region.

External actors:
- End User (browser + Android)
- Admin (browser)
- GitHub Actions (CI/CD)

Azure resources inside a Resource Group boundary:

Frontends (Azure Static Web Apps):
- React Web App → used by End User
- Angular Back-office → used by Admin

API layer:
- Azure API Management (APIM) receives all traffic from
  frontends and Android
  responsibilities: JWT validation, routing, rate limiting, CORS

App Services (containers pulled from ACR):
- user-service (Spring Boot :8081) ← routes from APIM
- journal-service (Spring Boot :8082) ← routes from APIM
- notification-service (Spring Boot :8083) ← internal only

Container registry:
- Azure Container Registry (ACR) → supplies images to all App Services
- GitHub Actions → pushes images to ACR on every deploy

Datastores:
- Azure DB for PostgreSQL Flexible Server
  database: journal (one shared database, two schemas per decision #12)
    schema: users → used by user-service
    schema: journal → used by journal-service
- Azure Cache for Redis → used by journal-service (stats cache) and notification-service (idempotency)

Messaging:
- Azure Event Hubs (Kafka-compatible)
  topics: users.user.registered, users.user.deactivated,
          users.password.reset.requested,
          journal.entry.created, journal.entry.updated
  producers: user-service, journal-service
  consumers: journal-service, notification-service

Secrets:
- Azure Key Vault → injects secrets into all App Services
  via Managed Identity

Observability:
- Azure Application Insights ← receives telemetry from
  all App Services

Show all connections with directional arrows and short labels.
```

**Diagram 3 — JWT auth flow (sequence)**
```
Draw a sequence diagram for JWT authentication.
Participants: Client, Gateway (nginx locally / APIM in cloud),
User Service, PostgreSQL.
Flow:
1. Client POST /api/users/login with email and password
2. Gateway forwards to User Service (no auth required for login)
3. User Service queries PostgreSQL to verify credentials
4. User Service returns accessToken (RS256 JWT) and refreshToken
5. Gateway returns tokens to Client
6. Client makes protected request with Authorization: Bearer <accessToken>
7. Gateway validates JWT signature using RS256 public key
8. Gateway forwards request to target service
9. On 401, Client POST /api/users/refresh with refreshToken
10. User Service returns new accessToken and refreshToken
```

**Diagram 4 — Journal Service ER diagram**
```
Draw an ER diagram with these entities:

User (from User Service, reference only — no join):
  id UUID PK, email STRING, name STRING

JournalEntry:
  id UUID PK, userId UUID FK, entryDate DATE UNIQUE per user,
  title STRING nullable, content TEXT, mood ENUM nullable,
  deleted BOOLEAN, createdAt TIMESTAMP, updatedAt TIMESTAMP

Tag:
  id UUID PK, userId UUID, name STRING unique per user

JournalEntryTag (join table):
  entryId UUID FK → JournalEntry, tagId UUID FK → Tag

Relationships:
- JournalEntry many-to-many Tag via JournalEntryTag
- JournalEntry.userId references User.id (logical, no FK across services)
```

**Diagram 5 — User Service ER diagram**
```
Draw an ER diagram for the User Service database.

Entities:

User:
  id UUID PK, name STRING, email STRING unique,
  passwordHash STRING, role ENUM (USER, ADMIN),
  active BOOLEAN, createdAt TIMESTAMP, updatedAt TIMESTAMP

RefreshToken:
  id UUID PK, token STRING unique, userId UUID FK → User,
  expiresAt TIMESTAMP, revoked BOOLEAN

Relationships:
- User one-to-many RefreshToken
```

**Diagram 6 — Event flow**
```
Draw an event flow diagram for Azure Event Hubs topics.

5 topics total:

- users.user.registered:
    published by: User Service
    consumed by:  Notification Service (sends welcome email)

- users.user.deactivated:
    published by: User Service
    consumed by:  Journal Service only (soft-deletes all entries for that userId)
    Note: Notification Service does NOT consume this event

- users.password.reset.requested:
    published by: User Service
    consumed by:  Notification Service (sends password reset email with #token= link)

- journal.entry.created:
    published by: Journal Service
    consumed by:  Notification Service (sends entry confirmation email)

- journal.entry.updated:
    published by: Journal Service
    consumed by:  Notification Service (event discarded, idempotency key stored only)
```

**Diagram 7 — CI/CD pipeline**
```
Draw a CI/CD pipeline diagram for the Bravoscribe system.

Trigger: developer pushes code to GitHub (main or dev branch)

Backend pipeline steps:
1. GitHub Actions workflow starts
2. Set up Java 25 and run mvn clean verify
   # verify runs JaCoCo coverage check — build fails if below threshold
3. Docker build — create container image tagged with git SHA
4. Docker push — push image to Azure Container Registry (ACR)
5. az webapp config container set — update App Service with new image tag
6. az webapp restart — restart the App Service
7. Azure Application Insights confirms deployment health

Frontend pipeline steps:
1. GitHub Actions workflow starts
2. npm install and npm run build
3. Deploy to Azure Static Web Apps via official GitHub Action

Show both pipelines in parallel tracks.
Actors: Developer, GitHub, GitHub Actions, ACR, App Service,
Azure Static Web Apps, Application Insights.
```

---

## 12. Decisions Log

| # | Decision | Reason | Date |
|---|---|---|---|
| 1 | One database per service | Avoid coupling; each service owns its data | 2026-06-04 |
| 2 | RS256 JWT (asymmetric) | Services only need public key to verify | 2026-06-04 |
| 3 | React = end users, Angular = back-office | Both are learning goals; intentionally separate apps | 2026-06-04 |
| 4 | Azure Event Hubs over standalone Kafka | Managed service; Kafka-compatible API means no code change | 2026-06-04 |
| 5 | App Service over AKS | Lower complexity at current scale; easy migration path to AKS later | 2026-06-04 |
| 6 | brazilsouth region | Closest Azure region to Brazilian users | 2026-06-04 |
| 7 | Azure Key Vault for secrets | Centralised, auditable secret management; native App Service integration | 2026-06-04 |
| 8 | Journal entries scoped by userId | Entries are private — enforced at service layer, not just UI | 2026-06-04 |
| 9 | Java 25 | Latest LTS; learning goal — use records, sealed classes, virtual threads | 2026-06-04 |
| 10 | Android separate from web | Native mobile is a deliberate learning goal, not redundancy | 2026-06-04 |
| 11 | nginx for local dev instead of Spring Cloud Gateway | Single entry point locally mirrors APIM in cloud; no extra Spring Boot service to maintain | 2026-06-04 |
| 12 | Shared PostgreSQL server for User Service and Journal Service | Cost saving (~$12/month less than two separate servers). Services are still logically isolated via separate schemas and separate DB users scoped to their own schema. Migration path to separate servers is documented in `infra/azure/SPEC.md` — requires only a `SPRING_DATASOURCE_URL` change in Key Vault, no code changes. | 2026-06-06 |
| 13 | No separate Azure CDN | Azure Static Web Apps already includes a built-in CDN for static assets at no extra cost. A separate CDN would only help with API caching, which is inappropriate for private journal data. Removed from architecture to avoid unnecessary cost and complexity. | 2026-06-06 |
| 14 | App name: Bravoscribe | Bravo (owner's name) + scribe (one who writes) — personal, descriptive, epic quality fitting the FFT theme. A scribe is exactly what a journal writer is. | 2026-06-07 |
| 15 | Warm/personal visual direction | Parchment tones, Lora serif typography, ink brown palette — makes writing feel personal rather than productive | 2026-06-07 |
| 16 | Raw reset token in Event Hubs payload (accepted risk) | The raw password reset token is published to Event Hubs so the Notification Service can build the reset link without a DB call. Risk: token visible in Event Hubs message retention (1-7 days). Accepted because: (1) Event Hubs access is restricted to Azure Managed Identity only, (2) token expires in 15 minutes, (3) token is single-use, (4) alternative (encrypting with Notification Service public key) adds significant complexity for a personal app. Revisit if the app becomes multi-user or public. | 2026-06-07 |
| 17 | JWT public key rotation not formally documented (accepted risk) | RS256 key rotation procedure is not defined in the spec. Accepted because: (1) this is a personal app with one user, (2) key compromise is unlikely, (3) rotation procedure is straightforward — generate new pair, update Key Vault secrets, restart App Services. Document formally before going multi-user. | 2026-06-07 |
| 18 | nginx CORS wildcard in local dev (accepted risk) | nginx uses `Access-Control-Allow-Origin: *` locally. Risk is limited to local development only — APIM enforces a strict CORS allowlist in all cloud environments. Accepted because: (1) local dev is not internet-exposed, (2) fixing it requires per-developer origin config, (3) risk is negligible on a local machine. | 2026-06-07 |
| 19 | Streak banner in localStorage (accepted risk) | The dismissed streak banner flag is stored in localStorage, accessible to JavaScript. Risk: XSS attack could manipulate it. Accepted because: (1) CSP headers limit XSS attack surface, (2) the worst outcome is the banner showing or not showing incorrectly — no sensitive data involved. | 2026-06-07 |
| 20 | Eraser auth sequence diagram shows refreshToken in body (accepted risk) | The sequence diagram prompt predates the V2 cookie fix and still shows refreshToken in the request body. Accepted because: (1) diagrams are reference only, not authoritative, (2) the spec text and code are the source of truth, (3) fixing the diagram is a low-priority cosmetic update. Update when regenerating diagrams. | 2026-06-07 |
| 21 | Email logged in plaintext on failed login (accepted risk) | Failed login warnings log the raw email address. Risk: email visible in Application Insights logs to Azure subscription owners. Accepted because: (1) this is a personal single-user app — the only email in the system is the owner's, (2) Application Insights access is restricted to the subscription owner. Hash email if app becomes multi-user. | 2026-06-07 |
| 22 | MongoDB for email delivery log (Notification Service) | Email documents have different shapes per notification type — exactly the NoSQL use case. Schema migrations never needed for new notification types. Azure Cosmos DB for MongoDB API (serverless) costs ~$1/month for a personal app. Learning goal: Spring Data MongoDB, document design vs relational. | 2026-06-11 |


---

*Keep this file updated as the system evolves. Service-level details live in each `SPEC.md`.*
