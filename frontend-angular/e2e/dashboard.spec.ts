import { test, expect } from "@playwright/test"
import { loginAsAdmin } from "./helpers"

test.describe("Dashboard", () => {
  test("shows real stats derived from the user list", async ({ page }) => {
    await loginAsAdmin(page, "dashboard")

    // Tests register real users concurrently, so assert boundaries rather than
    // exact counts — the point is confirming the stats are wired to real data
    // (the same GET /api/users call that was broken by the nginx routing/CORS
    // bugs this suite exists to catch a regression of), not a fixed number.
    const totalUsers = page.locator(".stat-card", { hasText: "Total users" }).locator(".stat-value")
    await expect(totalUsers).not.toHaveText("0")

    await expect(page.getByText("New users this week")).toBeVisible()
    await expect(page.getByText("Deactivated accounts")).toBeVisible()
  })
})
