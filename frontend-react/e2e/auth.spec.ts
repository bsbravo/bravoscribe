import { test, expect } from "@playwright/test"
import { registerAndLogin, uniqueUser } from "./helpers"

test.describe("Authentication", () => {
  test("register auto-logs in and lands on the Today editor", async ({ page }) => {
    const user = uniqueUser("register")
    await registerAndLogin(page, user)

    await expect(page).toHaveURL("/")
    await expect(page.locator("#entry-content")).toBeVisible()
    await expect(page.getByRole("link", { name: "Today" })).toBeVisible()
  })

  test("registering with an already-used email shows an error", async ({ page }) => {
    const user = uniqueUser("dupe")
    await registerAndLogin(page, user)

    // Sign out, then try registering the same email again.
    await page.getByRole("button", { name: "Sign out" }).click()
    await page.waitForURL("/login**")

    await page.goto("/register")
    await page.getByPlaceholder("Name").fill(user.name)
    await page.getByPlaceholder("Email").fill(user.email)
    await page.getByPlaceholder("Password").fill(user.password)
    await page.getByRole("button", { name: "Sign Up" }).click()

    await expect(page.getByText("An account with this email already exists.")).toBeVisible()
    await expect(page).toHaveURL("/register")
  })

  test("logging in with the wrong password shows an error and does not sign in", async ({ page }) => {
    const user = uniqueUser("wrongpw")
    await registerAndLogin(page, user)
    await page.getByRole("button", { name: "Sign out" }).click()
    await page.waitForURL("/login**")

    await page.getByPlaceholder("Email").fill(user.email)
    await page.getByPlaceholder("Password").fill("not-the-right-password")
    await page.getByRole("button", { name: "Sign In" }).click()

    await expect(page.getByText("Incorrect email or password.")).toBeVisible()
    await expect(page).toHaveURL("/login")
  })

  test("logging in with correct credentials returns to the app", async ({ page }) => {
    const user = uniqueUser("relogin")
    await registerAndLogin(page, user)
    await page.getByRole("button", { name: "Sign out" }).click()
    await page.waitForURL("/login**")

    await page.getByPlaceholder("Email").fill(user.email)
    await page.getByPlaceholder("Password").fill(user.password)
    await page.getByRole("button", { name: "Sign In" }).click()

    await expect(page).toHaveURL("/")
    await expect(page.locator("#entry-content")).toBeVisible()
  })

  test("unauthenticated visitors are redirected to login", async ({ page }) => {
    await page.goto("/")
    await expect(page).toHaveURL("/login")
  })
})
