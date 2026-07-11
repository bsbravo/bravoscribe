import type { Page } from "@playwright/test"

export interface TestUser {
  name: string
  email: string
  password: string
}

export function uniqueUser(label: string): TestUser {
  const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  return {
    name: `E2E ${label}`,
    email: `e2e-${label}-${id}@example.com`,
    password: "e2e-test-password-1",
  }
}

/** Registers a brand new user and waits for the auto-login redirect to Today. */
export async function registerAndLogin(page: Page, user: TestUser): Promise<void> {
  await page.goto("/register")
  await page.getByPlaceholder("Name").fill(user.name)
  await page.getByPlaceholder("Email").fill(user.email)
  await page.getByPlaceholder("Password").fill(user.password)
  await page.getByRole("button", { name: "Sign Up" }).click()
  await page.waitForURL("/")
}
