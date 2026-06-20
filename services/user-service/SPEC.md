# User Service — Service Specification

> See `SYSTEM_SPEC.md` for system-wide conventions.

## Responsibility
Manages user identity: registration, authentication, JWT issuance, profile management, and role assignment.

## Cookie security

The refreshToken is stored in an httpOnly cookie with these security attributes:

```java
ResponseCookie.from("refreshToken", token)
    .httpOnly(true)        // not accessible via JavaScript
    .secure(true)          // HTTPS only — set false for local dev
    .sameSite("Strict")    // never sent on cross-site requests (CSRF protection)
    .path("/api/users")    // only sent to user service endpoints
    .maxAge(Duration.ofDays(30))
    .build()
```

> **Local dev:** set `secure(false)` via `application-local.yml` — localhost is HTTP.
> **Production:** always `secure(true)` — Azure enforces HTTPS.

## Password policy

> **Single source of truth** — defined here, referenced by all clients (React, Angular, Android).

| Rule | Value |
|---|---|
| Minimum length | 8 characters |
| Maximum length | 128 characters |
| Complexity | None — length only |
| Hashing | BCrypt strength 12 |

**Backend validation (Jakarta Bean Validation on DTO):**
```java
public record RegisterRequest(
    @NotBlank @Size(max = 100) String name,
    @Email @NotBlank String email,
    @Size(min = 8, max = 128) String password
) {}

public record UpdateProfileRequest(
    @NotBlank @Size(max = 100) String name
) {}

public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @Size(min = 8, max = 128) String newPassword
) {}

public record ResetPasswordRequest(
    @NotBlank String token,
    @Size(min = 8, max = 128) String newPassword
) {}
```

Returns `400 Bad Request` with `ProblemDetail` if validation fails.

**This policy applies to:**
- `POST /api/users/register` — `password` field
- `PUT /api/users/me/password` — `newPassword` field
- `PUT /api/users/password-reset/confirm` — `newPassword` field

> **Security:** the DB lookup `WHERE token = ?` (comparing SHA-256 hashes) is sufficient
> protection against timing attacks. The comparison happens inside PostgreSQL where
> query time varies for unrelated reasons (network, query planning), making byte-by-byte
> brute-force impractical. `MessageDigest.isEqual()` is only needed when comparing
> tokens in application memory — which this implementation correctly avoids by always
> hashing before the DB lookup.

## Technology
| Property | Value |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1 |
| Port (local) | 8081 |
| Database | Azure Database for PostgreSQL Flexible Server — db: `journal`, schema: `users` |
| Special | Holds JWT **private key** (RS256) — no other service does |
| JWT library | `spring-boot-starter-oauth2-resource-server` — `NimbusJwtEncoder` (sign) + `NimbusJwtDecoder` (verify) |
| JWT algorithm | RS256 only — explicitly reject HS256 and any other algorithm to prevent algorithm confusion attacks |
| Context path | `server.servlet.context-path` is NOT set — see **LESSONS_LEARNED.md L1** in `application.yml`. `@SpringBootTest(webEnvironment = RANDOM_PORT)` does not apply the servlet context-path — all integration tests would receive 404. Instead every controller declares the full base path explicitly: `@RequestMapping("/api/users")` on `AuthController` and `AdminController`, full paths on `UserController` (`/api/users/me`) and `PasswordResetController` (`/api/users/password-reset`). `SecurityConfig` requestMatchers must use the same full `/api/users/...` paths. |

## JWT access token claims

| Claim | Value | Notes |
|---|---|---|
| `sub` | `userId` (UUID string) | Primary user identifier used by all services |
| `role` | `USER` or `ADMIN` | Single role per user |
| `exp` | `now + 900s` | 15 minute expiry |
| `iat` | `now` | Issued at |

**Example decoded payload:**
```json
{
  "sub": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "role": "USER",
  "iat": 1749999600,
  "exp": 1750000500
}
```

> No email or name in the token — keeps tokens small and avoids stale data.
> All services extract `sub` (userId) and `role` from these claims.

## Entities

