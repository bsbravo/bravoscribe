import { NavLink, useNavigate } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { LogOut, NotebookPen, User } from "lucide-react"
import { listEntries } from "@/api/journal"
import { logout as logoutRequest } from "@/api/auth"
import { useAuthStore } from "@/store/authStore"
import { ScrollArea } from "@/components/ui/scroll-area"
import { ThemeToggle } from "@/components/ThemeToggle"
import { MOOD_EMOJI } from "@/constants/mood"
import { getEntryPreview, getEntryTitle } from "@/lib/entry"
import { cn } from "@/lib/utils"

const navItems = [
  { to: "/", label: "Today", icon: NotebookPen, end: true },
  { to: "/entries", label: "All entries", icon: NotebookPen, end: false },
  { to: "/profile", label: "Profile", icon: User, end: false },
]

export function Sidebar() {
  const navigate = useNavigate()
  const clearAuth = useAuthStore((s) => s.clear)

  const { data } = useQuery({
    queryKey: ["entries", "sidebar-recent"],
    queryFn: () => listEntries({ size: 10 }),
  })

  async function handleSignOut() {
    try {
      await logoutRequest()
    } finally {
      clearAuth()
      navigate("/login")
    }
  }

  return (
    <aside className="hidden w-[260px] shrink-0 flex-col border-r border-border bg-parchment-2 md:flex">
      <div className="flex items-center justify-between px-5 py-6">
        <h1 className="font-serif text-xl font-medium tracking-tight text-ink">📓 Bravoscribe</h1>
        <ThemeToggle />
      </div>

      <nav className="flex flex-col gap-1 px-3">
        {navItems.map(({ to, label, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              cn(
                "rounded-lg px-3 py-2 text-sm font-medium text-ink-2 transition-colors hover:bg-parchment-3",
                isActive && "bg-parchment-3 text-ink"
              )
            }
          >
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="mt-6 flex-1 overflow-hidden px-3">
        <p className="px-2 pb-2 text-xs font-medium tracking-wide text-ink-4 uppercase">
          Recent entries
        </p>
        <ScrollArea className="h-full pb-4">
          <ul className="flex flex-col gap-1 pr-2">
            {data?.content.map((entry) => (
              <li key={entry.id}>
                <NavLink
                  to={`/entries/${entry.entryDate}`}
                  className={({ isActive }) =>
                    cn(
                      "flex items-start gap-2 rounded-lg px-2 py-2 text-left hover:bg-parchment-3",
                      isActive && "bg-parchment-3"
                    )
                  }
                >
                  {entry.mood ? (
                    <span className="mt-0.5 text-sm leading-none">{MOOD_EMOJI[entry.mood]}</span>
                  ) : (
                    <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-border" />
                  )}
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-serif text-sm text-ink">
                      {getEntryTitle(entry)}
                    </span>
                    <span className="block truncate text-xs text-ink-3">
                      {getEntryPreview(entry, 40)}
                    </span>
                  </span>
                </NavLink>
              </li>
            ))}
          </ul>
        </ScrollArea>
      </div>

      <div className="border-t border-border p-3">
        <button
          type="button"
          onClick={handleSignOut}
          className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium text-ink-2 hover:bg-parchment-3"
        >
          <LogOut size={16} />
          Sign out
        </button>
      </div>
    </aside>
  )
}
