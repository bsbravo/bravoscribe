import { test, expect } from "@playwright/test"
import { registerAndLogin, uniqueUser } from "./helpers"

test.describe("Theme", () => {
  test("toggling switches to Chronicle and persists across reload", async ({ page }) => {
    await registerAndLogin(page, uniqueUser("theme"))

    const html = page.locator("html")
    await expect(html).not.toHaveAttribute("data-theme", "chronicle")

    await page.getByRole("button", { name: "Toggle theme" }).click()
    await expect(html).toHaveAttribute("data-theme", "chronicle")

    await page.reload()
    await expect(html).toHaveAttribute("data-theme", "chronicle")

    // Toggle back — should return to the warm (non-chronicle) state.
    await page.getByRole("button", { name: "Toggle theme" }).click()
    await expect(html).not.toHaveAttribute("data-theme", "chronicle")
  })

  test("theme toggle is available before login on the auth pages", async ({ page }) => {
    await page.goto("/login")
    const html = page.locator("html")

    await page.getByRole("button", { name: "Toggle theme" }).click()
    await expect(html).toHaveAttribute("data-theme", "chronicle")

    // Persists through registration and into the authenticated app — no flash back to warm.
    await page.goto("/register")
    await expect(html).toHaveAttribute("data-theme", "chronicle")
  })
})
