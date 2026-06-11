# Notification Service — Service Specification

> See `SYSTEM_SPEC.md` for system-wide conventions.

## Responsibility
Sends notifications to users triggered by journal events. Driven entirely by Azure Event Hubs — no REST endpoints exposed to clients.

## Technology
| Property | Value |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1 |
| Port (local) | 8083 (internal only) |
| Datastore | Redis (idempotency keys) · MongoDB (email delivery log) |
| Email provider | SendGrid |

## Events consumed (Azure Event Hubs)
| Topic | Notification sent |
|---|---|
| `users.user.registered` | Welcome email |
| `users.password.reset.requested` | Password reset email with link |
| `journal.entry.created` | "Great job! First entry of the day" confirmation (optional, user can disable) |
| `journal.entry.updated` | No action — event consumed and discarded (idempotency key stored to prevent reprocessing) |

### Password reset email
```
Subject: Reset your Bravoscribe password
Body:
  Someone requested a password reset for your account.
  Click the link below to set a new password.
  This link expires in 15 minutes.

  [Reset password] → {FRONTEND_URL}/reset-password#token={rawToken}

  If you didn't request this, ignore this email.
  Your password will not change.
```
The raw token comes directly from the event payload — the Notification
Service never touches the database for this flow.

> **Security note:** the reset link uses a hash fragment (`#token`)
> not a query param (`?token`). Hash fragments are never sent to the
> server, never appear in Referer headers, and never appear in server logs.
> This prevents token leakage through Azure APIM or Application Insights logs.

## Phase 2 — Scheduled notifications

> Implement these after the core event-driven notifications are working.

### Service-to-service authentication (Phase 2 only)
The daily reminder and weekly summary jobs require the Notification Service
to call User Service and Journal Service APIs. These internal calls must be
authenticated to prevent any service from calling any other service freely.

**Implementation:** use a shared internal API key in a custom header:
```
X-Internal-Api-Key: {secret from Key Vault}
```
- Key Vault secret name: `internal-api-key`
- User Service and Journal Service expose dedicated internal endpoints
  (prefixed `/internal/`) that validate this header
- APIM blocks all `/internal/` routes from external clients
- Rotate the key via Key Vault — no code changes needed

### Daily reminder
- **Schedule:** cron every minute (`0 * * * * *`)
- **Logic:** query User Service for users whose `reminderTime` matches current UTC time and have no entry for today (check via Journal Service `GET /api/journal/entries/dates`)
- **Action:** send push notification via Firebase FCM
- **Note:** requires `reminderTime` field on User entity (User Service V4 migration — Phase 2 preferences)

### Weekly summary email
- **Schedule:** cron every Sunday at 08:00 UTC (`0 8 * * 0`)
- **Logic:** query User Service for users with `weeklySummaryEnabled = true`, fetch their entry count for the past 7 days from Journal Service
- **Action:** send summary email via SendGrid
- **Content:** "You wrote N entries this week — keep it up!" with streak info
- **Note:** requires `weeklySummaryEnabled` field on User entity (User Service V4 migration — Phase 2 preferences)

## Idempotency
Each event processed exactly once using a Redis key:
```
notify:processed:{topic}:{eventId}  TTL: 7 days
```

## Environment variables
```env
REDIS_URL=rediss://[redis-name].redis.cache.windows.net:6380
REDIS_PASSWORD=                     # from Key Vault
KAFKA_BOOTSTRAP_SERVERS=[ns].servicebus.windows.net:9093
KAFKA_SASL_JAAS_CONFIG=
MONGODB_URI=                         # from Key Vault (Azure Cosmos DB for MongoDB)
SENDGRID_API_KEY=                   # from Key Vault
FROM_EMAIL=noreply@[yourdomain].com
FRONTEND_URL=                        # used to build password reset link: {FRONTEND_URL}/reset-password#token={rawToken}
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
        pd.setType(URI.create("https://bravoscribe.com/errors/validation-failed"));
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
        pd.setType(URI.create("https://bravoscribe.com/errors/" +
            ex.getStatusCode().toString().toLowerCase().replace(" ", "-")));
        return pd;
    }
}
```

