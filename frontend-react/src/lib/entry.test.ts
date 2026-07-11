import { describe, it, expect } from "vitest"
import { getEntryTitle, getEntryPreview, getWordCount } from "./entry"

describe("getEntryTitle", () => {
  it("returns the title when set", () => {
    expect(getEntryTitle({ title: "My Day", content: "Something happened." })).toBe("My Day")
  })

  it("falls back to the first line of content when title is null", () => {
    expect(getEntryTitle({ title: null, content: "A great day.\nMore details here." })).toBe(
      "A great day."
    )
  })

  it("trims whitespace from the fallback first line", () => {
    expect(getEntryTitle({ title: null, content: "  Padded line  \nrest" })).toBe("Padded line")
  })

  it("falls back to 'Untitled' when title is null and content is blank", () => {
    expect(getEntryTitle({ title: null, content: "   " })).toBe("Untitled")
  })
})

describe("getEntryPreview", () => {
  it("uses the full content when a title is set", () => {
    expect(getEntryPreview({ title: "My Day", content: "Line one content here." })).toBe(
      "Line one content here."
    )
  })

  it("does not go blank for a single-line, untitled entry (regression)", () => {
    // Previously: title null + single-line content produced an empty preview,
    // because the function unconditionally dropped the first line assuming
    // there'd be a remainder. Single-line entries are the common case.
    const preview = getEntryPreview({ title: null, content: "Just one short line." })
    expect(preview).toBe("Just one short line.")
  })

  it("skips the first line (used as the title fallback) for multi-line untitled entries", () => {
    const preview = getEntryPreview({
      title: null,
      content: "First line as title\nSecond line onward\nThird line",
    })
    expect(preview).not.toContain("First line as title")
    expect(preview).toContain("Second line onward")
  })

  it("collapses internal whitespace and newlines to single spaces", () => {
    const preview = getEntryPreview({ title: "T", content: "Word1   \n\n  Word2\tWord3" })
    expect(preview).toBe("Word1 Word2 Word3")
  })

  it("truncates content longer than maxLength with an ellipsis", () => {
    const longContent = "a".repeat(200)
    const preview = getEntryPreview({ title: "T", content: longContent }, 50)
    expect(preview.length).toBe(51) // 50 chars + ellipsis
    expect(preview.endsWith("…")).toBe(true)
  })

  it("does not truncate content at or under maxLength", () => {
    const content = "a".repeat(50)
    const preview = getEntryPreview({ title: "T", content }, 50)
    expect(preview).toBe(content)
  })
})

describe("getWordCount", () => {
  it("returns 0 for empty content", () => {
    expect(getWordCount("")).toBe(0)
  })

  it("returns 0 for whitespace-only content", () => {
    expect(getWordCount("   \n\t  ")).toBe(0)
  })

  it("counts words separated by single spaces", () => {
    expect(getWordCount("one two three")).toBe(3)
  })

  it("counts words correctly across multiple spaces and newlines", () => {
    expect(getWordCount("one   two\n\nthree   four")).toBe(4)
  })
})
