# Phase 4 — Notification Service Implementation Plan

## Context

Phases 1–3 are complete and verified. Phase 4 is the Notification Service: a Spring Boot
microservice that is **entirely event-driven** — no REST endpoints exposed to clients.
It consumes 4 Kafka topics, checks Redis for idempotency, sends emails via SendGrid,
and persists an `EmailLog` document to MongoDB after every send attempt.

---

## Readiness Check — We Are Good to Go ✅

| Check | Status | Notes |
|---|---|---|
| Spec complete | ✅ | `services/notification-service/SPEC.md` (all sections filled) |
| Infrastructure | ✅ | Docker Compose: Redis, Kafka, MongoDB all running |
| Dockerfile | ✅ | `services/notification-service/Dockerfile` already exists (port 8083) |
| Template to follow | ✅ | Journal Service (`services/journal-service/`) is the reference |
| Lessons learned | ✅ | 5 Phase 3 lessons documented and applicable to Phase 4 |
| Missing | ⚠️ | No `pom.xml` yet — first file to create |

---

## Lessons Learned to Apply (from Phases 2 & 3)

| # | Rule | Why |
|---|---|---|
| L1 | **Jackson 3 imports**: use `tools.jackson.databind.ObjectMapper` | Spring Boot 4.1 ships Jackson 3; `com.fasterxml.*` imports cause `ClassNotFoundException` at runtime |
| L2 | **StringRedisTemplate for strings**: no custom serializer needed for idempotency keys | Serializer lesson from Phase 3 applies to `CacheManager` / POJOs only; `StringRedisTemplate` uses `StringRedisSerializer` by default — no extra config |
| L3 | **Test env vars must be explicit in `application.yml`** | `@DynamicPropertySource` doesn't inherit `${VAR:default}` fallbacks — every key used in test code must be present explicitly |
| L4 | **Kafka local**: `application-local.yml` sets `localhost:29092`; `application.yml` default is `kafka:9092` | Both listeners are advertised in Docker Compose; mismatch causes consumer to hang silently |
| L5 | **curl.exe on Windows**: always `curl.exe`, never `curl` | `curl` is `Invoke-WebRequest` in PowerShell; it fails silently with wrong output format |

---

## What's New vs. Journal Service (key differences)

| Aspect | Journal Service | Notification Service |
|---|---|---|
| REST endpoints | Yes (`/api/journal/...`) | **None** — Kafka-driven only |
| PostgreSQL / Flyway | Yes | **No** — no relational DB |
| Redis usage | `@EnableCaching` + `CacheManager` | **Direct `StringRedisTemplate`** for idempotency keys |
| MongoDB | No | **Yes** — `email_logs` collection |
| JWT security | RS256 resource server | **Minimal** — no client endpoints; actuator only |
| Kafka | Producer + Consumer | **Consumer only** (4 topics) |
| SendGrid | No | **Yes** — HTTP via `RestClient` |
| InetAddressFilter | No | **Yes** — allow `api.sendgrid.com` only (SSRF mitigation) |
| Swagger / OpenAPI | Yes | **No** — no REST surface |
| Cucumber / Rest Assured | Yes | **No** — replaced by Testcontainers integration tests |
| JaCoCo threshold | 80% | 80% (same) |

---

## Package Structure

```
com.bravoscribe.notificationservice/
├── NotificationServiceApplication.java
├── config/
│   ├── SecurityConfig.java             actuator permitAll, all else denyAll
│   ├── RedisConfig.java                StringRedisTemplate bean
│   └── SendGridConfig.java             RestClient bean (baseUrl = https://api.sendgrid.com)
├── consumer/
│   ├── UserRegisteredConsumer.java     @KafkaListener — users.user.registered
│   ├── PasswordResetRequestedConsumer.java  @KafkaListener — users.password.reset.requested
│   ├── JournalEntryCreatedConsumer.java  @KafkaListener — journal.entry.created
│   └── JournalEntryUpdatedConsumer.java  @KafkaListener — journal.entry.updated (discard)
├── document/
│   └── EmailLog.java                   @Document(collection = "email_logs") record
├── dto/
│   ├── UserRegisteredEvent.java        record  { eventId, userId, email, name }
│   ├── PasswordResetRequestedEvent.java  record { eventId, userId, email, rawToken }
│   └── JournalEntryCreatedEvent.java   record  { eventId, userId, email }
├── exception/
│   └── GlobalExceptionHandler.java     @RestControllerAdvice (minimal — actuator only)
├── repository/
│   └── EmailLogRepository.java         MongoRepository — findByRecipientEmail, findByStatus
└── service/
    └── NotificationService.java        idempotency check + email send + MongoDB log
```

