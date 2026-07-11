import { test, expect } from "@playwright/test"
import { login, loginAsAdmin, registerUser, uniqueUser } from "./helpers"

test.describe("Authentication", () => {
  test("a non-admin account is rejected client-side and stays on /login", async ({ page }) => {
    const user = uniqueUser("nonadmin")
    await registerUser(user)

    await login(page, user)

    await expect(page.getByText("This account does not have admin access.")).toBeVisible()
    await expect(page).toHaveURL("/login")
  })

  test("an admin account logs in and reaches the dashboard", async ({ page }) => {
    await loginAsAdmin(page, "login")
    await expect(page).toHaveURL(/\/dashboard$/)
  })

  test("logout returns to /login", async ({ page }) => {
    await loginAsAdmin(page, "logout")
    await page.getByRole("button", { name: "Log out" }).click()
    await expect(page).toHaveURL("/login")
  })

  test("unauthenticated visitors are redirected to /login", async ({ page }) => {
    await page.goto("/dashboard")
    await expect(page).toHaveURL("/login")
  })
})
