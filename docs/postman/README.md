# Bravoscribe — Postman Collections

Auto-generated from OpenAPI specs. Import into Postman to test all API endpoints.

---

## Generate collections

Run these commands after Phase 2 and 3 are implemented and services are running locally:

```bash
# 1. Export OpenAPI specs from running services
curl http://localhost:8081/v3/api-docs -o docs/openapi/user-service.yaml
curl http://localhost:8082/v3/api-docs -o docs/openapi/journal-service.yaml

# 2. Convert to Postman collections
npx openapi-to-postmanv2 \
  -s docs/openapi/user-service.yaml \
  -o docs/postman/user-service.postman_collection.json

npx openapi-to-postmanv2 \
  -s docs/openapi/journal-service.yaml \
  -o docs/postman/journal-service.postman_collection.json
```

Or import directly into Postman:
- `File → Import → URL`
- `http://localhost:8081/v3/api-docs` (User Service)
- `http://localhost:8082/v3/api-docs` (Journal Service)

---

## Postman environment

Create two environments in Postman — switch with one click:

**Local**
```json
{
  "baseUrl": "http://localhost:8080",
  "email": "bruno@email.com",
  "password": "YourPassword123",
  "accessToken": ""
}
```

**Azure**
```json
{
  "baseUrl": "https://api.bravoscribe.com",
  "email": "bruno@email.com",
  "password": "YourPassword123",
  "accessToken": ""
}
```

---

## Auto-login pre-request script

Add this to the **Collection → Pre-request Script** tab so every request
automatically logs in and sets the JWT before firing:

```javascript
// Auto-login and set accessToken before every request
const loginUrl = pm.environment.get('baseUrl') + '/api/users/login'

pm.sendRequest({
    url: loginUrl,
    method: 'POST',
    header: { 'Content-Type': 'application/json' },
    body: {
        mode: 'raw',
        raw: JSON.stringify({
            email: pm.environment.get('email'),
            password: pm.environment.get('password')
        })
    }
}, (err, res) => {
    if (err) {
        console.error('Login failed:', err)
    } else {
        pm.environment.set('accessToken', res.json().accessToken)
        console.log('accessToken refreshed')
    }
})
```

Then set the **Authorization** tab on the collection to:
- Type: `Bearer Token`
- Token: `{{accessToken}}`

All requests in the collection will automatically use the token.

---

## Collections

| File | Service | Endpoints |
|---|---|---|
| `user-service.postman_collection.json` | User Service | 12 endpoints — auth, JWT, password reset, preferences |
| `journal-service.postman_collection.json` | Journal Service | 12 endpoints — entries, tags, export, stats |

> **Note:** Notification Service has no REST endpoints — Kafka-driven only.
> No Postman collection needed.

---

## Quick test sequence

After importing, run requests in this order to verify the full flow:

```
1. POST /api/users/register
2. POST /api/users/login          ← sets accessToken automatically
3. GET  /api/users/me             ← verify JWT works
4. POST /api/journal/entries      ← create first entry
5. GET  /api/journal/stats        ← verify stats
6. GET  /api/journal/entries/export ← download zip
```

---

## When to generate

| Phase | Action |
|---|---|
| Phase 2 done | Generate `user-service.postman_collection.json` |
| Phase 3 done | Generate `journal-service.postman_collection.json` |
| Phase 8 done | Add Azure environment, test against live API |
