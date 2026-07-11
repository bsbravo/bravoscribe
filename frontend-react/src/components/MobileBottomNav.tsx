import { NavLink } from "react-router-dom"
import { NotebookPen, ListTodo, User } from "lucide-react"
import { cn } from "@/lib/utils"

const navItems = [
  { to: "/", label: "Today", icon: NotebookPen, end: true },
  { to: "/entries", label: "Entries", icon: ListTodo, end: false },
  { to: "/profile", label: "Profile", icon: User, end: false },
]

export function MobileBottomNav() {
  return (
    <nav className="fixed inset-x-0 bottom-0 z-10 flex border-t border-border bg-parchment-2 md:hidden">
      {navItems.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) =>
            cn(
              "flex flex-1 flex-col items-center gap-1 py-2 text-xs font-medium text-ink-3",
              isActive && "text-ink"
            )
          }
        >
          <Icon size={20} />
          {label}
        </NavLink>
      ))}
    </nav>
  )
}