**Standard error response shape:**
```json
{
  "type": "https://bravoscribe.com/errors/validation-failed",
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

> Notification Service has no REST endpoints — no HTTP error responses.
> Errors are logged and stored in MongoDB `email_logs` with `status: "failed"`.

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

> **MongoDB safety:** always use `MongoRepository` derived queries
> (e.g. `findByRecipientEmail`). Never use `@Query` with string concatenation
> on user-supplied fields — this prevents NoSQL injection attacks.

> **Note:** In production (Azure) the Actuator port is kept internal via APIM — 
> `/actuator/**` is never exposed through the public API gateway.

> **Swagger UI / OpenAPI** are also excluded from JWT authentication in `SecurityConfig`:
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

## Spring Boot 4.1 — relevant features

**InetAddressFilter (SSRF mitigation):** notification-service makes outbound HTTP calls
to SendGrid. Enable in `application.yml` to restrict outbound connections:

```yaml
spring:
  http:
    client:
      inet-address-filter:
        enabled: true
        allow: api.sendgrid.com
```

> Note: The Application Insights OpenTelemetry Java Agent operates at JVM level via
> `-javaagent` — it is independent of Spring Boot version and unaffected by 4.1's
> built-in OTel starter improvements.

## Email delivery log (MongoDB)

Every email sent is persisted as a document in MongoDB collection `email_logs`.
This enables auditing, debugging, and retry tracking.

### Document schema

```json
{
  "_id": "ObjectId",
  "eventId": "uuid",
  "topic": "users.user.registered | users.password.reset.requested | journal.entry.created",
  "recipientEmail": "user@example.com",
  "subject": "Welcome to Bravoscribe",
  "sentAt": "2026-06-11T12:00:00Z",
  "status": "sent | failed",
  "sendGridMessageId": "abc123",
  "retryCount": 0,
  "errorMessage": null
}
```

Each email type has a **different payload shape** — exactly why MongoDB is the right fit here.
Schema migrations are never needed when new notification types are added.

### Spring Data MongoDB

```java
// domain/EmailLog.java
@Document(collection = "email_logs")
public record EmailLog(
    @Id String id,
    String eventId,
    String topic,
    String recipientEmail,
    String subject,
    Instant sentAt,
    String status,           // "sent" | "failed"
    String sendGridMessageId,
    int retryCount,
    String errorMessage
) {}

// repository/EmailLogRepository.java
public interface EmailLogRepository extends MongoRepository<EmailLog, String> {
    List<EmailLog> findByRecipientEmail(String email);
    List<EmailLog> findByStatus(String status);
}
```

### Cloud equivalent

Local: `mongo:7` Docker container
Cloud: **Azure Cosmos DB for MongoDB API** (serverless) — no code change, connection string only.
Cost for a personal app: ~$0.25–$1.25/month at $0.25 per million RUs consumed.

## Test coverage — JaCoCo

```xml
<!-- Add to pom.xml build/plugins -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <configuration>
        <excludes>
            <exclude>**/consumer/**</exclude>
            <exclude>**/repository/**</exclude>
            <exclude>**/dto/**</exclude>
            <exclude>**/document/**</exclude>
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
                            <include>com.bravoscribe.notificationservice.service.*</include>
                        </includes>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum> <!-- 80% — email/idempotency logic -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> **80% threshold** — email sending and idempotency logic.
> Build fails if coverage drops below 80%.
> Report generated at `target/site/jacoco/index.html`.

## AI Regeneration Prompt
```
Using SYSTEM_SPEC.md and services/notification-service/SPEC.md, generate the complete Spring Boot project for the Notification Service.

Include:
- Kafka consumers for 4 topics (NOT users.user.deactivated — that is Journal Service only):
  users.user.registered, users.password.reset.requested,
  journal.entry.created, journal.entry.updated (discard)
- Redis idempotency logic: key = notify:processed:{topic}:{eventId}, TTL 7 days
- Email sending via SendGrid (SENDGRID_API_KEY from env)
- EmailLog @Document saved to MongoDB after every send attempt
  (status: "sent" or "failed", retryCount, sendGridMessageId, errorMessage)
- Spring Boot 4.1 InetAddressFilter for SSRF mitigation on SendGrid calls
  (allow: api.sendgrid.com only)
- Password reset email must use #token= hash fragment (NOT ?token=)
- JUnit 5 unit tests for email sending and idempotency logic (80% service layer — JaCoCo)
- JaCoCo Maven plugin configured per the Test coverage section (80% threshold)
- No REST endpoints — this service is internal Kafka-driven only
```

## Logging

Follow the logging rules in `SYSTEM_SPEC.md` section 7.2. Key events to log:

```
INFO  Email sent — event: {topic} userId: {id}
WARN  Duplicate event skipped — topic: {topic} eventId: {id}
ERROR Email delivery failed — userId: {id} provider: sendgrid — {exception}
ERROR Kafka consumer error — topic: {topic} — {exception}
```

**Never log:** email content, user names, or email addresses.

## Definition of Done

Before moving to frontends, verify all of these:

- [ ] `./mvnw clean verify` passes, all unit tests pass, JaCoCo 80% threshold met
- [ ] Service starts locally with `docker-compose up`
- [ ] Welcome email received after user registration
- [ ] Redis idempotency verified (duplicate event does not send duplicate email)
- [ ] Kafka consumer verified for all listed topics
- [ ] Password reset email contains correct link with raw token
- [ ] Password reset email sent within 30 seconds of event
