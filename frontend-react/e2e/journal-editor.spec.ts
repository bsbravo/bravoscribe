import { test, expect } from "@playwright/test"
import { registerAndLogin, uniqueUser } from "./helpers"

test.describe("Today editor", () => {
  test("writing and saving an entry works end to end", async ({ page }) => {
    await registerAndLogin(page, uniqueUser("editor"))

    await expect(page.getByText("not saved yet")).toBeVisible()
    await expect(page.getByRole("button", { name: "Save" })).toBeDisabled()

    await page.locator("#entry-content").fill("Today I wrote an end-to-end test.")
    await expect(page.getByRole("button", { name: "Save" })).toBeEnabled()

    await page.getByRole("radio", { name: "Great" }).click()

    await page.getByRole("button", { name: "add tag" }).click()
    await page.getByPlaceholder("Tag name").fill("e2e")
    await page.keyboard.press("Enter")
    await expect(page.getByText("e2e", { exact: true })).toBeVisible()

    await page.getByRole("button", { name: "Save" }).click()
    await expect(page.getByText("saved", { exact: true })).toBeVisible()
    await expect(page.getByRole("radio", { name: "Great" })).toHaveAttribute("aria-checked", "true")

    await expect(page.getByText("6 words")).toBeVisible()
  })

  test("saved content survives a page reload", async ({ page }) => {
    await registerAndLogin(page, uniqueUser("reload"))

    await page.locator("#entry-content").fill("This entry should still be here after reload.")
    await page.getByRole("button", { name: "Save" }).click()
    await expect(page.getByText("saved", { exact: true })).toBeVisible()

    await page.reload()

    await expect(page.locator("#entry-content")).toHaveValue(
      "This entry should still be here after reload."
    )
  })

  test("Save stays disabled until there is content", async ({ page }) => {
    await registerAndLogin(page, uniqueUser("emptysave"))

    const saveButton = page.getByRole("button", { name: "Save" })
    await expect(saveButton).toBeDisabled()

    await page.locator("#entry-content").fill("x")
    await expect(saveButton).toBeEnabled()

    await page.locator("#entry-content").fill("")
    await expect(saveButton).toBeDisabled()
  })
})
