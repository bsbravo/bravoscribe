# Bravoscribe — Implementation Plan

> **Version:** 1.0.0  
> **Spec version:** SYSTEM_SPEC.md v2.0.0

---

## Overview

Eight phases in strict order.

```
Phase 1 — Infrastructure (Docker Compose + nginx)
Phase 2 — User Service (auth, JWT, profiles)
Phase 3 — Journal Service (entries, tags, export)
Phase 4 — Notification Service (emails, Kafka consumers)
Phase 5 — React Web App (end-user frontend)
Phase 6 — Android App (native mobile)
Phase 7 — Angular Back-office (admin frontend)
Phase 8 — Azure Cloud (deploy to brazilsouth)
```

Start every Claude Code session:
```bash
cd C:\Users\bruno\code\bravoscribe
claude
```

---

## Phase 1 — Infrastructure

**Goal:** full local stack running with `docker-compose up`

**Claude Code prompt:**
```
Read SYSTEM_SPEC.md sections 3, 6, and the infra/ folder.
Generate the complete local development infrastructure.
Do not generate any Spring Boot code yet. Confirm when done.
```

**Definition of Done:**
- [ ] `docker-compose up` starts with no errors
- [ ] PostgreSQL: schemas `users` and `journal` present
  ```powershell
  docker exec -it bravoscribe-postgres psql -U postgres -d journal -c "\dn"
  ```
- [ ] Redis responds to ping
  ```powershell
  docker exec -it bravoscribe-redis redis-cli ping  # → PONG
  ```
- [ ] MongoDB responds to ping
  ```powershell
  docker exec -it bravoscribe-mongo mongosh -u mongo -p mongo --eval "db.adminCommand('ping')"  # → ok: 1
  ```
- [ ] Kafka running (empty topic list is correct)
  ```powershell
  docker exec -it bravoscribe-kafka kafka-topics --bootstrap-server localhost:9092 --list
  ```
- [ ] nginx health check returns 200
  ```powershell
  curl http://localhost:8080/health  # → ok
  ```
- [ ] Kafka UI accessible at `http://localhost:8090`
- [ ] pgAdmin accessible at `http://localhost:5050`

---

## Phase 2 — User Service

**Entry condition:** Phase 1 complete

**Claude Code prompt:**
```
Read SYSTEM_SPEC.md and services/user-service/SPEC.md carefully.
Do not generate any code yet. Confirm you understand:
1. Java 25 — records for DTOs, sealed classes, virtual threads
2. Password policy — min 8, max 128, BCrypt strength 12
3. Refresh token — httpOnly cookie only, never request body
4. JWT — RS256, private key stays in User Service only
5. Package namespace — com.bravoscribe.userservice
Then wait for my instructions.
```

**Implementation order:** pom.xml + Maven wrapper (`.mvn/wrapper/`, `mvnw`, `mvnw.cmd`) → Flyway V1-V4 → entities → repos → DTOs → services → controllers → SecurityConfig → Kafka producers → unit tests → Level 2 Cucumber tests

**Definition of Done:**
- [ ] `./mvnw clean verify` passes, all unit tests pass, JaCoCo 90% threshold met
- [ ] JWT login flow end to end: register → login → protected endpoint
- [ ] refreshToken cookie: httpOnly, SameSite=Strict
- [ ] Password reset always returns 204, token expires 15min single-use
- [ ] Deactivated user returns 401
- [ ] Register rate limit: returns 429 **(Phase 8 — cloud only)**
- [ ] OpenAPI exported: `curl http://localhost:8081/v3/api-docs -o docs/openapi/user-service.yaml`

---

## Phase 3 — Journal Service

**Entry condition:** Phase 2 complete

**Claude Code prompt:**
```
Read SYSTEM_SPEC.md and services/journal-service/SPEC.md carefully.
Do not generate any code yet. Confirm you understand:
1. Java 25 — records, sealed classes, virtual threads
2. userId always from JWT — never request body
3. One entry per day per user (409 on duplicate)
4. @PastOrPresent on entryDate
5. Soft delete only — never hard delete
6. ILIKE search — parameterised queries only
7. Page size capped at 100
8. Package namespace — com.bravoscribe.journalservice
Then wait for my instructions.
```

**Implementation order:** pom.xml + Maven wrapper (`.mvn/wrapper/`, `mvnw`, `mvnw.cmd`) → Flyway V1-V5 → entities → repos → DTOs → services → controllers → SecurityConfig → Kafka producer + deactivation consumer → export service → unit tests → Level 2 Cucumber tests