### User
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| name | String | Required, max 100 chars |
| email | String | Unique, required |
| passwordHash | String | BCrypt strength 12 |
| role | Enum | `USER`, `ADMIN` |
| active | Boolean | Soft delete flag, default true |
| reminderTime | String | nullable — daily reminder time e.g. "21:00" (HH:mm UTC). Phase 2. |
| weeklySummaryEnabled | Boolean | default false — weekly summary email. Phase 2. |
| createdAt | Instant | Auto-set |
| updatedAt | Instant | Auto-updated |

### RefreshToken
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| token | String | Unique, stored hashed (SHA-256) |
| userId | UUID | FK → User |
| expiresAt | Instant | |
| revoked | Boolean | default false |

### PasswordResetToken
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| token | String | Unique, stored hashed (SHA-256) |
| userId | UUID | FK → User |
| expiresAt | Instant | 15 minutes from creation |
| used | Boolean | default false — single use only |

## Endpoints

### Public (no JWT required)
> **Security rule:** `POST /api/users/login` and `POST /api/users/refresh`
> must check `user.active = true` before issuing tokens. Return 401 if
> `active = false`. Deactivation also revokes all existing refresh tokens.

```
POST /api/users/register
     Body: { name, email, password }
     Returns: 201 { accessToken } + Set-Cookie: refreshToken (httpOnly)
              Same response shape as POST /login — user is immediately authenticated
              after registration, no second login required
     Rate limit: 10 req/hour per IP (enforced at APIM)

POST /api/users/login
     Body: { email, password }
     Returns: 200 { accessToken } + sets refreshToken httpOnly cookie
     Error:   401 always — never distinguish between wrong email and wrong password
     Notes:   constant-time comparison — call BCrypt.matches() even when email not found
              (use a dummy hash) to prevent timing-based account enumeration
     Rate limit: 100 req/min per IP (enforced at APIM)

POST /api/users/refresh
     Cookie: refreshToken (httpOnly — sent automatically by browser, NOT in request body)
     Returns: 200 { accessToken } + sets new refreshToken httpOnly cookie
     Notes:   backend reads refreshToken from HttpServletRequest.getCookies()
              never accept refreshToken in request body — defeats httpOnly purpose

### Refresh token rotation (Option B)

On every `POST /api/users/refresh` call:

```
1. Read refreshToken from httpOnly cookie
2. Look up token in refresh_tokens table
3. Validate: exists, not revoked, not expired, user is active
4. If invalid → 401 Unauthorized, clear cookie
5. If valid:
   a. Mark old token as revoked = true (single DB update)
   b. Generate new refresh token (UUID)
   c. Insert new token into refresh_tokens table
   d. Issue new access token (JWT, 15 min)
   e. Set new refreshToken httpOnly cookie
   f. Return 200 { accessToken }
```

**Why this matters:** if a refresh token is stolen and used, the attacker
gets one new access token but the legitimate user's next refresh call will
fail (the stolen token was already rotated). The session is broken and the
user is forced to log in again — limiting the attack window.

**Reuse detection (token theft indicator):**
If a refresh token is presented that was already rotated (revoked = true):
1. Invalidate ALL refresh tokens for that userId immediately
2. Return 401
3. Force full re-login

This detects token theft — if the attacker used the stolen token and the
legitimate user tries to refresh, both are now logged out. The legitimate
user notices and can change their password.

**Cleanup:** delete expired refresh tokens older than 30 days on each login
to prevent the `refresh_tokens` table growing unbounded.
```

### Protected (JWT required)
```
DELETE /api/users/logout
       Cookie: refreshToken (httpOnly — read from cookie, not body)
       Returns: 204
       Notes:   revokes the refresh token and clears the cookie

GET  /api/users/me
     Returns: 200 { id, name, email, role, active, createdAt }
              active — lets client detect deactivated accounts
              createdAt — "member since" date on profile screen

PUT  /api/users/me
    Security: `role` and `active` fields are NEVER accepted from this endpoint.
              DTO must explicitly exclude them — use a separate UpdateProfileRequest
              record that only contains `name`. Never map request body directly
              to the User entity to prevent mass assignment / privilege escalation.
     Body: { name }
     Returns: 200 { id, name, email, role, active, createdAt }

PUT  /api/users/me/password
     Body: { currentPassword, newPassword }
     Returns: 204
     Error:   400 if currentPassword is incorrect
     Notes:   newPassword must meet same strength rules as registration
              all existing refresh tokens are revoked on success
              (forces re-login on all devices)
```

