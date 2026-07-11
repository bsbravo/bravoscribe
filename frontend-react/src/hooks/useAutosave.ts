import { useEffect } from "react"

interface UseAutosaveOptions {
  enabled: boolean
  isDirty: boolean
  onSave: () => void
  delayMs?: number
}

// Fires onSave 30s after the last change — never on mount, never before the first
// explicit Save (enabled = false until an entryId exists).
export function useAutosave({ enabled, isDirty, onSave, delayMs = 30_000 }: UseAutosaveOptions) {
  useEffect(() => {
    if (!enabled || !isDirty) return
    const timer = setTimeout(onSave, delayMs)
    return () => clearTimeout(timer)
  }, [enabled, isDirty, onSave, delayMs])
}
