# OpenAPI Specs

This folder contains the OpenAPI specifications exported from each running service.

## Purpose
- **Frontend code generation** — React and Angular generate their API clients from these files
- **Contract documentation** — readable API contract per service
- **Offline client generation** — frontends can generate clients without the backend running

## How to export (after each service is implemented)

```bash
# Start services locally
cd infra && docker-compose up -d

# Export specs
curl http://localhost:8081/v3/api-docs -o docs/openapi/user-service.yaml
curl http://localhost:8082/v3/api-docs -o docs/openapi/journal-service.yaml

# Commit
git add docs/openapi/
git commit -m "docs: export OpenAPI specs"
```

## How to generate API clients

### React (@hey-api/openapi-ts)
```bash
cd frontend-react
npx @hey-api/openapi-ts \
  --input ../docs/openapi/user-service.yaml \
  --output src/api/user

npx @hey-api/openapi-ts \
  --input ../docs/openapi/journal-service.yaml \
  --output src/api/journal
```

### Angular (openapi-generator)
```bash
cd frontend-angular
npx @openapitools/openapi-generator-cli generate \
  -i ../docs/openapi/user-service.yaml \
  -g typescript-angular \
  -o src/api/user
```

> **Note:** Angular back-office only calls User Service (SYSTEM_SPEC §3.2 privacy rule).
> No Journal Service client is generated for Angular.

## Files
| File | Service | Status |
|---|---|---|
| `user-service.yaml` | User Service | ⬜ pending implementation |
| `journal-service.yaml` | Journal Service | ⬜ pending implementation |
