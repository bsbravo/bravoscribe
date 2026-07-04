# Bravoscribe — Lessons Learned

Real issues encountered during implementation and the fixes applied.
Updated as each phase progresses.

---

## Phase 2 — User Service

### L1 — `server.servlet.context-path` breaks Testcontainers tests

**What happened:** Setting `server.servlet.context-path=/api/users` in `application.yml`
causes `@SpringBootTest(webEnvironment = RANDOM_PORT)` to return 404 on all endpoints.
Spring Boot does not apply the servlet context-path in the test environment.

**Fix:** Remove `server.servlet.context-path`. Instead, declare the full base path
explicitly on every controller:
```java
@RequestMapping("/api/users")   // AuthController, AdminController
@RequestMapping("/api/users/me") // UserController
@RequestMapping("/api/users/password-reset") // PasswordResetController
```
`SecurityConfig` requestMatchers must use the same full `/api/users/...` paths.

---

### L2 — Rest Assured 5.x throws NPE on Java 25 for non-POST methods

**What happened:** Rest Assured 5.x uses Groovy 4.x HTTPBuilder which has a metaclass
reflection bug on Java 25. All non-POST HTTP methods (GET, PUT, DELETE) throw:
```
NullPointerException at HTTPBuilder.doRequest:494
```

**Fix:** Use Rest Assured **6.0.0** explicitly in `pom.xml`:
```xml
<rest-assured.version>6.0.0</rest-assured.version>
```

---

### L3 — V1 migration must NOT contain `CREATE SCHEMA`

**What happened:** Adding `CREATE SCHEMA IF NOT EXISTS users;` to V1 migration fails
because Flyway connects as `user_svc` which has schema-level privileges only, not
database-level `CREATE` privilege.

**Fix:** Remove `CREATE SCHEMA` from V1. The schema must be pre-created:
- **Production:** `infra/init-schemas.sql` runs as superuser via `docker-compose`
- **Tests:** Testcontainers `.withInitScript("db/test-schema.sql")` runs as superuser

`test-schema.sql` content:
```sql
CREATE SCHEMA IF NOT EXISTS users;
GRANT ALL ON SCHEMA users TO user_svc;
```

---

### L4 — `@DynamicPropertySource` does not inherit env-var defaults

**What happened:** `JwtService` produced tokens with **zero expiry** in tests.
`application.yml` uses `${JWT_ACCESS_EXPIRY_SECONDS:900}` with env-var fallback,
but `@DynamicPropertySource` in Testcontainers does not inherit these fallback values.

**Fix:** Explicitly set all JWT expiry values in `src/test/resources/application.yml`:
```yaml
jwt:
  access-expiry-seconds: 900
  refresh-expiry-days: 30
  password-reset-expiry-minutes: 15
```

---

### L5 — Cucumber Background step must delete-then-create users

**What happened:** Scenarios that change the password (e.g. "Change password succeeds")
leave stale DB state. The next scenario's Background step tries to register the same
email — which either fails (409 conflict) or succeeds but with the wrong password.

**Fix:** The "a registered user" Background step must delete before creating:
```java
@Given("a registered user with email {string} and password {string}")
public void aRegisteredUser(String email, String password) {
    userRepo.findByEmail(email).ifPresent(userRepo::delete); // ← delete first
    registerUser(email, password);
    ctx.reset();
}
```

---

### L6 — Kafka `localhost:9092` not reachable from host machine

**What happened:** Running user-service via `./mvnw spring-boot:run` (outside Docker)
fails to connect to Kafka at `localhost:9092`. Kafka inside Docker advertises itself
as `kafka:9092` — a hostname only resolvable inside the Docker network.