---

## Implementation Order

### 1. `pom.xml` + Maven wrapper

Mirror journal-service `pom.xml` with these changes:

**Remove** (not needed in notification-service):
- `spring-boot-starter-web` → replaced by `spring-boot-starter` (no HTTP server needed for consumers, but keep web for actuator)
- `spring-boot-starter-data-jpa` + `postgresql` + Flyway
- `spring-boot-starter-oauth2-resource-server` (no JWT resource server)
- `springdoc-openapi-starter-webmvc-ui` (no REST endpoints, no Swagger)
- `testcontainers:postgresql`
- Cucumber + Rest Assured

**Add**:
- `spring-boot-starter-data-mongodb`
- `testcontainers:mongodb`

**Keep**:
- `spring-boot-starter-web` (needed for Actuator over HTTP)
- `spring-boot-starter-security` (SecurityConfig for actuator)
- `spring-boot-starter-data-redis`
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus`
- `spring-kafka` + `spring-kafka-test`
- `testcontainers:testcontainers` + `testcontainers:junit-jupiter` + `testcontainers:kafka`
- `logstash-logback-encoder`
- `spring-boot-starter-test`

**Change**:
- `artifactId` → `notification-service`
- JaCoCo `<include>` → `com.bravoscribe.notificationservice.service.*`
- JaCoCo `<excludes>` → add `**/consumer/**`, `**/document/**`; remove `**/controller/**`, `**/entity/**`, `**/kafka/event/**`
- Remove `maven-failsafe-plugin` (no Cucumber integration tests)
- `JaCoCo version` stays `0.8.13`

**Maven wrapper**: copy `mvnw`, `mvnw.cmd`, `.mvn/` from journal-service.
**Critical**: immediately run `git update-index --chmod=+x services/notification-service/mvnw` after copying.

---

### 2. Application configuration

**`src/main/resources/application.yml`**:
```yaml
server:
  port: 8083

spring:
  application:
    name: notification-service
  threads:
    virtual:
      enabled: true
  data:
    redis:
      url: ${REDIS_URL:redis://localhost:6379}
    mongodb:
      uri: ${MONGODB_URI:mongodb://mongo:mongo@localhost:27017/bravoscribe?authSource=admin}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
    consumer:
      group-id: notification-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
  http:
    client:
      inet-address-filter:
        enabled: true
        allow: api.sendgrid.com   # SSRF mitigation — SendGrid only

notification:
  sendgrid:
    api-key: ${SENDGRID_API_KEY:}
  from-email: ${FROM_EMAIL:noreply@bravoscribe.com}
  frontend-url: ${FRONTEND_URL:http://localhost:5173}

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: when-authorized
  tracing:
    sampling:
      probability: 1.0
```

**`src/main/resources/application-local.yml`**:
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:29092
  data:
    redis:
      url: redis://localhost:6379
    mongodb:
      uri: mongodb://mongo:mongo@localhost:27017/bravoscribe?authSource=admin
logging:
  pattern:
    console: "%d{HH:mm:ss} [%highlight(%-5level)] %cyan(%logger{20}) - %msg%n"
```

**`src/test/resources/application.yml`** (Testcontainers sets URLs via `@DynamicPropertySource`):
```yaml
spring:
  application:
    name: notification-service
  kafka:
    consumer:
      group-id: notification-service-test
      auto-offset-reset: earliest
  http:
    client:
      inet-address-filter:
        enabled: false   # disabled in tests — no real SendGrid calls
notification:
  sendgrid:
    api-key: test-api-key
  from-email: noreply@bravoscribe.com
  frontend-url: http://localhost:5173
```

---

### 3. Domain — `EmailLog` document + `EmailLogRepository`

```java
// document/EmailLog.java
@Document(collection = "email_logs")
public record EmailLog(
    @Id String id,
    String eventId,
    String topic,
    String recipientEmail,
    String subject,
    Instant sentAt,
    String status,            // "sent" | "failed"
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

---

### 4. Event DTOs (Java 25 records)

One record per event shape consumed from Kafka. Deserialized from JSON using Jackson 3.

```java
// dto/UserRegisteredEvent.java
public record UserRegisteredEvent(String eventId, String userId, String email, String name) {}

// dto/PasswordResetRequestedEvent.java
public record PasswordResetRequestedEvent(String eventId, String userId, String email, String rawToken) {}

// dto/JournalEntryCreatedEvent.java
public record JournalEntryCreatedEvent(String eventId, String userId, String email) {}
```

> Check what fields User Service and Journal Service actually put in each event payload before
> finalising these — look at the producer code in `services/user-service/` and `services/journal-service/`.

---

### 5. Config beans

**`RedisConfig.java`** — `StringRedisTemplate` for idempotency keys (string → string, no custom serializer):
```java
@Configuration
public class RedisConfig {
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
```

**`SendGridConfig.java`** — `RestClient` bean (InetAddressFilter applies at HTTP client level via `application.yml`):
```java
@Configuration
public class SendGridConfig {
    @Value("${notification.sendgrid.api-key}")
    private String apiKey;

    @Bean
    public RestClient sendGridClient(RestClient.Builder builder) {
        return builder
            .baseUrl("https://api.sendgrid.com")
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
```

---

### 6. `NotificationService` — the core

Single service class covering all three concerns: idempotency, email send, MongoDB log.

**Idempotency key format**: `notify:processed:{topic}:{eventId}` — TTL 7 days (604800 seconds)

**Methods**:
- `processUserRegistered(UserRegisteredEvent)` → send welcome email
- `processPasswordResetRequested(PasswordResetRequestedEvent)` → send reset email
- `processJournalEntryCreated(JournalEntryCreatedEvent)` → send confirmation email
- `processJournalEntryUpdated(String eventId, String topic)` → store idempotency key only, no email
- `private boolean isAlreadyProcessed(String topic, String eventId)` → `stringRedisTemplate.hasKey(key)`
- `private void markProcessed(String topic, String eventId)` → `stringRedisTemplate.opsForValue().set(key, "1", Duration.ofDays(7))`
- `private void sendEmail(String topic, String eventId, String to, String subject, String body)` → calls `sendGridClient`, saves `EmailLog`

**Password reset link format** (hash fragment, NOT query param):
```
{frontendUrl}/reset-password#token={rawToken}
```

**Entry confirmation email**: always sent in Phase 1. User preference opt-out is Phase 2.

**Custom Micrometer metrics** (inject `MeterRegistry`):
```java
Counter.builder("notifications.sent").register(meterRegistry).increment();
Counter.builder("notifications.failed").register(meterRegistry).increment();
```

**Logging** (per SYSTEM_SPEC.md §7.3 — never log email content, names, or addresses):
```java
log.info("Email sent — event: {} userId: {}", topic, userId);
log.warn("Duplicate event skipped — topic: {} eventId: {}", topic, eventId);
log.error("Email delivery failed — userId: {} provider: sendgrid — {}", userId, ex.getMessage());
log.error("Kafka consumer error — topic: {} — {}", topic, ex.getMessage());
```

---

### 7. Kafka consumers

Each consumer:
1. Deserializes JSON payload using `ObjectMapper` (Jackson 3: `tools.jackson.databind.ObjectMapper`)
2. Calls the corresponding `NotificationService` method
3. Catches all exceptions and logs them — **never rethrows** (a rethrow causes Kafka to retry indefinitely)

```java
@Component
public class UserRegisteredConsumer {
    @KafkaListener(topics = "users.user.registered", groupId = "notification-service")
    public void consume(String message) {
        try {
            var event = objectMapper.readValue(message, UserRegisteredEvent.class);
            notificationService.processUserRegistered(event);
        } catch (Exception e) {
            log.error("Kafka consumer error — topic: users.user.registered — {}", e.getMessage());
        }
    }
}
```

`JournalEntryUpdatedConsumer` only calls `markProcessed()` — no email, no SendGrid call.

---

### 8. `SecurityConfig`

No user-facing endpoints — only Actuator:
```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/**").permitAll()
    .anyRequest().denyAll()
)
.csrf(AbstractHttpConfigurer::disable);
```

No JWT resource server configuration needed.

---

### 9. `GlobalExceptionHandler`

Minimal — only handles exceptions for the Actuator surface (unlikely in practice):
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        pd.setType(URI.create("https://bravoscribe.com/errors/internal-server-error"));
        return pd;
    }
}
```

---

### 10. Logback (`logback-spring.xml`)

Copy from journal-service — same JSON config via `logstash-logback-encoder`. Only change is the `<springProperty name="service" default="notification-service"/>`.

---

### 11. Unit tests

**`NotificationServiceTest.java`** — JUnit 5 + Mockito, mocking `StringRedisTemplate`, `RestClient`, `EmailLogRepository`, `MeterRegistry`.

Package declaration: `package com.bravoscribe.notificationservice.service;` (for package-private access).

| Test | What it verifies |
|---|---|
| `processUserRegistered_sendsWelcomeEmail` | Calls RestClient, saves EmailLog status="sent" |
| `processUserRegistered_skipsDuplicate` | Redis key present → no RestClient call, logs WARN |
| `processPasswordResetRequested_sendsResetEmailWithHashFragment` | Email body contains `#token=`, not `?token=` |
| `processJournalEntryCreated_sendsConfirmationEmail` | Calls RestClient, saves EmailLog status="sent" |
| `processJournalEntryUpdated_storesIdempotencyKeyOnly` | Marks processed in Redis, no email sent |
| `sendEmail_onSendGridFailure_logsFailedEmailLog` | RestClient throws → EmailLog status="failed" with errorMessage |
| `isAlreadyProcessed_returnsTrueWhenRedisKeyExists` | `hasKey()` returns true → method returns true |

---

### 12. Integration tests

**`NotificationConsumerIT.java`** — Testcontainers (Kafka + Redis + MongoDB) + MockRestServiceServer for SendGrid.

```java
@SpringBootTest
@Testcontainers
class NotificationConsumerIT {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("spring.data.redis.url", () -> "redis://localhost:" + redis.getMappedPort(6379));
        r.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }
}
```

| Test | What it verifies |
|---|---|
| `userRegisteredEvent_triggersWelcomeEmail` | Produce to `users.user.registered` → SendGrid called, EmailLog in MongoDB |
| `duplicateUserRegisteredEvent_skipped` | Second identical message → no second SendGrid call |
| `passwordResetEvent_triggersResetEmailWithHashFragment` | Email body has `#token=`, not `?token=` |
| `journalEntryCreatedEvent_triggersConfirmationEmail` | Email sent to correct address |
| `journalEntryUpdatedEvent_noEmailSent` | No SendGrid call, idempotency key set in Redis |

---

## Critical Implementation Details

### Idempotency key
```java
private String idempotencyKey(String topic, String eventId) {
    return "notify:processed:" + topic + ":" + eventId;
}
```

### SendGrid v3 mail/send payload (minimal)
```json
{
  "personalizations": [{ "to": [{ "email": "user@example.com" }] }],
  "from": { "email": "noreply@bravoscribe.com" },
  "subject": "Welcome to Bravoscribe",
  "content": [{ "type": "text/plain", "value": "..." }]
}
```

### Password reset link — hash fragment (not query param)
```java
String link = frontendUrl + "/reset-password#token=" + event.rawToken();
// ✅ #token= — never sent to server, never in logs
// ❌ ?token= — appears in APIM and Application Insights logs
```

### Event payload compatibility
Before generating, inspect the actual Kafka producer code to confirm field names:
- `UserService/kafka/UserEventProducer.java` — what does the `users.user.registered` payload look like?
- `UserService/kafka/UserEventProducer.java` — what does the `users.password.reset.requested` payload look like?
- `JournalService/kafka/JournalEventProducer.java` — what does the `journal.entry.created` payload look like?

Align `dto/` records exactly with those producer payloads.

### No NoSQL injection
Always use derived queries (`findByRecipientEmail`, `findByStatus`) — never `@Query` with string concatenation on user-supplied fields (per SPEC.md safety note).

---

## Open Decisions

### `journal.entry.created` — confirmation email pending

The `journal.entry.created` Kafka payload is `{ eventId, entryId, userId, entryDate }` — no email address, because journal-service stores no user PII. The notification-service currently stores the idempotency key and logs the event but sends no email.

**Two options to resolve before Phase 5 (React) when this becomes user-visible:**

| Option | Change required | Trade-off |
|---|---|---|
| A — Enrich the event | Add `email` field to `JournalEventProducer` in journal-service | Couples journal-service to user identity; denormalises PII into the event stream |
| B — Internal lookup API | Add `GET /internal/users/{id}/email` to user-service; notification-service calls it | Extra HTTP hop; requires `X-Internal-Api-Key` auth between services; decoupled event payload |

Option B is architecturally cleaner (events stay minimal, PII stays in user-service) but adds an inter-service HTTP call. Option A is simpler to implement now.

**Current state:** idempotency key is stored, no email is sent, no log entry is written. The consumer will need to be updated once this decision is made.

---

## Verification (Definition of Done)

After `./mvnw clean verify` passes:

1. `docker-compose -f infra/docker-compose.yml --env-file .env up --profile services` starts notification-service on port 8083 with no errors
2. All unit tests pass; JaCoCo 80% service layer threshold met
3. All integration tests green (Testcontainers)
4. Actuator health check: `curl.exe http://localhost:8083/actuator/health` → `{"status":"UP"}`
5. End-to-end smoke tests (Docker Compose running):
   - Register new user → welcome email received in SendGrid Activity Feed
   - Request password reset → email received with `#token=` link (not `?token=`)
   - Create first journal entry → confirmation email received
   - Publish same event twice → only one email sent (idempotency)
   - Check MongoDB `email_logs` collection → documents present with correct status