**Definition of Done:**
- [ ] `./mvnw clean verify` passes, all tests pass, JaCoCo 80% threshold met
- [ ] One-entry-per-day enforced (409), future dates rejected (400)
- [ ] Ownership enforced (other user's entry returns 404)
- [ ] Export returns valid zip, stats endpoint correct
- [ ] OpenAPI exported: `curl http://localhost:8082/v3/api-docs -o docs/openapi/journal-service.yaml`

---

## Phase 4 — Notification Service

**Entry condition:** Phases 2 + 3 complete

**Claude Code prompt:**
```
Read SYSTEM_SPEC.md and services/notification-service/SPEC.md carefully.
Do not generate any code yet. Confirm you understand:
1. No REST endpoints — event-driven only
2. Redis idempotency key: notify:processed:{topic}:{eventId} TTL 7 days
3. Password reset email uses #token= hash fragment, NOT ?token=
4. Package namespace — com.bravoscribe.notificationservice
5. Email delivery log — every sent email persisted to MongoDB collection
   `email_logs` using Spring Data MongoDB (@Document, MongoRepository)
Then wait for my instructions.
```

**Definition of Done:**
- [ ] `./mvnw clean verify` passes, all unit tests pass, JaCoCo 80% threshold met
- [ ] Welcome email after registration
- [ ] Reset email contains `#token=` not `?token=`
- [ ] Duplicate event does not send duplicate email

---

## Phase 5 — React Web App

**Entry condition:** Phases 2, 3, 4 complete

**Claude Code prompt:**
```
Read SYSTEM_SPEC.md and frontend-react/SPEC.md carefully.
Do not generate any code yet. Confirm you understand:
1. Two themes — Warm (default) and Chronicle (FFT dark)
2. Autosave — POST on explicit Save, PUT after 30s
3. entryDate always local timezone: toLocaleDateString('sv')
4. accessToken in Zustand memory only — never localStorage
Then wait for my instructions.
```

**Definition of Done:**
- [ ] `npm run build` passes
- [ ] Login → write entry → view entry works end to end
- [ ] Both themes switch and persist on reload
- [ ] Autosave shows "saved" after 30s inactivity
- [ ] Delete Undo toast, unsaved changes guard work

---

## Phase 6 — Android App

**Entry condition:** Phase 5 complete

**Claude Code prompt:**
```
Read SYSTEM_SPEC.md and android/SPEC.md carefully.
Do not generate any code yet. Confirm you understand:
1. MVVM + Clean Architecture
2. Two themes — LightWarmColorScheme and DarkChronicleColorScheme
3. entryDate always LocalDate.now() — device timezone
4. Autosave 30 seconds (same as React)
5. Package namespace — com.bravoscribe.android
Then wait for my instructions.
```

**Definition of Done:**
- [ ] App builds, login → write → view works
- [ ] Both themes persist across restart
- [ ] Entries load from Room when offline

---

## Phase 7 — Angular Back-office

**Entry condition:** Phase 2 complete

**Claude Code prompt:**
```
Read SYSTEM_SPEC.md and frontend-angular/SPEC.md carefully.
Do not generate any code yet. Confirm you understand:
1. Admin-only — accesses User Service only
2. Angular 22 + Angular Material + NgRx
3. ADMIN JWT role enforced
Then wait for my instructions.
```

**Definition of Done:**
- [ ] `ng build` passes
- [ ] Admin login, user list, deactivate action work
- [ ] USER role cannot access admin routes (403)

---

## Phase 8 — Azure Cloud

**Entry condition:** All phases 1–7 complete

**Claude Code prompt:**
```
Read SYSTEM_SPEC.md and infra/azure/SPEC.md carefully.
Do not generate any code yet. Confirm you understand:
1. Region: brazilsouth
2. Shared PostgreSQL — db: journal, schemas: users + journal
3. ACR with Managed Identity — no admin account
4. Key Vault for all secrets
Then wait for my instructions.
```

**Definition of Done:**
- [ ] `terraform apply` completes, all services reachable via APIM
- [ ] Application Insights receiving telemetry
- [ ] Register rate limit verified (429 after 10 req/hour)
- [ ] All 4 alerts configured

---

## Tips

**One layer at a time.** After each step: *"Review against [SPEC.md section X]. List deviations."*

**Commit after each phase:**
```bash
git add . && git commit -m "feat(phase-N): description" && git push origin main
```

*See SYSTEM_SPEC.md for full architecture and decisions log.*