### Public (password reset — no JWT required)
```
POST /api/users/password-reset/request
     Body: { email }
     Returns: 204 always — never reveals if email exists (security)
     Action: if email found → generate token → store hashed →
             publish users.password.reset.requested event to Kafka
             token expires in 15 minutes, single use

PUT  /api/users/password-reset/confirm
     Body: { token, newPassword }
     Returns: 204
     Error:   400 if token not found, already used, or expired
     Action: validate token → update password (BCrypt 12) →
             mark token as used → revoke all refresh tokens →
             user must re-login on all devices
```

### Protected (continued)
```
PUT  /api/users/me/preferences
     Body: { reminderTime?, weeklySummaryEnabled? }
     Returns: 200 { reminderTime, weeklySummaryEnabled }
     Notes: Phase 2 — implement after core is working
```

### Admin only
```
GET /api/users?page=0&size=31     → 200 Page<UserResponse>
    Notes:   ADMIN role required
             default page=0, size=31
             max page size capped at 100 server-side: Math.min(size, 100)
             sort: createdAt descending (newest first) — not client-configurable
             implementation: PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending())
PUT /api/users/{id}/deactivate    → 204
     Notes:   sets user.active = false
              revokes ALL existing refresh tokens for that user
              user is immediately locked out on next token refresh
```

## Events published (Azure Event Hubs)
| Topic | Trigger | Payload |
|---|---|---|
| `users.user.registered` | New registration | `{ eventId, userId, email }` |
| `users.user.deactivated` | Admin deactivates | `{ eventId, userId }` |
| `users.password.reset.requested` | Reset requested | `{ eventId, userId, email, resetToken, expiresAt }` |

> `resetToken` in the event is the **raw** (unhashed) token — the Notification
> Service uses it to build the reset link. User Service stores only the hashed
> version. Token never stored raw anywhere except in transit via Event Hubs.

## Database migrations (Flyway)

> **Critical:** Flyway connects as `user_svc` (not `postgres`). By default `user_svc`
> only has `USAGE` on the `users` schema — not `CREATE`. Without `GRANT CREATE ON SCHEMA users TO user_svc`
> in `infra/init-schemas.sql`, V1 will fail immediately with:
> `ERROR: permission denied for schema users`
>
> This is already handled in `infra/init-schemas.sql`. If you ever recreate the schema
> or add a new service, remember to add the `GRANT CREATE` for the new service user.
> The same pattern applies to `journal_svc` on the `journal` schema.

> **Critical:** V1 migration must NOT contain `CREATE SCHEMA IF NOT EXISTS users;` — see **LESSONS_LEARNED.md L3**.
> The schema must already exist before Flyway runs — pre-created by `infra/init-schemas.sql`
> (superuser) in production and by Testcontainers `withInitScript("db/test-schema.sql")` in tests.
> Adding `CREATE SCHEMA` to V1 fails because `user_svc` has schema-level privileges only,
> not database-level `CREATE` privilege.


```
V1__create_users_table.sql
V2__create_refresh_tokens_table.sql
V3__create_password_reset_tokens_table.sql
V4__add_preference_fields_to_users.sql   ← adds reminder_time, weekly_summary_enabled
```
> V4 adds preference fields (reminderTime, weeklySummaryEnabled) — implement in Phase 2 alongside
  the entity fields and PUT /api/users/me/preferences endpoint. All three must be consistent:
  entity fields → V4 migration → endpoint. Without V4, Hibernate ddl-auto: validate fails on startup.

## Scheduled jobs
```java
// Runs daily at 3am — removes expired password reset tokens
@Scheduled(cron = "0 0 3 * * *")
public void cleanExpiredPasswordResetTokens() {
    passwordResetTokenRepo.deleteByExpiresAtBefore(
        Instant.now().minus(24, ChronoUnit.HOURS)
    );
}
```
Enable scheduling in the main class: `@EnableScheduling`

