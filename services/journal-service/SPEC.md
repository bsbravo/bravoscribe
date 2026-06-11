# Journal Service — Service Specification

> See `SYSTEM_SPEC.md` for system-wide conventions.

## Responsibility
Manages all journal entries. Each entry belongs to exactly one user and is private. Users can create one entry per day and edit entries from previous days.

## Technology
| Property | Value |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1 |
| Port (local) | 8082 |
| Database | Azure Database for PostgreSQL Flexible Server — db: `journal` |

## Sorting conventions

Sort order is always hardcoded server-side — the client never controls sort.

| Endpoint | Sort | Reason |
|---|---|---|
| `GET /api/journal/entries` | `entryDate DESC` | Most recent first — standard journal view |
| `GET /api/journal/entries/dates` | `entryDate ASC` | Chronological for calendar rendering |
| `GET /api/journal/entries/export` | `entryDate ASC` | Chronological — better for AI reading |
| `GET /api/journal/tags` | `name ASC` | Alphabetical — easier to find tags |
| `GET /api/journal/stats` | n/a | Single object, no sort |

**Implementation rule:** always use `PageRequest.of(page, size, Sort.by("entryDate").descending())`
in the controller. Never accept a `?sort=` query parameter from the client.
Always cap page size: `int effectiveSize = Math.min(size, 100)` — max 100 entries per page.

## Optional fields behaviour

`title`, `mood` and `tags` are all optional. The only required fields are `entryDate` and `content`.

| Field | If null/empty | Flyway column |
|---|---|---|
| `title` | Stored as null — clients show first line of content as preview | `VARCHAR(255) NULL` |
| `mood` | Stored as null — clients show no emoji | `VARCHAR(10) NULL` |
| `tags` | Empty join table — clients show no tags row | n/a |

**DTO validation:**
```java
public record CreateEntryRequest(
    @NotNull @PastOrPresent LocalDate entryDate,       // cannot be a future date
    @Size(max = 255) String title,                     // optional
    @NotBlank @Size(max = 10000) String content,       // required, max 10,000 chars
    Mood mood,                                         // optional
    @Size(max = 10) List<UUID> tagIds                  // optional, max 10 tags
) {}

public record CreateTagRequest(
    @NotBlank @Size(max = 50) String name              // max 50 chars
) {}
```
Returns `400 Bad Request` if:
- `content` exceeds 10,000 characters
- `tagIds` contains more than 10 entries
- tag `name` exceeds 50 characters

> **Mood storage:** use `@Enumerated(EnumType.STRING)` — stores `'GREAT'`, `'GOOD'` etc.
> Never use `EnumType.ORDINAL` — fragile if enum order ever changes.
> Do NOT create a separate moods table — moods are fixed by design, not user-configurable.

