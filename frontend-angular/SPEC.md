# Angular Back-office — Specification

> See `SYSTEM_SPEC.md` for system-wide conventions.

## Responsibility
Admin back-office for managing users. Not accessible to end users.

## Service access

| Service | Access | Reason |
|---|---|---|
| **User Service** | ✅ Yes | Full access — user list, detail, deactivation, login |
| **Journal Service** | ❌ No | Journal entries are private — admins cannot read or manage them |
| **Notification Service** | ❌ No | Internal service — no client access |

> **Privacy rule:** Admins can manage user accounts but have no visibility into journal entry content. This is enforced at the service layer — Journal Service always scopes queries by the JWT `userId`, and admin tokens are not granted any bypass.

## Hosting
**Azure Static Web Apps** (brazilsouth) — separate app from React, separate URL.

## Technology
| Property | Value |
|---|---|
| Framework | Angular 22 + TypeScript |
| State management | NgRx (signals-based) |
| Routing | Angular Router (lazy-loaded modules) |
| UI library | Angular Material |
| API client | Auto-generated from OpenAPI spec |
| Auth | JWT via HttpInterceptor; refresh token in httpOnly cookie |
| Testing | Jest + Angular Testing Library |

## Pages / screens
| Route | Description |
|---|---|
| `/login` | Admin login |
| `/dashboard` | Summary stats: total users, new users this week, deactivated accounts |
| `/users` | User list with search and pagination |
| `/users/:id` | User detail — view info, deactivate account |

**Session management:**
- Logout button in top navigation bar — calls `DELETE /api/users/logout`, clears NgRx auth store, redirects to `/login`
- On 401 response from any API call: `HttpInterceptor` clears store and redirects to `/login` with `?reason=session-expired`
- Login page shows "Your session has expired. Please sign in again." when `reason=session-expired` is in the URL

## UX rules

### Login page
- On 401 (wrong credentials): show inline error "Invalid email or password"
- Password field includes visibility toggle (Angular Material `matSuffix`):

```html
<mat-form-field>
  <mat-label>Password</mat-label>
  <input matInput [type]="hidePassword ? 'password' : 'text'" />
  <button mat-icon-button matSuffix (click)="hidePassword = !hidePassword"
          [attr.aria-label]="hidePassword ? 'Show password' : 'Hide password'">
    <mat-icon>{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
  </button>
</mat-form-field>
```
- Default state: password hidden
- Uses Angular Material `mat-icon-button` + `matSuffix` — consistent with Material Design
- On `?reason=session-expired` in URL: show banner "Your session has expired. Please sign in again."
- No register link — admin accounts are created out of band

### Dashboard empty state
- When `totalUsers === 0`: show "No users registered yet" placeholder
- Stats cards still render with value `0` — do not hide them

### Deactivation confirmation
- `/users/:id` shows a **Deactivate account** button
- Clicking it opens a confirmation dialog:
  - Title: "Deactivate this account?"
  - Body: "The user will be immediately logged out and will not be able to log in again. This action cannot be undone."
  - Buttons: "Cancel" (secondary) · "Deactivate" (warn/destructive)
- Only fires `PUT /api/users/{id}/deactivate` after confirmation
- On success: show snackbar "Account deactivated" and navigate back to `/users`

### Logout
- Logout button always visible in the top navigation bar
- Calls `DELETE /api/users/logout` → clears NgRx auth store → redirects to `/login`
- On any 401 from API: `HttpInterceptor` auto-redirects to `/login?reason=session-expired`

## Environment variables
```env
API_BASE_URL=https://[apim-name].azure-api.net
```

## Password policy
> Defined in `services/user-service/SPEC.md` — minimum 8 characters, maximum 128. No complexity rules.
> Angular back-office only has login — no register or change password screen. No password validation needed on Angular side.

## AI Regeneration Prompt
```
Using SYSTEM_SPEC.md and frontend-angular/SPEC.md, generate the full
Angular back-office scaffold. Include: lazy-loaded routing, NgRx auth
store, HttpInterceptor for JWT, AuthGuard, login page (with 401 error
message on failed login and session-expired message), dashboard with
stats (empty state when zero users), and users feature module with list
and detail/deactivation pages. Deactivation must show a confirmation
dialog: "Deactivate this account? The user will be immediately logged out
and will not be able to log in again." with Cancel and Confirm buttons.
Logout button in top nav calls DELETE /api/users/logout.
```