## Environment variables
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://[server].postgres.database.azure.com:5432/journal?currentSchema=users&sslmode=require
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
JWT_PRIVATE_KEY=
JWT_PUBLIC_KEY=
# Request size limit (also set in application.yml)
# spring.servlet.multipart.max-request-size=1MB
# server.tomcat.max-http-form-post-size=1MB
JWT_ACCESS_EXPIRY_SECONDS=900
JWT_REFRESH_EXPIRY_DAYS=30
PASSWORD_RESET_EXPIRY_MINUTES=15
KAFKA_BOOTSTRAP_SERVERS=[ns].servicebus.windows.net:9093
KAFKA_SASL_JAAS_CONFIG=
```

## Logging

Follow the logging rules in `SYSTEM_SPEC.md` section 7.2. Key events to log:

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

Use MDC to attach `userId` to all logs after authentication:
```java
MDC.put("userId", userId.toString());
```

**Never log:** passwords, raw JWT tokens, email addresses in INFO/ERROR levels.

## Cucumber feature files (Level 2 API tests)

```
src/test/resources/features/
├── auth.feature              ← register, login, logout, refresh
├── password-reset.feature    ← forgot password end to end
├── password-change.feature   ← change password, revoke tokens
├── profile.feature           ← get/update profile
└── admin.feature             ← admin user list, deactivate
```

See `SYSTEM_SPEC.md` section 4.1 for full scenario examples and step definition structure.

## Definition of Done

Before moving to the next service, verify all of these:

- [ ] `./mvnw clean verify` passes, all unit tests pass, JaCoCo 90% threshold met
- [ ] Service starts locally with `docker-compose up`
- [ ] All endpoints verified via Swagger UI (`http://localhost:8081/swagger-ui.html`)
- [ ] OpenAPI spec exported and committed:
  ```bash
  curl http://localhost:8081/v3/api-docs -o docs/openapi/user-service.yaml
  git add docs/openapi/user-service.yaml
  git commit -m "docs: export user-service OpenAPI spec"
  ```
- [ ] JWT login flow tested end to end (register → login → access protected endpoint)
- [ ] Register returns 400 for password shorter than 8 characters
- [ ] Register returns 400 for password longer than 128 characters
- [ ] Change password returns 400 for new password shorter than 8 characters
- [ ] Reset password confirm returns 400 for new password shorter than 8 characters
- [ ] Admin endpoints return 403 for USER role
- [ ] Change password returns 422 for wrong current password
- [ ] Change password revokes all existing refresh tokens on success
- [ ] Password reset request always returns 204 (even for unknown email)
- [ ] Password reset token expires after 15 minutes
- [ ] Password reset token is single-use (second use returns 400)
- [ ] Password reset revokes all refresh tokens on success
- [ ] Expired token cleanup job runs without error
- [ ] Kafka events verified (check Event Hubs / local Kafka topic)
- [ ] Login returns 401 for both wrong email and wrong password (same response, no enumeration)
- [ ] Deactivated user cannot login (401)
- [ ] Deactivated user refresh token returns 401
- [ ] Deactivated user has all refresh tokens revoked
- [ ] refreshToken cookie has httpOnly, Secure, SameSite=Strict, path=/api/users
- [ ] Register rate limit: returns 429 after 10 requests/hour per IP — **(Phase 8 — cloud only, skip locally)**
- [ ] Register returns 400 for name longer than 100 characters
- [ ] POST request larger than 1MB returns 413
- [ ] Protected endpoints return 401 without JWT token

## API tests (Level 2 — Cucumber + Rest Assured)

Feature files live in `src/test/resources/features/`.
Step definitions use Rest Assured against the running Spring Boot app
with Testcontainers providing real PostgreSQL and Kafka.

