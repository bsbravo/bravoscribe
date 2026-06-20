# Azure Infrastructure — Specification

> See `SYSTEM_SPEC.md` for system-wide conventions and the full Azure services list.

## Region
`brazilsouth` (São Paulo) for all resources.

## Phased environment approach

> **Start with `dev` only.** Create `staging` and `production` when you actually need them. This keeps costs low while learning and building.

| Phase | Environments active | When to activate |
|---|---|---|
| **Building** (now) | `dev` only | From day one |
| **Pre-launch** | `dev` + `staging` | When the app is feature-complete and needs real testing |
| **Launch** | `dev` + `staging` + `production` | When real users are onboarded |

**Destroy dev when not actively working** to avoid unnecessary costs:
```bash
terraform destroy -var="env=dev"   # destroys all dev resources
terraform apply  -var="env=dev"    # recreates them when needed
```

## Resource groups

| Resource Group | Environment | Status |
|---|---|---|
| `journal-rg-shared` | shared (ACR only) | ⬜ create now |
| `journal-rg-dev` | dev | ⬜ create now |
| `journal-rg-staging` | staging | ⬜ create at pre-launch |
| `journal-rg-prod` | production | ⬜ create at launch |

> All resources for a given environment live inside its resource group.
> Deleting the resource group deletes everything inside it — useful for teardown.
> ACR lives in `journal-rg-shared` so it survives environment teardowns.

---

## Azure resources

### Azure Container Registry (ACR)
- **Name:** `journalacr`
- **SKU:** Basic (dev) / Standard (staging + prod)
- **Scope:** One registry shared across all environments — lives in `journal-rg-shared`
- Images tagged by environment and git SHA: `user-service:dev-a1b2c3d`
- **Network:** allow Azure services only — disable public network access after initial setup
- **Authentication:** Managed Identity only — admin account disabled
  - GitHub Actions: uses service principal with AcrPush role
  - App Services: use Managed Identity with AcrPull role
- **No anonymous pull** — all pulls require authentication

### Azure App Service Plan
- **Name:** `journal-plan-[env]`
- **OS:** Linux
- **SKU:** Basic B1 (dev) / Standard S1 (staging + prod)
- All microservices share one App Service Plan per environment

### Azure App Services (one per microservice)
| App Service name | Docker image | Internal port |
|---|---|---|
| `journal-user-service-[env]` | `journalacr.azurecr.io/user-service:[tag]` | 8081 |
| `journal-journal-service-[env]` | `journalacr.azurecr.io/journal-service:[tag]` | 8082 |
| `journal-notification-service-[env]` | `journalacr.azurecr.io/notification-service:[tag]` | 8083 |

### Azure Database for PostgreSQL Flexible Server
- **Name:** `journal-postgres-[env]`
- **SKU:** Burstable B1ms (dev) / General Purpose D2s_v3 (staging + prod)
- **Schemas:** `users` and `journal` on the **same server and same database**
- **SSL:** enforced
- **Firewall:** allow Azure services only

> **Cost decision:** User Service and Journal Service share one PostgreSQL
> server and one database, separated by schemas. Each service connects
> with its own database user scoped to its own schema only.
> See decision #12 in `SYSTEM_SPEC.md`.

**Database users (one per service):**
```sql
-- User Service — access to users schema only
CREATE USER user_svc WITH PASSWORD '...';
GRANT ALL ON SCHEMA users TO user_svc;
REVOKE ALL ON SCHEMA journal FROM user_svc;

-- Journal Service — access to journal schema only
CREATE USER journal_svc WITH PASSWORD '...';
GRANT ALL ON SCHEMA journal TO journal_svc;
REVOKE ALL ON SCHEMA users FROM journal_svc;
```

**Migration path if schemas need to be split later:**
1. Create a new PostgreSQL Flexible Server
2. Migrate the target schema using `pg_dump --schema`
3. Update the relevant `SPRING_DATASOURCE_URL` in Key Vault
4. No application code changes needed

### Azure Cache for Redis
- **Name:** `journal-redis-[env]`
- **SKU:** Basic C0 (dev) / Standard C1 (staging + prod)
- **TLS:** port 6380 (SSL required)

### Azure Event Hubs
- **Namespace:** `journal-eventhubs-[env]`
- **SKU:** Basic (dev) / Standard (staging + prod)
- **Topics (Event Hubs):**
  - `users.user.registered`
  - `users.user.deactivated`
  - `users.password.reset.requested`
  - `journal.entry.created`
  - `journal.entry.updated`

### Azure Key Vault
- **Name:** `journal-kv-[env]`
- **Secrets stored:**
  - `spring-datasource-password`
  - `jwt-private-key`
  - `jwt-public-key`
  - `kafka-sasl-jaas-config`
  - `redis-password`
  - `sendgrid-api-key`
  - `frontend-url`       ← React app URL, used by Notification Service to build password reset email links
  - `cosmos-mongodb-uri` ← Azure Cosmos DB for MongoDB connection string (notification-service)
  - `internal-api-key`   ← shared key for service-to-service calls (Phase 2)
