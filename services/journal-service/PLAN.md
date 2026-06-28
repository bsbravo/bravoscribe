# Phase 3 — Journal Service Implementation Plan

## Context

Phase 1 (Infrastructure) and Phase 2 (User Service) are complete and verified (`mvn verify` passed — 28 unit + 24 Cucumber tests). Phase 3 is the Journal Service: a Spring Boot microservice that manages private journal entries, tags, full-text search, zip export, and Redis-cached stats. It publishes Kafka events on writes and consumes `users.user.deactivated` to soft-delete entries for deactivated users.

---

## Readiness Check — We Are Good to Go ✅

| Check | Status | Notes |
|---|---|---|
| Spec complete | ✅ | `services/journal-service/SPEC.md` (851 lines, all sections filled) |
| Implementation plan | ✅ | `IMPLEMENTATION_PLAN.md` Phase 3 entry condition met |
| Infrastructure | ✅ | Docker Compose running with PostgreSQL, Redis, Kafka, nginx |
| `journal` schema + permissions | ✅ | Handled in `infra/init-schemas.sql` (per SPEC.md note) |
| User Service (JWT issuer) | ✅ | Phase 2 complete; journal-service only needs the public key |
| Dockerfile | ✅ | `services/journal-service/Dockerfile` already exists |
| Template to follow | ✅ | User Service (`services/user-service/`) is the reference |
| Lessons learned | ✅ | 8 Phase 2 lessons documented and applicable to Phase 3 |
| Missing | ⚠️ | No `pom.xml` yet — first file to create |

---

## Lessons Learned to Apply (from Phase 2)

These burn us if ignored:

- **L1 (context-path):** No `server.servlet.context-path`. Declare full path on each controller: `@RequestMapping("/api/journal/entries")` and `@RequestMapping("/api/journal/tags")`. SecurityConfig matchers must use the same full paths.
- **L2 (Rest Assured):** Pin `rest-assured.version` to `6.0.0` — 5.x throws NPE on Java 25.
- **L3 (schema creation):** No `CREATE SCHEMA` in V1 migration. Schema is pre-created by `infra/init-schemas.sql`. Tests use a `test-schema.sql` init script on the Testcontainers PostgreSQL container.
- **L4 (test env vars):** All values needed by test code must be explicitly set in `src/test/resources/application.yml`. Do not rely on `${VAR:default}` fallback — `@DynamicPropertySource` doesn't inherit them.
- **L5 (Cucumber cleanup):** Background step must delete then create test users. No scenario may leave stale DB state for the next one.
- **L6 (Kafka local):** `application-local.yml` sets `spring.kafka.bootstrap-servers: localhost:29092`. Default in `application.yml`: `kafka:9092`.
- **L7 (Swagger 401):** SecurityConfig must explicitly `permitAll()` for `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`.
- **L8 (curl on Windows):** Use `curl.exe` in PowerShell, not `curl` (which is `Invoke-WebRequest`).

---

## What's New vs. User Service (key differences)

| Aspect | User Service | Journal Service |
|---|---|---|
| Redis | Not used | `spring-boot-starter-data-redis` + `@EnableCaching` |
| Kafka | Producer only | Producer (2 topics) + Consumer (1 topic) |
| JWT keys | Private + Public | Public key only (RS256 verification) |
| JaCoCo threshold | 90% | 80% |
| Cookie management | Yes (refresh token) | None |
| Export | None | Zip with markdown file |
| Controller paths | `/api/users/...` | `/api/journal/entries`, `/api/journal/tags` |

---

## Package Structure

```
com.bravoscribe.journalservice/
├── JournalServiceApplication.java     @SpringBootApplication @EnableCaching
├── config/
│   ├── SecurityConfig.java
│   └── RedisConfig.java               CacheManager + 1800s TTL
├── controller/
│   ├── JournalController.java         @RequestMapping("/api/journal/entries")
│   └── TagController.java             @RequestMapping("/api/journal/tags")
├── dto/
│   ├── CreateEntryRequest.java        record (Java 25)
│   ├── UpdateEntryRequest.java        record
│   ├── JournalEntryResponse.java      record
│   ├── CreateTagRequest.java          record
│   ├── TagResponse.java               record
│   └── StatsResponse.java             record
├── entity/
│   ├── JournalEntry.java
│   ├── Tag.java
│   ├── JournalEntryTag.java           @EmbeddedId or composite key
│   └── Mood.java                      enum with @Enumerated(STRING)
├── exception/
│   └── GlobalExceptionHandler.java    @RestControllerAdvice, RFC 9457 ProblemDetail
├── kafka/
│   ├── JournalEventProducer.java      publishes entry.created + entry.updated
│   └── UserDeactivatedConsumer.java   consumes users.user.deactivated
├── repository/
│   ├── JournalEntryRepository.java    ILIKE search, date range, date-only projection
│   └── TagRepository.java
├── security/
│   └── JwtAuthenticationFilter.java   MDC userId (same pattern as user-service)
└── service/
    ├── JournalService.java            CRUD + stats + @CacheEvict (all 4 write ops)
    ├── TagService.java
    └── ExportService.java             builds zip in memory, MD format
```

---

## Implementation Order

Follows `IMPLEMENTATION_PLAN.md` Phase 3 exactly:

