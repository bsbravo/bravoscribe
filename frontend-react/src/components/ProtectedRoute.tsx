import { Navigate, Outlet } from "react-router-dom"
import { useAuthStore } from "@/store/authStore"

export function ProtectedRoute() {
  const accessToken = useAuthStore((s) => s.accessToken)

  if (!accessToken) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}