- App Services access Key Vault via **Managed Identity** (no credentials in config)

### Azure API Management (APIM)
- **Name:** `journal-apim-[env]`
- **SKU:** Consumption (dev) / Developer (staging + prod)
- **Policies:** JWT validation, rate limiting, CORS, Application Insights logging
- Sits in front of all App Services
- **Access rules enforced at APIM:**
  - React + Android → User Service + Journal Service
  - Angular → User Service only
  - Notification Service → not exposed externally

### Azure Static Web Apps
| App | Name | Branch |
|---|---|---|
| React (end users) | `journal-react-[env]` | `main` |
| Angular (back-office) | `journal-angular-[env]` | `main` |

> **No separate Azure CDN needed.** Azure Static Web Apps includes a
> built-in global CDN that automatically distributes static assets
> (JS, CSS, images) at no extra cost. A separate CDN would only add
> value for API response caching, which is inappropriate for a personal
> journal with private, dynamic data.

### Azure Application Insights
- **Name:** `journal-insights-[env]`
- Connected to all App Services via `APPLICATIONINSIGHTS_CONNECTION_STRING`

---

## Estimated monthly cost (dev environment only)

| Resource | SKU | Estimated cost |
|---|---|---|
| App Service Plan B1 | Basic | ~$13/month |
| PostgreSQL Flexible Server B1ms (shared) | Burstable | ~$12/month |
| Azure Cache for Redis C0 | Basic | ~$16/month |
| Event Hubs Basic | Basic | ~$10/month |
| APIM Consumption | per call | ~$0 (first 1M calls free) |
| Static Web Apps | Free tier | $0 |
| ACR Basic | Basic | ~$5/month |
| Application Insights | Pay per use | ~$0 (low traffic) |
| Azure Cosmos DB MongoDB | Serverless | ~$1/month |
| **Total (approximate)** | | **~$57/month** |

> **Cost decisions:**
> - ~~Three separate App Service Plans~~ → one shared B1 plan saves ~$27/month
> - ~~Two PostgreSQL servers~~ → one shared server (two schemas) saves ~$12/month. See decision #12
> - Cosmos DB MongoDB serverless → ~$1/month for a personal app (pay per RU, no idle cost)
> - Destroy the dev environment when not actively working to reduce costs to ~$5/month (ACR only)

---

## CI/CD — GitHub Actions

### Workflow: deploy service
**File:** `.github/workflows/deploy-[service].yml`
**Trigger:** push to `main` (prod) or `dev` (dev)

```
Steps:
1. Checkout code
2. Set up Java 25
3. mvn clean package -DskipTests
4. Docker build → tag with git SHA and env
5. Push to ACR (journalacr.azurecr.io)
6. az webapp config container set → update App Service image
7. az webapp restart
```

### Workflow: deploy frontend
**File:** `.github/workflows/deploy-react.yml` / `deploy-angular.yml`
**Trigger:** push to `main`

```
Steps:
1. Checkout code
2. npm install && npm run build
3. Deploy to Azure Static Web Apps (built-in action)
```

---

## Terraform structure
```
infra/terraform/
├── main.tf           # Provider, resource groups (shared + env)
├── acr.tf            # Container registry (shared resource group)
├── appservice.tf     # App Service Plan + App Services
├── postgres.tf       # PostgreSQL Flexible Server + databases
├── redis.tf          # Azure Cache for Redis
├── eventhubs.tf      # Event Hubs namespace + topics
├── keyvault.tf       # Key Vault + secrets (values via vars)
├── apim.tf           # API Management + routing policies
├── staticwebapps.tf  # React + Angular Static Web Apps
├── monitoring.tf     # Application Insights
├── variables.tf      # Input variables (env, image tags, secrets)
└── outputs.tf        # Output values (URLs, connection strings)
```

## AI Regeneration Prompt
```
Using SYSTEM_SPEC.md and infra/azure/SPEC.md, generate the full Terraform
configuration for the Bravoscribe system in the brazilsouth region.
Create all .tf files listed in the Terraform structure section. Use
variables for environment name, image tags, and secret values. Follow
the phased approach — parameterise everything by env so the same
Terraform code works for dev, staging, and production. ACR lives in
journal-rg-shared, all other resources in journal-rg-[env]. Output all
App Service URLs, ACR login server, and Key Vault URI.
```

---

## KQL Queries (Application Insights)

> Paste these directly into Azure Portal → Application Insights → Logs.
> These are your first-response queries when something breaks in production.

### Errors and failures

**All errors in the last hour**
```kql
traces
| where timestamp > ago(1h)
| where severityLevel == 3  // ERROR
| project timestamp, cloud_RoleName, message, customDimensions
| order by timestamp desc
```

