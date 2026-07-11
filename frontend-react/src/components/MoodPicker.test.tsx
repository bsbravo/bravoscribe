import { describe, it, expect, vi } from "vitest"
import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { MoodPicker } from "./MoodPicker"

describe("MoodPicker", () => {
  it("renders all 5 mood options", () => {
    render(<MoodPicker value={null} onChange={vi.fn()} />)
    expect(screen.getAllByRole("radio")).toHaveLength(5)
  })

  it("marks none as checked when value is null", () => {
    render(<MoodPicker value={null} onChange={vi.fn()} />)
    for (const radio of screen.getAllByRole("radio")) {
      expect(radio).toHaveAttribute("aria-checked", "false")
    }
  })

  it("marks the matching mood as checked", () => {
    render(<MoodPicker value="GOOD" onChange={vi.fn()} />)
    expect(screen.getByRole("radio", { name: "Good" })).toHaveAttribute("aria-checked", "true")
  })

  it("calls onChange with the clicked mood", async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<MoodPicker value={null} onChange={onChange} />)

    await user.click(screen.getByRole("radio", { name: "Great" }))

    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange).toHaveBeenCalledWith("GREAT")
  })

  it("only ever reports a single selected mood at a time (radiogroup semantics)", () => {
    render(<MoodPicker value="TERRIBLE" onChange={vi.fn()} />)
    const checked = screen.getAllByRole("radio").filter((r) => r.getAttribute("aria-checked") === "true")
    expect(checked).toHaveLength(1)
  })
})