## Business rules
- A user can have **at most one entry per calendar day** (based on user's local date sent in request)
- A user can only **read and edit their own entries** — `userId` is always taken from the JWT, never from the request body
- Entries are **never hard-deleted** — they are soft-deleted (`deleted = true`)
- Entry content supports plain text (Markdown rendering is handled client-side)

## Entities

### JournalEntry
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| userId | UUID | Owner — from JWT, never from request body |
| entryDate | LocalDate | The day this entry belongs to. Unique per user. |
| title | String | Optional, max 255 chars |
| content | String (TEXT) | The journal body, required. Max 10,000 characters. |
| mood | Enum | `GREAT` `GOOD` `NEUTRAL` `BAD` `TERRIBLE` — optional |
| | | Stored as `VARCHAR(10)` using `@Enumerated(EnumType.STRING)` |
| | | Never use `EnumType.ORDINAL` — fragile if enum order changes |
| deleted | Boolean | Soft delete flag, default false |
| createdAt | Instant | Auto-set on creation |
| updatedAt | Instant | Auto-updated on every edit |

### Tag
| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| userId | UUID | Owner |
| name | String | Unique per user, max 50 chars |

### JournalEntryTag (join table)
| Field | Type |
|---|---|
| entryId | UUID |
| tagId | UUID |

## Endpoints

```
# List entries (paginated, most recent first)
# Optional date range filter for calendar month navigation
# Optional full-text search across title and content
GET /api/journal/entries?page=0&size=31&from=yyyy-MM-dd&to=yyyy-MM-dd&q=search+term
    Returns: 200 Page<JournalEntryResponse>
    Notes:   q max length: 200 characters — @Size(max=200) on request param
             page size capped at 100 server-side: Math.min(size, 100)
             from and to are optional — omit for full history
             when provided, returns only entries within the date range
             q is optional — case-insensitive search on title + content (ILIKE %q%)
             q and from/to can be combined
             MUST use parameterised queries — never string concatenation
             Safe: @Query with @Param('q') using Spring Data JPA named params
             Never: string concatenation like 'WHERE content LIKE %' + q + '%'
             sort: always entryDate DESC — hardcoded, not overridable by client
             implementation: PageRequest.of(page, size, Sort.by("entryDate").descending())
             never use @PageableDefault or allow client sort param for this endpoint

# Get dates with entries (lightweight — for calendar dot markers)
# Returns only dates, no content — used by the calendar component
GET /api/journal/entries/dates?from=yyyy-MM-dd&to=yyyy-MM-dd
    Returns: 200 List<String>  (e.g. ["2026-06-02","2026-06-05","2026-06-06"])
    Notes:   from and to are required
             typical use: from=first day of month, to=last day of month
             cheap query — SELECT entry_date only, no content loaded
             sort: entryDate ASC — chronological order for calendar rendering

# Get single entry by ID
GET /api/journal/entries/{id}
    Returns: 200 JournalEntryResponse
    Error:   404 if not found or not owned by caller

# Get entry by date
GET /api/journal/entries/date/{date}     (date format: yyyy-MM-dd)
    Returns: 200 JournalEntryResponse
    Error:   404 if no entry for that date

# Create entry
POST /api/journal/entries
     Body: { entryDate, title?, content, mood?, tagIds? }
     Returns: 201 JournalEntryResponse
     Error:   409 if entry for that date already exists

# Update entry
PUT /api/journal/entries/{id}
    Body: { title?, content, mood?, tagIds? }
    Returns: 200 JournalEntryResponse
    Error:   404 if not found or not owned by caller

# Soft-delete entry
DELETE /api/journal/entries/{id}
       Returns: 204
       Error:   404 if not found or not owned by caller

# Export entries as zip (contains a single MD file)
# Designed for AI consumption — no user identifying information included
GET /api/journal/entries/export?from=yyyy-MM-dd&to=yyyy-MM-dd
    Returns: application/zip
    Headers: Content-Disposition: attachment;
             filename="journal-export-{from}-{to}.zip"
    Notes:   from and to are required
             max range: 366 days (safety limit — return 400 if exceeded)
             max content per entry: 10,000 chars (enforced by V5)
             worst case payload: 366 × 10,000 chars ≈ 3.66MB text → ~1.5MB zip
             entries ordered oldest first (better for AI reading) — entryDate ASC
             returns 404 if no entries exist in the range
             zip contains one file: journal-export-{from}-{to}.md
             built in memory (not streamed) — acceptable at this scale
             server-side timeout: 30 seconds
             implement with Spring @Async or set request timeout in App Service

# Stats — for profile page
GET /api/journal/stats
    Returns: 200 {
      totalEntries: int,
      totalWords: int,
      currentStreak: int,
      longestStreak: int,
      firstEntryDate: LocalDate nullable
    }
    Notes:   Soft-deleted entries excluded from all counts (totalEntries, totalWords, streaks)
             scoped to authenticated user (userId from JWT)
             totalWords = SUM of word counts across all entries
             word count = content split by whitespace
             streaks calculated server-side

# Tags
GET    /api/journal/tags              → 200 List<TagResponse>
POST   /api/journal/tags              → 201 TagResponse  body: { name }
DELETE /api/journal/tags/{id}         → 204
```

## Export format (MD file inside zip)

The exported MD file is designed to be fed directly to an AI assistant.
It contains **no user-identifying information** (no name, no email, no userId).

```markdown
# Bravoscribe Export
**Period:** {from} to {to}
**Total entries:** {count}
**Exported:** {exportedAt ISO-8601}

> This is a personal journal export. Each entry is written by the
> same person and represents their thoughts, feelings and experiences
> on that day.

---

## {entryDate formatted as yyyy-MM-dd · EEEE}

**Mood:** {mood label} {mood emoji}
**Tags:** {comma-separated tag names}
**Words:** {word count}

### {title}

{content}

---
```

**Zip structure:**
```
bravoscribe-export-2026-06-01-to-2026-06-12.zip
└── bravoscribe-export-2026-06-01-to-2026-06-12.md   ← single markdown file
```

**Zip filename:** `bravoscribe-export-{from}-to-{to}.zip`
**MD filename:** same as zip, with `.md` extension

**Concrete example of the MD file:**
```markdown
# Bravoscribe Export
**Period:** 2026-06-01 to 2026-06-12
**Total entries:** 3
**Exported:** 2026-06-12T09:41:00Z

> This is a personal journal export. Each entry is written by the
> same person and represents their thoughts, feelings and experiences
> on that day.

---

## 2026-06-08 · Monday

**Mood:** Great 😄
**Tags:** work, learning
**Words:** 142

### A good day at work

Had a productive morning — finally finished the User Service spec review.

---

## 2026-06-10 · Wednesday

**Mood:** Neutral 😐
**Words:** 55

Had breakfast on the balcony. Nothing special but peaceful.

---

## 2026-06-12 · Friday

**Mood:** Good 🙂
**Tags:** goals
**Words:** 89

### Friday reflection

Good week overall. Ready for Phase 2 this weekend.

---
```

**Export rules:**
- Entries ordered **oldest first** (chronological — better for AI context)
- Mood label mapping: `GREAT → Great 😄` `GOOD → Good 🙂` `NEUTRAL → Neutral 😐` `BAD → Bad 😔` `TERRIBLE → Terrible 😞`
- If mood is null: omit the `**Mood:**` line entirely
- If no tags: omit the `**Tags:**` line entirely
- If no title: omit the `### {title}` heading — content starts directly after metadata
- Word count calculated server-side (split content by whitespace)
- Entries with `deleted = true` are excluded
- Date format: `yyyy-MM-dd · EEEE` (e.g. `2026-06-12 · Friday`)
- Separator between entries: `---` (horizontal rule)

## Events published (Azure Event Hubs)
| Topic | Trigger | Payload |
|---|---|---|
| `journal.entry.created` | POST succeeds | `{ entryId, userId, entryDate }` |
| `journal.entry.updated` | PUT succeeds | `{ entryId, userId }` |

## Events consumed (Azure Event Hubs)
| Topic | Action |
|---|---|
| `users.user.deactivated` | Soft-delete all entries owned by that user |

## Database migrations (Flyway)
```
V1__create_journal_entries_table.sql
V2__create_tags_table.sql
V3__create_journal_entry_tags_table.sql
V4__add_soft_delete_to_entries.sql
  -- ALTER TABLE journal_entries ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT false;
  -- CREATE INDEX idx_entries_user_not_deleted ON journal_entries (user_id, entry_date) WHERE deleted = false;
V5__add_entry_constraints.sql
  -- ALTER TABLE journal_entries ADD CONSTRAINT uq_user_entry_date UNIQUE (user_id, entry_date);
  -- ALTER TABLE journal_entries ADD CONSTRAINT chk_content_length CHECK (char_length(content) <= 10000);
```

## Environment variables
```env
# Request size limit (set in application.yml)
# spring.servlet.multipart.max-request-size=1MB
# server.tomcat.max-http-form-post-size=1MB
SPRING_DATASOURCE_URL=jdbc:postgresql://[server].postgres.database.azure.com:5432/journal?currentSchema=journal&sslmode=require
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
JWT_PUBLIC_KEY=
KAFKA_BOOTSTRAP_SERVERS=[ns].servicebus.windows.net:9093
KAFKA_SASL_JAAS_CONFIG=
REDIS_URL=rediss://[redis-name].redis.cache.windows.net:6380   # stats cache — 30min TTL
```

## API tests (Level 2 — Cucumber + Rest Assured)

Feature files live in `src/test/resources/features/`.
Step definitions use Rest Assured against the running Spring Boot app
with Testcontainers providing real PostgreSQL and Kafka.

```gherkin
Feature: Journal entries

  Background:
    Given I am logged in as "bruno@email.com"

  Scenario: Create entry for today
    When I create an entry with content "Had a great day" for today
    Then I receive status 201
    And the entry is returned with the correct date

  Scenario: Cannot create two entries for the same day
    Given I have an entry for today
    When I create another entry for today
    Then I receive status 409

  Scenario: Cannot create entry for a future date
    When I create an entry for tomorrow
    Then I receive status 400

  Scenario: Cannot read another user's entry
    Given another user has an entry with id "other-entry-id"
    When I request GET /api/journal/entries/other-entry-id
    Then I receive status 404

  Scenario: Cannot edit another user's entry
    Given another user has an entry with id "other-entry-id"
    When I send PUT /api/journal/entries/other-entry-id with new content
    Then I receive status 404

  Scenario: Soft delete does not appear in entry list
    Given I have an entry
    When I delete that entry
    Then it does not appear in GET /api/journal/entries

  Scenario: Search returns matching entries
    Given I have entries with content "Azure decisions" and "Spring Boot"
    When I search for q=azure
    Then I receive only the entry containing "Azure"

  Scenario: Search with q longer than 200 chars returns 400
    When I search with a query of 201 characters
    Then I receive status 400

  Scenario: Page size is capped at 100
    Given I have 150 entries
    When I request GET /api/journal/entries?size=200
    Then I receive at most 100 entries

  Scenario: Export returns valid zip with MD file
    Given I have 3 entries this month
    When I request export for this month
    Then I receive a zip file
    And the zip contains a valid markdown file
    And the markdown contains no email or name

  Scenario: Export with no entries returns 404
    When I request export for a date range with no entries
    Then I receive status 404

  Scenario: Stats reflect current entries
    Given I have 5 entries on consecutive days
    When I request GET /api/journal/stats
    Then totalEntries is 5
    And currentStreak is 5

Feature: Tags

  Background:
    Given I am logged in as "bruno@email.com"

  Scenario: Create a tag
    When I create a tag named "learning"
    Then I receive status 201

  Scenario: Cannot create a tag longer than 50 characters
    When I create a tag with a name of 51 characters
    Then I receive status 400

  Scenario: Cannot add more than 10 tags to an entry

  Scenario: Get entry dates returns all dates with entries
    Given I have entries on "2026-06-10" and "2026-06-11"
    When I call GET /api/journal/entries/dates?from=2026-06-01&to=2026-06-30
    Then I receive status 200
    And the response contains ["2026-06-10", "2026-06-11"]

  Scenario: Get entry by specific date
    Given I have an entry for today
    When I call GET /api/journal/entries/date/{today}
    Then I receive status 200
    And the response contains today's entry

  Scenario: Get entry by date with no entry returns 404
    Given I have no entry for yesterday
    When I call GET /api/journal/entries/date/{yesterday}
    Then I receive status 404

  Scenario: List tags returns all user tags alphabetically
    Given I have created tags "work" and "family"
    When I call GET /api/journal/tags
    Then I receive status 200
    And the response contains ["family", "work"] in alphabetical order

  Scenario: Delete tag removes it from entries
    Given I have a tag "work" assigned to an entry
    When I call DELETE /api/journal/tags/{tagId}
    Then I receive status 204
    And GET /api/journal/entries/{entryId} no longer shows the "work" tag

  Scenario: Cannot add more than 10 tags to an entry
    Given I have 10 tags
    When I create an entry with all 10 tags plus one more
    Then I receive status 400
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
| 400 | `/errors/validation-failed` | DTO validation failure |
| 400 | `/errors/future-date` | entryDate is in the future |
| 401 | `/errors/unauthorized` | Missing or invalid JWT |
| 404 | `/errors/not-found` | Entry or tag not found (also used for ownership — never reveal existence) |
| 409 | `/errors/duplicate-entry-date` | Two entries for the same day |
| 422 | `/errors/content-too-long` | Content exceeds 10,000 characters |

## Redis caching — stats endpoint

The stats endpoint is cached in Redis per user. Stats change only on writes
(create, update, delete) so a 30-minute TTL is appropriate.

### Configuration

```yaml
# application.yml
spring:
  cache:
    type: redis
  data:
    redis:
      time-to-live: 1800  # 30 minutes in seconds
```

Enable caching in the main class:
```java
@SpringBootApplication
@EnableCaching
public class JournalServiceApplication { ... }
```

### @Cacheable on stats endpoint

```java
@Cacheable(value = "journal-stats", key = "#userId")
public StatsResponse getStats(UUID userId) {
    // DB query only runs on first call per user per 30 minutes
    // subsequent calls served from Redis instantly
}
```

### @CacheEvict on every write — all four must be present

```java
// Create — affects totalEntries, totalWords, currentStreak
@CacheEvict(value = "journal-stats", key = "#userId")
public JournalEntry createEntry(UUID userId, CreateEntryRequest request) { ... }

// Update — affects totalWords (content may have changed)
@CacheEvict(value = "journal-stats", key = "#userId")
public JournalEntry updateEntry(UUID userId, UUID entryId, UpdateEntryRequest request) { ... }

// Soft delete — affects totalEntries, totalWords, streaks
@CacheEvict(value = "journal-stats", key = "#userId")
public void deleteEntry(UUID userId, UUID entryId) { ... }

// Deactivation consumer — soft-deletes all entries for a user
@CacheEvict(value = "journal-stats", key = "#userId")
public void softDeleteAllByUserId(UUID userId) { ... }
```

> **Critical:** all 4 eviction points must be implemented. Missing any one
> causes stale stats — a bug, not just a UX issue. A deleted entry that
> still appears in the word count is incorrect data.

### Maven dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Redis is already in `docker-compose.yml` and Azure infrastructure —
no new infrastructure needed.

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
        include: health, info, metrics
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
mvn flyway:repair

# 3. Re-run migrations
mvn flyway:migrate

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
                            <include>com.bravoscribe.journalservice.service.*</include>
                        </includes>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum> <!-- 80% — business logic -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> **80% threshold** — service layer business logic.
> Build fails if coverage drops below 80%.
> Report generated at `target/site/jacoco/index.html`.

## AI Regeneration Prompt
```
Using SYSTEM_SPEC.md and services/journal-service/SPEC.md, generate the complete Spring Boot project for the Journal Service.

Include:
- JournalEntry, Tag, JournalEntryTag entities
- All DTOs (Java 25 records)
- Repositories
- JournalService, TagService
- JournalController, TagController
- @RestControllerAdvice with sealed error classes
- @EnableCaching on main class
- @Cacheable(value="journal-stats", key="#userId") on getStats()
- @CacheEvict on createEntry, updateEntry, deleteEntry, softDeleteAllByUserId
- Redis cache config: time-to-live 1800 seconds (30 minutes)
- Kafka producer for journal.entry.created and journal.entry.updated
- Kafka consumer for users.user.deactivated (soft-delete all entries)
- Flyway migrations V1–V5 (V4 = soft-delete + index, V5 = unique constraint + content check)
- JUnit 5 unit tests for JournalService (80% service layer coverage — JaCoCo)
- Level 2 API tests: Cucumber feature files matching all 20 scenarios,
  Rest Assured step definitions, CucumberRunner for JUnit 5,
  Testcontainers setup with PostgreSQL, Kafka, and Redis
- JaCoCo Maven plugin configured per the Test coverage section (80% threshold)

Pay special attention to:
- GET /api/journal/entries supports optional from/to date range params
- GET /api/journal/entries/dates?from=&to= returns List<String> of dates only
  (SELECT entry_date only — do not load full content)
- userId always from JWT — never from request body
- Soft delete only — never hard delete
```

## Logging

Follow the logging rules in `SYSTEM_SPEC.md` section 7.2. Key events to log:

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

**Never log:** journal entry content, titles, or any personal data from entries.

## Cucumber feature files (Level 2 API tests)

```
src/test/resources/features/
├── entries.feature           ← create, read, update, delete, ownership, future date
├── search.feature            ← full-text search, q length limit, combined filters
├── calendar.feature          ← dates endpoint, date range filter
├── tags.feature              ← create, list, delete, max 10 per entry
├── export.feature            ← zip download, 404 no entries, 400 range exceeded
└── stats.feature             ← totalEntries, totalWords, streaks
```

See `SYSTEM_SPEC.md` section 4.1 for full scenario examples and step definition structure.

## Definition of Done

Before moving to the next service, verify all of these:

- [ ] `mvn clean package` passes with no errors
- [ ] All unit tests pass (`mvn test`)
- [ ] Service starts locally with `docker-compose up`
- [ ] All endpoints verified via Swagger UI (`http://localhost:8082/swagger-ui.html`)
- [ ] OpenAPI spec exported and committed:
  ```bash
  curl http://localhost:8082/v3/api-docs -o docs/openapi/journal-service.yaml
  git add docs/openapi/journal-service.yaml
  git commit -m "docs: export journal-service OpenAPI spec"
  ```
- [ ] One-entry-per-day rule tested (second POST for same date returns 409)
- [ ] POST entry with future entryDate returns 400
- [ ] GET entries?q= uses parameterised query — no SQL injection
- [ ] GET entries?size=10000 returns at most 100 entries
- [ ] GET entries?q= returns 400 for q longer than 200 characters
- [ ] `GET /api/journal/entries/dates` returns only dates, no content
- [ ] `GET /api/journal/entries?from=&to=` filters correctly by date range
- [ ] `GET /api/journal/entries?q=` returns entries matching title or content (case-insensitive)
- [ ] `q` and `from/to` can be combined in the same request
- [ ] userId ownership tested (cannot read or edit another user's entry)
- [ ] Kafka events verified (entry.created and entry.updated published)
- [ ] users.user.deactivated consumer tested (entries soft-deleted)
- [ ] Export endpoint tested: zip downloaded, MD file inside is valid
- [ ] `GET /api/journal/stats` returns correct totalEntries, totalWords, streaks (soft-deleted entries excluded)
- [ ] Export with no entries returns 404
- [ ] Export range > 366 days returns 400
- [ ] POST entry returns 400 for more than 10 tags
- [ ] POST tag returns 400 for name longer than 50 characters
- [ ] POST request larger than 1MB returns 413
- [ ] Exported MD contains no user-identifying information
- [ ] POST entry returns 400 for content exceeding 10,000 characters
- [ ] Export completes within 30 seconds for 366 entries of max content size
