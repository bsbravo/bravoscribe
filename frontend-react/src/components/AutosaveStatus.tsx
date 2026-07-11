import { cn } from "@/lib/utils"

export type SaveStatus = "not-saved" | "saving" | "saved" | "unsaved" | "error"

const LABEL: Record<SaveStatus, string> = {
  "not-saved": "not saved yet",
  saving: "saving...",
  saved: "saved",
  unsaved: "unsaved changes",
  error: "save failed — retry",
}

export function AutosaveStatus({ status }: { status: SaveStatus }) {
  return (
    <span
      className={cn(
        "text-xs font-medium",
        status === "saved" && "text-teal",
        status === "error" && "text-destructive",
        (status === "not-saved" || status === "unsaved") && "text-ink-3",
        status === "saving" && "text-ink-4"
      )}
    >
      {LABEL[status]}
    </span>
  )
}
