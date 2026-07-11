import { test, expect } from "@playwright/test"
import { goToUsersList, loginAsAdmin, registerUser, uniqueUser } from "./helpers"

test.describe("Deactivate account", () => {
  test("deactivating a user shows the confirmation dialog, a success message, and blocks their login", async ({
    page,
  }) => {
    const target = uniqueUser("deactivate")
    await registerUser(target)
    await loginAsAdmin(page, "deactivator")

    await goToUsersList(page)
    await page.getByRole("cell", { name: target.email }).click()
    // name alone isn't unique across runs (only email has the timestamp/random
    // suffix) — scope to the detail card's title, the only one on this page.
    await expect(page.locator("mat-card-title")).toHaveText(target.name)

    await page.getByRole("button", { name: "Deactivate account" }).click()
    await expect(page.getByText("Deactivate this account?")).toBeVisible()
    await page.getByRole("button", { name: "Deactivate", exact: true }).click()

    // Confirming redirects back to the users list (see onDeactivateSuccess$ in
    // users.effects.ts) — it doesn't stay on the detail page.
    await expect(page.getByText("Account deactivated")).toBeVisible()
    await expect(page).toHaveURL(/\/users$/)

    const loginRes = await page.request.post("http://localhost:8080/api/users/login", {
      data: { email: target.email, password: target.password },
    })
    expect(loginRes.status()).toBe(401)
  })

  test("canceling the confirmation dialog leaves the account active", async ({ page }) => {
    const target = uniqueUser("cancel")
    await registerUser(target)
    await loginAsAdmin(page, "canceler")

    await goToUsersList(page)
    await page.getByRole("cell", { name: target.email }).click()

    await page.getByRole("button", { name: "Deactivate account" }).click()
    await page.getByRole("button", { name: "Cancel" }).click()

    await expect(page.getByText("Deactivate this account?")).not.toBeVisible()
    await expect(page.getByRole("button", { name: "Deactivate account" })).toBeEnabled()
  })
})
