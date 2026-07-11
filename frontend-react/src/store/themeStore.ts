import { create } from "zustand"
import { persist } from "zustand/middleware"

export type Theme = "warm" | "chronicle"

interface ThemeState {
  theme: Theme
  toggle: () => void
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set, get) => ({
      theme: "warm",
      toggle: () => set({ theme: get().theme === "warm" ? "chronicle" : "warm" }),
    }),
    { name: "bravoscribe-theme" }
  )
)
