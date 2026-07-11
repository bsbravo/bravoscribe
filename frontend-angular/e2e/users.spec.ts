import { test, expect } from "@playwright/test"
import { goToUsersList, loginAsAdmin, registerUser, uniqueUser } from "./helpers"

test.describe("Users list", () => {
  test("renders real rows and search filters within the loaded page", async ({ page }) => {
    const target = uniqueUser("findme")
    await registerUser(target)
    await loginAsAdmin(page, "userslist")

    await goToUsersList(page)
    await expect(page.getByRole("cell", { name: target.email })).toBeVisible()

    const search = page.getByLabel("Search this page")
    await search.fill(target.name)
    await expect(page.getByRole("cell", { name: target.email })).toBeVisible()

    await search.fill("zzz-no-such-user-zzz")
    await expect(page.getByText("No users found")).toBeVisible()
  })

  test("navigating to a row opens that user's detail page", async ({ page }) => {
    const target = uniqueUser("clickrow")
    await registerUser(target)
    await loginAsAdmin(page, "clickrow")

    await goToUsersList(page)
    await page.getByRole("cell", { name: target.email }).click()

    // name alone isn't unique across runs (only email has the timestamp/random
    // suffix) — scope to the detail card's title, the only one on this page.
    await expect(page.locator("mat-card-title")).toHaveText(target.name)
    await expect(page.getByText(target.email)).toBeVisible()
  })
})
