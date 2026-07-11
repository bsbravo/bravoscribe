import type { JournalEntryResponse } from "@/api/types"

// title/mood/tags are all optional — null title falls back to the first line of content.
export function getEntryTitle(entry: Pick<JournalEntryResponse, "title" | "content">): string {
  if (entry.title) return entry.title
  const firstLine = entry.content.split("\n")[0].trim()
  return firstLine || "Untitled"
}

export function getEntryPreview(
  entry: Pick<JournalEntryResponse, "title" | "content">,
  maxLength = 120
): string {
  // When there's no title, the first line of content doubles as the title
  // (getEntryTitle). Skip it here so the preview doesn't just repeat it —
  // unless it's the only line, in which case there's nothing else to show.
  const lines = entry.content.split("\n")
  const remainder = entry.title || lines.length === 1 ? entry.content : lines.slice(1).join(" ")
  const clean = remainder.replace(/\s+/g, " ").trim()
  if (clean.length <= maxLength) return clean
  return clean.slice(0, maxLength).trimEnd() + "…"
}

export function getWordCount(content: string): number {
  const trimmed = content.trim()
  return trimmed ? trimmed.split(/\s+/).length : 0
}
