import { useEffect } from "react"
import { useThemeStore } from "@/store/themeStore"

export function useTheme() {
  const theme = useThemeStore((s) => s.theme)
  const toggle = useThemeStore((s) => s.toggle)

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme)
  }, [theme])

  return { theme, toggle }
}