**All warnings in the last hour**
```kql
traces
| where timestamp > ago(1h)
| where severityLevel == 2  // WARN
| project timestamp, cloud_RoleName, message, customDimensions
| order by timestamp desc
```

**Error rate per service (last 24h)**
```kql
requests
| where timestamp > ago(24h)
| summarize total = count(),
            errors = countif(resultCode >= 500)
    by cloud_RoleName
| extend errorRate = round(100.0 * errors / total, 2)
| order by errorRate desc
```

**Slowest endpoints (p95 latency, last 24h)**
```kql
requests
| where timestamp > ago(24h)
| summarize p95 = percentile(duration, 95),
            count = count()
    by name, cloud_RoleName
| order by p95 desc
| take 20
```

---

### User Service queries

**Failed login attempts (last hour)**
```kql
traces
| where timestamp > ago(1h)
| where cloud_RoleName == "user-service"
| where message contains "Failed login"
| project timestamp, message,
          email = customDimensions.email,
          reason = customDimensions.reason
| order by timestamp desc
```

**Failed login spike detection (brute force)**
```kql
traces
| where timestamp > ago(10m)
| where cloud_RoleName == "user-service"
| where message contains "Failed login"
| summarize attempts = count() by bin(timestamp, 1m)
| order by timestamp desc
```

**User registrations over time**
```kql
traces
| where timestamp > ago(7d)
| where cloud_RoleName == "user-service"
| where message contains "User registered"
| summarize registrations = count() by bin(timestamp, 1d)
| order by timestamp asc
```

**All activity for a specific user**
```kql
traces
| where timestamp > ago(7d)
| where tostring(customDimensions.userId) == "{userId}"
| project timestamp, cloud_RoleName, severityLevel, message
| order by timestamp desc
```

---

### Journal Service queries

**Unauthorized access attempts (last 24h)**
```kql
traces
| where timestamp > ago(24h)
| where cloud_RoleName == "journal-service"
| where message contains "Unauthorized access attempt"
| project timestamp, message, customDimensions
| order by timestamp desc
```

**Duplicate entry attempts (last 24h)**
```kql
traces
| where timestamp > ago(24h)
| where cloud_RoleName == "journal-service"
| where message contains "Duplicate entry attempt"
| project timestamp, message,
          userId = customDimensions.userId,
          entryDate = customDimensions.entryDate
| order by timestamp desc
```

**Exports generated (last 7 days)**
```kql
traces
| where timestamp > ago(7d)
| where cloud_RoleName == "journal-service"
| where message contains "Export generated"
| project timestamp,
          userId = customDimensions.userId,
          from = customDimensions.from,
          to = customDimensions.to,
          entries = customDimensions.entries
| order by timestamp desc
```

**Export failures**
```kql
traces
| where timestamp > ago(7d)
| where cloud_RoleName == "journal-service"
| where severityLevel == 3
| where message contains "Export generation failed"
| project timestamp, message, customDimensions
| order by timestamp desc
```

---

### Notification Service queries

**Email delivery failures (last 24h)**
```kql
traces
| where timestamp > ago(24h)
| where cloud_RoleName == "notification-service"
| where message contains "Email delivery failed"
| project timestamp, message,
          userId = customDimensions.userId
| order by timestamp desc
```

**Duplicate events skipped (last 24h)**
```kql
traces
| where timestamp > ago(24h)
| where cloud_RoleName == "notification-service"
| where message contains "Duplicate event skipped"
| summarize count = count() by tostring(customDimensions.topic)
```

---

### Infrastructure queries

**Service availability (last 24h)**
```kql
availabilityResults
| where timestamp > ago(24h)
| summarize availability = avg(toint(success)) * 100
    by cloud_RoleName, bin(timestamp, 1h)
| order by timestamp desc
```

**HTTP 5xx errors by endpoint (last 24h)**
```kql
requests
| where timestamp > ago(24h)
| where resultCode >= 500
| summarize count = count()
    by name, resultCode, cloud_RoleName
| order by count desc
```

**Average response time per service (last 24h)**
```kql
requests
| where timestamp > ago(24h)
| summarize avg_ms = avg(duration),
            p95_ms = percentile(duration, 95)
    by cloud_RoleName
| order by p95_ms desc
```

**Active users (unique userIds, last 24h)**
```kql
traces
| where timestamp > ago(24h)
| where isnotempty(customDimensions.userId)
| summarize activeUsers = dcount(tostring(customDimensions.userId))
```

---

### Alerts reference

These alerts should be configured in Azure Monitor:

| Alert name | KQL condition | Threshold | Action |
|---|---|---|---|
| High error rate | HTTP 5xx > X% of requests | 5% for 5 min | Email |
| Failed login spike | Failed login WARN > N per min | 10 per min | Email |
| Service down | No heartbeat | 2 min | Email |
| Slow response | p95 latency > Nms | 2000ms for 5 min | Email |
| Export failures | Export failed ERROR count | 3 in 10 min | Email |