1. **`pom.xml` + Maven wrapper** (`.mvn/wrapper/`, `mvnw`, `mvnw.cmd`)
   - Mirror user-service pom.xml structure
   - Add `spring-boot-starter-data-redis` (not in user-service)
   - Change artifactId → `journal-service`, package → `com.bravoscribe.journalservice`
   - JaCoCo threshold → 80% (not 90%)
   - Keep same versions: Spring Boot 4.1, Java 25, Cucumber 7.15.0, Rest Assured 6.0.0, Testcontainers 1.21.4

2. **Flyway migrations V1–V5** (`src/main/resources/db/migration/`)
   - V1: `CREATE TABLE journal.journal_entries` (no CREATE SCHEMA — schema pre-exists)
   - V2: `CREATE TABLE journal.tags` (unique per userId + name)
   - V3: `CREATE TABLE journal.journal_entry_tags` (join table)
   - V4: Soft delete column + partial index `(user_id, entry_date) WHERE deleted = false`
   - V5: `UNIQUE (user_id, entry_date)` constraint + `CHECK (char_length(content) <= 10000)`

3. **Entities** — `JournalEntry`, `Tag`, `JournalEntryTag`, `Mood` enum

4. **Repositories** — including ILIKE search and `SELECT entry_date` projection (dates endpoint — no content loaded)

5. **DTOs** — Java 25 records with Jakarta validation; `CreateEntryRequest` with `@PastOrPresent` on `entryDate`

6. **Services** — `JournalService`, `TagService`, `ExportService`
   - `@Cacheable(value="journal-stats", key="#userId")` on `getStats()`
   - `@CacheEvict` on all 4 write ops: `createEntry`, `updateEntry`, `deleteEntry`, `softDeleteAllByUserId`
   - Streak calculation logic in `JournalService`
   - Zip + markdown generation in `ExportService` (built in memory)

7. **Controllers** — `JournalController`, `TagController`
   - `PageRequest.of(page, effectiveSize, Sort.by("entryDate").descending())` — never allow client sort
   - `int effectiveSize = Math.min(size, 100)` — page size cap

8. **SecurityConfig** — RS256 JWT resource server (public key only), full paths for matchers, permitAll for actuator + swagger

9. **Kafka** — `JournalEventProducer` (2 topics), `UserDeactivatedConsumer` (1 topic → calls `softDeleteAllByUserId`)

10. **`application.yml`** — port 8082, schema `journal`, Redis config (TTL 1800), virtual threads, Kafka, Actuator, Swagger

11. **Unit tests** — JUnit 5 + Mockito for `JournalService`, `TagService`, `ExportService` (80% service layer coverage)

12. **Level 2 Cucumber API tests** — 21 scenarios across 6 feature files:
    - `entries.feature` — create, read, update, delete, ownership, future date
    - `search.feature` — full-text search, q length limit, combined filters
    - `calendar.feature` — dates endpoint, date range filter, get entry by date (200 + 404)
    - `tags.feature` — create, list, delete, max 10 per entry
    - `export.feature` — zip download, 404 no entries, 400 range exceeded
    - `stats.feature` — totalEntries, totalWords, streaks
    - Testcontainers: PostgreSQL + `test-schema.sql` init script + Kafka + Redis

---

## Critical Implementation Details

### Redis eviction (all 4 must be present)
```java
@CacheEvict(value = "journal-stats", key = "#userId")
// on: createEntry, updateEntry, deleteEntry, softDeleteAllByUserId
```

### ILIKE search — parameterised only
```java
@Query("SELECT e FROM JournalEntry e WHERE e.userId = :userId AND e.deleted = false " +
       "AND (:q IS NULL OR LOWER(e.content) LIKE LOWER(CONCAT('%', :q, '%')) " +
       "OR LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%')))")
```

### Date-only projection (calendar endpoint)
Use a Spring Data Projection or `@Query` with `SELECT e.entryDate` — never load content.

### Export zip (in memory)
```java
ByteArrayOutputStream baos = new ByteArrayOutputStream();
ZipOutputStream zip = new ZipOutputStream(baos);
// add journal-export-{from}-to-{to}.md as zip entry
return ResponseEntity.ok()
    .header("Content-Disposition", "attachment; filename=\"bravoscribe-export-{from}-to-{to}.zip\"")
    .contentType(MediaType.parseMediaType("application/zip"))
    .body(baos.toByteArray());
```

### Mood emoji mapping (for export)
`GREAT→😄`, `GOOD→🙂`, `NEUTRAL→😐`, `BAD→😔`, `TERRIBLE→😞`

### SecurityConfig — no JWT private key
Journal Service is an RS256 resource server only. No private key needed — only `JWT_PUBLIC_KEY`.

---

## Verification (Definition of Done)

After `./mvnw clean verify` passes:

1. `docker-compose -f infra/docker-compose.yml --env-file .env up` starts journal-service on port 8082 with no errors
2. All unit tests pass, JaCoCo 80% service layer threshold met
3. All 20 Cucumber scenarios green
4. Swagger UI at `http://localhost:8082/swagger-ui.html` — all endpoints visible
5. Manual checks:
   - POST same date twice → 409
   - POST future date → 400
   - GET another user's entry → 404
   - GET entries?size=10000 → ≤ 100 results
   - GET entries?q=abc (q > 200 chars) → 400
   - DELETE entry → GET entries no longer shows it
   - GET stats → reflects correct counts (no soft-deleted entries)
   - GET export?from=&to= → valid zip with MD file, no PII
6. Export OpenAPI spec:
   ```powershell
   curl.exe http://localhost:8082/v3/api-docs -o docs/openapi/journal-service.yaml
   ```
