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

> _To be added during Phase 3 implementation._

---

## Phase 4 — Notification Service

> _To be added during Phase 4 implementation._

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