```gherkin
Feature: Authentication

  Scenario: Successful registration
    When I register with name "Bruno" email "bruno@email.com" password "password123"
    Then I receive status 201
    And the response contains id and email

  Scenario: Cannot register with duplicate email
    Given a user exists with email "bruno@email.com"
    When I register with email "bruno@email.com" password "password123"
    Then I receive status 409

  Scenario: Cannot register with short password
    When I register with email "new@email.com" password "short"
    Then I receive status 400

  Scenario: Successful login returns JWT and sets cookie
    Given a user exists with email "bruno@email.com" password "password123"
    When I login with email "bruno@email.com" password "password123"
    Then I receive status 200
    And the response contains accessToken
    And the response sets a refreshToken httpOnly cookie

  Scenario: Login with wrong password returns 401
    Given a user exists with email "bruno@email.com"
    When I login with email "bruno@email.com" password "wrongpassword"
    Then I receive status 401

  Scenario: Login with unknown email returns 401
    When I login with email "unknown@email.com" password "password123"
    Then I receive status 401

  Scenario: Deactivated user cannot login
    Given a deactivated user exists with email "inactive@email.com"
    When I login with email "inactive@email.com" password "password123"
    Then I receive status 401

  Scenario: Protected endpoint requires JWT
    When I call GET /api/users/me without a token
    Then I receive status 401

  Scenario: Refresh token rotates on use
    Given I am logged in as "bruno@email.com"
    When I call POST /api/users/refresh
    Then I receive a new accessToken
    And a new refreshToken cookie is set

  Scenario: Password reset flow end to end
    Given a user exists with email "bruno@email.com"
    When I request a password reset for "bruno@email.com"
    Then I receive status 204
    When I confirm the reset with a valid token and new password "newpassword123"
    Then I receive status 204
    And I can login with "newpassword123"
    And I cannot login with the old password

  # Rate limit — cloud only (Phase 8)
  # Not testable locally — enforced by APIM only
  # Scenario: Register rate limit
  #   When I send 11 register requests from the same IP within an hour
  #   Then the 11th request receives status 429
```

## Error handling — RFC 9457 Problem Details

All errors follow **RFC 9457 (Problem Details for HTTP APIs)** — native in Spring Boot 4.1 via `ProblemDetail`.

```java
// exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        );
        pd.setType(URI.create("urn:bravoscribe:error:validation-failed"));
        pd.setProperty("errors", ex.getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList());
        return pd;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            ex.getStatusCode(), ex.getReason()
        );
        pd.setType(URI.create("urn:bravoscribe:error:" +
            ex.getStatusCode().toString().toLowerCase().replace(" ", "-")));
        return pd;
    }
}
```

**Standard error response shape:**
```json
{
  "type": "urn:bravoscribe:error:validation-failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/journal/entries",
  "errors": ["content: size must be between 1 and 10000"]
}
```

**Error types per service:**

| Status | type URI | When |
|---|---|---|
| 400 | `urn:bravoscribe:error:validation-failed` | DTO validation failure |
| 401 | `urn:bravoscribe:error:unauthorized` | Missing or invalid JWT |
| 422 | `urn:bravoscribe:error:invalid-credentials` | Wrong email or password |
| 403 | `urn:bravoscribe:error:forbidden` | Valid JWT but wrong role |
| 404 | `urn:bravoscribe:error:not-found` | User not found |
| 409 | `urn:bravoscribe:error:email-already-exists` | Duplicate registration |
| 429 | `urn:bravoscribe:error:rate-limit-exceeded` | Too many requests (Phase 8) |

## Spring Boot Actuator

All three services include `spring-boot-starter-actuator` for health checks and metrics.

### Maven dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Configuration

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized   # full details only for authenticated requests
  health:
    defaults:
      enabled: true
