import { useEffect } from "react"

export function useEditorShortcuts(isSaveEnabled: boolean, handleSave: () => void) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "s") {
        e.preventDefault()
        if (isSaveEnabled) handleSave()
      }
    }
    window.addEventListener("keydown", handler)
    return () => window.removeEventListener("keydown", handler)
  }, [isSaveEnabled, handleSave])
}
