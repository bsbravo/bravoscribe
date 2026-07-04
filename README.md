# 📓 Bravoscribe

> Built by [Bruno Bravo](https://www.linkedin.com/in/bsbravo/) — Java Tech Lead @ Banco Bradesco

![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-brightgreen?logo=springboot) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791?logo=postgresql) ![MongoDB](https://img.shields.io/badge/MongoDB-7-47A248?logo=mongodb) ![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis) ![Kafka](https://img.shields.io/badge/Kafka-3.9-black?logo=apachekafka) ![React](https://img.shields.io/badge/React-19-61DAFB?logo=react) ![Angular](https://img.shields.io/badge/Angular-22-red?logo=angular) ![Android](https://img.shields.io/badge/Android-Compose-3DDC84?logo=android) ![Azure](https://img.shields.io/badge/Azure-brazilsouth-0078D4?logo=microsoftazure) ![Swagger](https://img.shields.io/badge/OpenAPI-3.0-85EA2D?logo=swagger) ![RFC 9457](https://img.shields.io/badge/errors-RFC%209457-orange) ![Terraform](https://img.shields.io/badge/Terraform-planned-lightgrey?logo=terraform) ![AI-first](https://img.shields.io/badge/AI--first-development-blueviolet?logo=openai) ![License](https://img.shields.io/badge/license-MIT-green)

Bravoscribe is a personal journaling app built as a deliberate learning project
to practice enterprise Java microservices, cloud architecture, and full-stack
development across React, Angular, and Android. The focus is on production-quality
engineering decisions — security, observability, testing strategy, and documented
architecture — not just getting something to run.

## 🤖 AI-first development

This project was built using an **AI-first approach** — architecture decisions, security
reviews, spec validation, and implementation are driven by AI pair programming with
human direction and review at every step.

Every major decision is documented in the [decisions log](./SYSTEM_SPEC.md#12-decisions-log),
capturing not just *what* was built but *why* — a practice that AI-assisted development
makes practical at a level previously reserved for large engineering teams.

## What this project covers

| Area | Technologies |
|---|---|
| Microservices | Java 25, Spring Boot 4.1, Spring Security 7, JWT RS256 |
| Messaging | Apache Kafka 3.9, event-driven architecture, async notifications |
| Testing | JUnit 5 + Mockito (unit) · Cucumber BDD + Rest Assured + Testcontainers (API) · cross-service acceptance tests |
| Web frontend | React 19 + TypeScript, Tailwind v4, Zustand, TanStack Query v5 |
| Back-office | Angular 22, NgRx, Angular Material, lazy loading |
| Mobile | Android, Jetpack Compose, MVVM + Clean Architecture, Hilt, Room |
| Cloud | Azure App Service, APIM, Event Hubs, Key Vault, Static Web Apps |
| Relational DB | PostgreSQL 17, Spring Data JPA, Flyway migrations, shared server dual-schema pattern |
| NoSQL | MongoDB 7, Spring Data MongoDB, document design, Azure Cosmos DB for MongoDB |
| Observability | OpenTelemetry Java Agent → Azure Application Insights |
| Security | BCrypt 12, httpOnly cookies, RS256 asymmetric JWT, hash fragment reset tokens |
| Caching | Redis 7, Spring @Cacheable + @CacheEvict, cache invalidation strategy, 30-min TTL |
| API docs | OpenAPI 3.0, Swagger UI, auto-generated from Spring Boot annotations |
| API errors | RFC 9457 Problem Details — consistent error shape across all services |
| Containerisation | Docker, Docker Compose, Azure Container Registry (ACR) |
| CI/CD | GitHub Actions — automated build, test, and deploy to Azure |


## Status

| Phase | What | Status |
|---|---|---|
| 1 | Infrastructure — Docker Compose + nginx | ✅ Done |
| 2 | User Service — auth, JWT, password reset | ✅ Done |
| 3 | Journal Service — entries, tags, export | ✅ Done |
| 4 | Notification Service — email via Kafka | ✅ Done |
| 5 | React Web App | ⬜ Planned |
| 6 | Android App | ⬜ Planned |
| 7 | Angular Back-office | ⬜ Planned |
| 8 | Azure Cloud deploy | ⬜ Planned |

## Architecture

Three Spring Boot microservices behind nginx (local) / Azure APIM (cloud).
No Spring Cloud Gateway — nginx and APIM handle all routing concerns.

See [SYSTEM_SPEC.md](./SYSTEM_SPEC.md) for the full architecture, security model,
and 22-entry decisions log explaining every major design choice.

> **Why microservices for a personal app?**  
> Deliberately over-engineered as a learning project — each service is a
> contained unit to practice JWT propagation, async messaging, and
> independent deployment. See the [decisions log](./SYSTEM_SPEC.md#12-decisions-log).

## Quick start (local)

```bash
# 1. Copy env template and generate JWT keys
cp .env.example .env
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
# Add private.pem and public.pem contents to .env

# 2. Start the full stack (from repo root — so .env is found automatically)
docker-compose -f infra/docker-compose.yml --env-file .env up
```

Once running, everything is accessible through nginx on **port 8080**:

```bash
# Health check
curl http://localhost:8080/health

# Register a user
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Bruno","email":"bruno@email.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bruno@email.com","password":"password123"}'
```

> All API calls go through `http://localhost:8080` — the nginx gateway.
> Direct service ports (8081, 8082, 8083) are available for Swagger UI only.

## Ports

| Service | Direct URL | Via nginx |
|---|---|---|
| nginx (gateway) | — | http://localhost:8080 |
| User Service | http://localhost:8081 | http://localhost:8080/api/users/ |
| Journal Service | http://localhost:8082 | http://localhost:8080/api/journal/ |
| Notification Service | http://localhost:8083 | internal only — not exposed |
| React App | http://localhost:5173 | — |
| Angular Back-office | http://localhost:4200 | — |
| Swagger (User) | http://localhost:8081/swagger-ui.html | http://localhost:8080/users/swagger-ui/ |
| Swagger (Journal) | http://localhost:8082/swagger-ui.html | http://localhost:8080/journal/swagger-ui/ |
| Health check | — | http://localhost:8080/health |
| Kafka UI | http://localhost:8090 | — |
| pgAdmin | http://localhost:5050 | admin@bravoscribe.com / admin |

> **Use the nginx URL for all API calls.** Direct ports are available during local dev
> but bypass JWT validation at the gateway level — use them for Swagger UI only.

## Documentation

| Doc | Location |
|---|---|
| System spec | [SYSTEM_SPEC.md](./SYSTEM_SPEC.md) |
| Implementation plan | [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) |
| User Service | [services/user-service/SPEC.md](./services/user-service/SPEC.md) |
| Journal Service | [services/journal-service/SPEC.md](./services/journal-service/SPEC.md) |
| Notification Service | [services/notification-service/SPEC.md](./services/notification-service/SPEC.md) |
| React App | [frontend-react/SPEC.md](./frontend-react/SPEC.md) |
| Angular Back-office | [frontend-angular/SPEC.md](./frontend-angular/SPEC.md) |
| Android App | [android/SPEC.md](./android/SPEC.md) |
| Azure Infrastructure | [infra/azure/SPEC.md](./infra/azure/SPEC.md) |
