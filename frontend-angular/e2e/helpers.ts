import type { Page } from "@playwright/test"
import { Client } from "pg"

const API_BASE_URL = "http://localhost:8080"

export interface TestUser {
  name: string
  email: string
  password: string
}

export function uniqueUser(label: string): TestUser {
  const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  return {
    name: `E2E ${label}`,
    email: `e2e-ng-${label}-${id}@example.com`,
    password: "e2e-test-password-1",
  }
}

export async function registerUser(user: TestUser): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/api/users/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(user),
  })
  if (!res.ok) throw new Error(`register failed: ${res.status} ${await res.text()}`)
}

// AdminController has no self-service admin-creation endpoint (by design — see
// SPEC.md), so tests promote a freshly registered user directly in the DB,
// same as the manual verification this suite replaces.
export async function promoteToAdmin(email: string): Promise<void> {
  const client = new Client({
    connectionString:
      process.env.E2E_DATABASE_URL ?? "postgresql://postgres:postgres@localhost:5432/journal",
  })
  await client.connect()
  try {
    await client.query("UPDATE users.users SET role = 'ADMIN' WHERE email = $1", [email])
  } finally {
    await client.end()
  }
}

export async function registerAdmin(label: string): Promise<TestUser> {
  const user = uniqueUser(label)
  await registerUser(user)
  await promoteToAdmin(user.email)
  return user
}

export async function login(page: Page, user: Pick<TestUser, "email" | "password">): Promise<void> {
  await page.goto("/login")
  await page.getByRole("textbox", { name: "Email" }).fill(user.email)
  await page.getByRole("textbox", { name: "Password" }).fill(user.password)
  await page.getByRole("button", { name: "Sign in" }).click()
}

export async function loginAsAdmin(page: Page, label: string): Promise<TestUser> {
  const admin = await registerAdmin(label)
  await login(page, admin)
  await page.waitForURL("**/dashboard")
  return admin
}

// The user list only loads the first page (31 rows) by default, and the DB
// accumulates users across the whole E2E run — a user registered earlier in
// the run can fall outside that window by the time a later test checks for
// it. Bump to the paginator's largest page size so a just-registered user is
// reliably present, instead of depending on registration order/timing.
export async function goToUsersList(page: Page): Promise<void> {
  // Client-side nav (not page.goto, a hard reload) — matches how a real user
  // gets here and avoids racing the app's silent-refresh-on-boot/auth guard.
  await page.getByRole("link", { name: "Users" }).click()
  await page.waitForURL("**/users")
  // force: true — Material's MDC touch-target overlay div intercepts the
  // pointer event at the select's visual center, a known MDC/Playwright quirk.
  await page.getByLabel("Items per page:").click({ force: true })
  await page.getByRole("option", { name: "100" }).click()
}
