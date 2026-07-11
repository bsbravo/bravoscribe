import { describe, it, expect } from "vitest"
import { render, screen } from "@testing-library/react"
import { AutosaveStatus, type SaveStatus } from "./AutosaveStatus"

const EXPECTED_LABEL: Record<SaveStatus, string> = {
  "not-saved": "not saved yet",
  saving: "saving...",
  saved: "saved",
  unsaved: "unsaved changes",
  error: "save failed — retry",
}

describe("AutosaveStatus", () => {
  for (const status of Object.keys(EXPECTED_LABEL) as SaveStatus[]) {
    it(`renders the correct label for "${status}"`, () => {
      render(<AutosaveStatus status={status} />)
      expect(screen.getByText(EXPECTED_LABEL[status])).toBeInTheDocument()
    })
  }
})
