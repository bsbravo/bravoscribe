import { Moon, Sun } from "lucide-react"
import { useTheme } from "@/hooks/useTheme"
import { Button } from "@/components/ui/button"

export function ThemeToggle() {
  const { theme, toggle } = useTheme()

  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label="Toggle theme">
      {theme === "warm" ? <Sun size={20} /> : <Moon size={20} />}
    </Button>
  )
}