**Fix:** Add a second listener on port `29092` for host machine access in `docker-compose.yml`:
```yaml
kafka:
  ports: ["9092:9092", "29092:29092"]
  environment:
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,PLAINTEXT_HOST://0.0.0.0:29092
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092
    KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
```
Then in `application-local.yml`:
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:29092
```

---

### L7 — Swagger UI returns 401 without explicit `permitAll`

**What happened:** `SecurityConfig` blocked all requests with JWT validation by default,
including Swagger UI and OpenAPI docs endpoints.

**Fix:** Explicitly permit Swagger paths in `SecurityConfig`:
```java
.requestMatchers(
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/v3/api-docs/**"
).permitAll()
```
In production (Azure) these paths are blocked at APIM level and never exposed publicly.

---

### L8 — PowerShell `curl` is not real curl

**What happened:** `curl` in PowerShell is an alias for `Invoke-WebRequest` with
different syntax. The `-H` flag doesn't work the same way.

**Fix:** Use `curl.exe` to invoke the real curl binary shipped with Windows 10/11:
```powershell
curl.exe -X POST http://localhost:8081/api/users/register \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Bruno\",...}"
```
Or use Git Bash where standard curl syntax works without escaping issues.

---

## Phase 3 — Journal Service

### L1 — Jackson 2 import instead of Jackson 3

**What happened:** `UserDeactivatedConsumer.java` used
`com.fasterxml.jackson.databind.ObjectMapper`. Spring Boot 4.1 ships Jackson 3,
which lives under a different package — the wrong import compiles (both are on
the classpath transitively) but produces `ClassNotFoundException` at runtime.

**Fix:** Use the Jackson 3 import everywhere:
```java
import tools.jackson.databind.ObjectMapper;
```

---

### L2 — Spring Data Redis 4.x requires an explicit serializer for POJOs

**What happened:** `RedisConfig.java`'s `defaultCacheConfig()` uses
`DefaultRedisElementWriter`, which fails to serialize plain POJOs/records cached
via `@Cacheable`.

**Fix:**
```java
.serializeValuesWith(SerializationPair.fromSerializer(new JdkSerializationRedisSerializer()))
```
The cached type (`StatsResponse`) must also `implements Serializable`.

---

### L3 — Hibernate `lower(bytea)` error on PostgreSQL search query

**What happened:** `JournalEntryRepository.searchEntries`'s JPQL used
`LOWER(CONCAT('%', :q, '%'))`. Hibernate 6.x sometimes binds a null/long String
parameter as `bytea`, and Postgres has no `lower(bytea)` overload.

**Fix:** Force the parameter type explicitly in the HQL:
```
CAST(:q AS String)
```

---

### L4 — `@Validated` + `@Size` on `@RequestParam` throws `ConstraintViolationException`

**What happened:** Method-level validation on a controller parameter goes through
an AOP proxy, which throws `ConstraintViolationException` — not
`HandlerMethodValidationException`, which is what `@Valid` on a request body throws.
The existing exception handler only caught the latter, so validation failures
surfaced as a raw 500.

**Fix:** Add a dedicated handler:
```java
@ExceptionHandler(ConstraintViolationException.class)
```
to `GlobalExceptionHandler`.

---

### L5 — Misdiagnosed Page response by checking `totalElements` instead of `content`

**What happened:** A search endpoint returning a SQL error (500, from L3) was
initially misdiagnosed as a `Page<T>` serialization problem because
`totalElements` came back null/missing in the test assertion.

**Fix:** `totalElements` stays at the JSON root in Spring Data 4.x — unchanged
from 3.x. Assert on `content` list emptiness instead of `totalElements`; it's a
more robust signal and won't be misread when the real failure is upstream (a 500).

---

### L6 — Test class package must match the class under test, not its folder

**What happened:** `JournalServiceTest.java` needed access to package-private
methods on `JournalService`, but lives physically under a `unit/` test folder.

**Fix:** Java resolves visibility by the `package` declaration, not directory
path — declare the test class in the same package as the class under test even
when the file sits in a different folder:
```java
package com.bravoscribe.journalservice.service;
```

---

### L7 — Copied `mvnw` wrapper loses its execute bit

**What happened:** Copying `mvnw` from another service (or checking it out on
Windows) drops the Unix execute permission, so `./mvnw` fails in CI/Docker with
a permission error even though the file is present and correct.

**Fix:** After copying the Maven wrapper into a new service, immediately run:
```
git update-index --chmod=+x <service>/mvnw
```
This bit doesn't survive a normal `git add` — it must be set explicitly.

---

## Phase 4 — Notification Service

### L1 — Mockito 5 `STRICT_STUBS` makes mocking `RestClient`'s fluent chain painful

**What happened:** `RestClient`'s builder-style API (`.post().uri(...).body(...).retrieve()...`)
requires stubbing a long chain of intermediate mock objects. Under Mockito 5's
`STRICT_STUBS`, any unused stub in that chain fails the test with
`UnnecessaryStubbingException`.

**Fix:** Extract the SendGrid HTTP call behind a small functional interface,
`SendGridGateway`, and inject that into `NotificationService` instead of the
`RestClient` directly. Unit tests mock `SendGridGateway` (a single method call)
instead of the multi-step `RestClient` chain.

---

### L2 — Spring Boot 4.1 ships no Kafka auto-configuration

**What happened:** Unlike earlier Spring Boot versions, adding `spring-kafka`
alone does not auto-configure a `ConsumerFactory` or listener container — 
`@KafkaListener` methods silently never fire.

**Fix:** `KafkaConfig.java` must manually declare:
```java
@EnableKafka
// + a ConsumerFactory bean
// + a ConcurrentKafkaListenerContainerFactory bean
```
`KafkaTemplate` (producer side) is only needed in `@TestConfiguration` for test
message publishing — notification-service has no production producer.

---

### L3 — Redis Testcontainer's default wait strategy races on startup

**What happened:** Integration tests using a plain `GenericContainer<>("redis:7")`
intermittently failed to connect — the container reported "started" before Redis
was actually accepting connections.

**Fix:** Use an explicit log-based wait strategy:
```java
.waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
```

---

## Phase 5 — React Web App

> _To be added during Phase 5 implementation._

---

## Phase 6 — Android App

> _To be added during Phase 6 implementation._

---

## Phase 7 — Angular Back-office

> _To be added during Phase 7 implementation._

---

## Phase 8 — Azure Cloud

> _To be added during Phase 8 implementation._
