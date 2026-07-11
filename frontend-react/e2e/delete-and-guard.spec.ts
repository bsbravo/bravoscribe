import { test, expect } from "@playwright/test"
import { registerAndLogin, uniqueUser } from "./helpers"

test.describe("Delete + unsaved changes guard", () => {
  test("navigating away with unsaved changes shows the confirmation dialog", async ({ page }) => {
    await registerAndLogin(page, uniqueUser("guard"))

    await page.locator("#entry-content").fill("First save, so an entry exists.")
    await page.getByRole("button", { name: "Save" }).click()
    await expect(page.getByText("saved", { exact: true })).toBeVisible()

    await page.locator("#entry-content").fill("First save, so an entry exists. Plus more.")
    await page.getByRole("link", { name: "Profile" }).click()

    await expect(page.getByText("Leave without saving?")).toBeVisible()
    await expect(page).toHaveURL("/")

    await page.getByRole("button", { name: "Leave anyway" }).click()
    await expect(page).toHaveURL("/profile")
  })

  test("choosing 'Stay and save' saves the entry and cancels the navigation", async ({ page }) => {
    await registerAndLogin(page, uniqueUser("staysave"))

    await page.locator("#entry-content").fill("Original content.")
    await page.getByRole("button", { name: "Save" }).click()
    await expect(page.getByText("saved", { exact: true })).toBeVisible()

    await page.locator("#entry-content").fill("Original content. Edited before navigating away.")
    await page.getByRole("link", { name: "Profile" }).click()
    await expect(page.getByText("Leave without saving?")).toBeVisible()

    await page.getByRole("button", { name: "Stay and save" }).click()

    await expect(page).toHaveURL("/")
    await expect(page.getByText("saved", { exact: true })).toBeVisible()
  })

  test("deleting an entry shows an undo toast", async ({ page }) => {
    await registerAndLogin(page, uniqueUser("delete"))

    await page.locator("#entry-content").fill("An entry that is about to be deleted.")
    await page.getByRole("button", { name: "Save" }).click()
    await expect(page.getByText("saved", { exact: true })).toBeVisible()

    await page.getByRole("button", { name: "Delete entry" }).click()
    await expect(page.getByText("Delete this entry?")).toBeVisible()
    await page.getByRole("button", { name: "Delete", exact: true }).click()

    // exact: true — the editor's own post-delete placeholder text ("Entry deleted.
    // Back to all entries") would otherwise also match this toast's text.
    await expect(page.getByText("Entry deleted", { exact: true })).toBeVisible()
    await expect(page.getByText("Undo")).toBeVisible()
  })
})
