import { test, expect } from "@playwright/test"
import { registerAndLogin, uniqueUser } from "./helpers"

// The sidebar always shows a "Recent entries" list regardless of the current
// page, so any entry text also appears there — scope assertions to <main> to
// check the entries-list page itself, not the sidebar's copy of the same text.
// Content uses two distinct lines so the title (first line) and preview
// (remaining lines) don't duplicate the same text within one EntryCard either.

test.describe("All entries", () => {
  test("shows the zero-state for a brand new account", async ({ page }) => {
    await registerAndLogin(page, uniqueUser("emptylist"))
    await page.getByRole("link", { name: "All entries" }).click()

    await expect(page.getByText("No entries yet. Go write your first one!")).toBeVisible()
  })

  test("a saved entry appears in the list immediately", async ({ page }) => {
    await registerAndLogin(page, uniqueUser("listentry"))

    const title = `Findable entry ${Date.now()}`
    await page.locator("#entry-content").fill(`${title}\nSome body detail below the title line.`)
    await page.getByRole("button", { name: "Save" }).click()
    await expect(page.getByText("saved", { exact: true })).toBeVisible()

    await page.getByRole("link", { name: "All entries" }).click()
    await expect(page.locator("main").getByText(title)).toBeVisible()
  })

  test("search filters the list down to matching entries", async ({ page }) => {
    await registerAndLogin(page, uniqueUser("search"))

    const needle = `Needle${Date.now()}`
    await page.locator("#entry-content").fill(`${needle}\nSome unrelated body content here.`)
    await page.getByRole("button", { name: "Save" }).click()
    await expect(page.getByText("saved", { exact: true })).toBeVisible()

    await page.getByRole("link", { name: "All entries" }).click()
    // main h3 — the entry card's title specifically, not the dynamic page
    // heading above it (which also echoes the search query once filtered).
    await expect(page.locator("main h3", { hasText: needle })).toBeVisible()

    await page.getByPlaceholder("Search entries…").fill("something that does not exist anywhere")
    await expect(page.getByText("No entries found.")).toBeVisible({ timeout: 5000 })

    await page.getByPlaceholder("Search entries…").fill(needle)
    await expect(page.locator("main h3", { hasText: needle })).toBeVisible({ timeout: 5000 })
  })
})
