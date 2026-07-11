import { Outlet } from "react-router-dom"
import { Sidebar } from "@/components/Sidebar"
import { MobileBottomNav } from "@/components/MobileBottomNav"

export function AppLayout() {
  return (
    <div className="flex min-h-screen bg-parchment">
      <Sidebar />
      <main className="min-w-0 flex-1 pb-16 md:pb-0">
        <Outlet />
      </main>
      <MobileBottomNav />
    </div>
  )
}