```

### Exposed endpoints

| Endpoint | Used by | What it shows |
|---|---|---|
| `GET /actuator/health` | Azure App Service health check | `{ status: "UP" }` — service + DB + Kafka liveness |
| `GET /actuator/info` | Monitoring | App name, version, Java version |
| `GET /actuator/metrics` | Application Insights | JVM, HTTP, cache, Kafka metrics |

### Security

Actuator endpoints are **not** protected by JWT in `SecurityConfig`:

```java
// SecurityConfig.java
.requestMatchers("/actuator/**").permitAll()
```

> **Note:** In production (Azure) the Actuator port is kept internal via APIM — 
> `/actuator/**` is never exposed through the public API gateway.

> **Swagger UI / OpenAPI** are also excluded from JWT authentication in `SecurityConfig` — see **LESSONS_LEARNED.md L7**:
> ```java
> .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
> ```
> In production (Azure) these paths are blocked at APIM level — never exposed through
> the public gateway. In local dev they are accessible directly on the service port.

### Azure App Service health check

In Phase 8, configure Azure App Service health check path:
```
Health probe path: /actuator/health
```

This allows Azure to automatically restart the container if the service becomes unhealthy.

## Flyway repair strategy

### Local development — manual repair

If a migration fails during local development:

```bash
# 1. Fix the SQL error in the migration file
# 2. Repair the failed migration record
./mvnw flyway:repair

# 3. Re-run migrations
./mvnw flyway:migrate

# 4. Restart the service
docker-compose restart [service-name]
```

Flyway marks the failed row in `flyway_schema_history` as repaired.
Spring Boot will then start normally on next launch.

### Cloud (Azure Phase 8) — automatic repair

Add to `application.yml` for cloud profile only:

```yaml
spring:
  flyway:
    repair-on-migrate: true   # auto-repair before each migration attempt
```

**Why cloud only:** Azure App Service auto-restarts on failure. Without
`repair-on-migrate: true` a failed migration leaves the service in a crash
loop requiring manual intervention via Azure Portal. With it, the service
self-recovers once the fixed migration file is deployed.

**Risk:** if the migration SQL is still broken after deploy, the service
will still crash — repair only clears the history record, not the SQL error.
Always test migrations locally before deploying to Azure.

## Test coverage — JaCoCo

Minimum coverage enforced on service layer only. Controllers are covered by Level 2
Cucumber tests. Repositories, DTOs, and the main class are excluded.

```xml
<!-- Add to pom.xml build/plugins -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <configuration>
        <excludes>
            <exclude>**/controller/**</exclude>
            <exclude>**/repository/**</exclude>
            <exclude>**/dto/**</exclude>
            <exclude>**/entity/**</exclude>
            <exclude>**/*Application.class</exclude>
        </excludes>
    </configuration>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <includes>
                            <include>com.bravoscribe.userservice.service.*</include>
                        </includes>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.90</minimum> <!-- 90% — auth/security critical -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> **90% threshold** — User Service handles JWT, authentication, and password reset.
> Security-critical code requires stricter coverage than other services.
> Build fails if service layer drops below 90%.
> Report generated at `target/site/jacoco/index.html`.

## AI Regeneration Prompt
```
Using SYSTEM_SPEC.md and services/user-service/SPEC.md, generate the complete Spring Boot project for the User Service.

Include:
- User, RefreshToken, and PasswordResetToken entities
- All DTOs (Java 25 records)
- Repositories
- UserService, AuthService, JwtService (RS256)
- All 12 controllers with correct HTTP methods and status codes
- @RestControllerAdvice for error handling
- SecurityConfig with JWT filter (RS256 public key only for verification)
  Must permit without JWT: `/api/users/register`, `/api/users/login`, `/api/users/refresh`,
  `/api/users/password-reset/**`, `/actuator/**`,
  `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`
  — otherwise Swagger UI returns 401 and cannot be used for testing
- Flyway migrations V1–V4 (V4 adds preference fields: reminderTime, weeklySummaryEnabled)
- Kafka producers for users.user.registered, users.user.deactivated, users.password.reset.requested
- JUnit 5 unit tests for UserService and AuthService (90% service layer coverage — JaCoCo)
- Level 2 API tests: Cucumber feature files matching all scenarios in the API tests section,
  Rest Assured step definitions, CucumberRunner for JUnit 5,
  Testcontainers setup with PostgreSQL and Kafka
- JaCoCo Maven plugin configured per the Test coverage section (90% threshold)
- Maven wrapper (`.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd`) — required by Dockerfile

**Critical test setup constraints:**

- Use **Rest Assured 6.0.0** (not 5.x) — see **LESSONS_LEARNED.md L2**
  (5.x has a metaclass reflection NPE on Java 25 for GET, PUT, DELETE)

- PostgreSQL Testcontainer must call `.withInitScript("db/test-schema.sql")` — the file
  `src/test/resources/db/test-schema.sql` creates the schema and runs `GRANT CREATE` as superuser
  before Flyway migrations run

- `src/test/resources/application.yml` must explicitly set `jwt.access-expiry-seconds`,
  `jwt.refresh-expiry-days`, `jwt.password-reset-expiry-minutes` — see **LESSONS_LEARNED.md L4**
  (`@DynamicPropertySource` does not inherit env-var fallback defaults)

- The "a registered user" Cucumber Background step must **delete-then-create** — see **LESSONS_LEARNED.md L5**
  (stale DB state from password-change scenarios breaks subsequent Background steps)
```
