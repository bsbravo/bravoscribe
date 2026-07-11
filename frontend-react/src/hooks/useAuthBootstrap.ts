import { useEffect, useState } from "react"
import { apiClient } from "@/api/client"
import { getMe } from "@/api/users"
import { useAuthStore } from "@/store/authStore"
import type { AccessTokenResponse } from "@/api/types"

// Access tokens live only in memory, so a page reload always starts logged-out.
// On mount, try a silent refresh against the httpOnly cookie to restore the session
// before any route decides whether the user is authenticated.
export function useAuthBootstrap() {
  const [isReady, setIsReady] = useState(false)
  const setSession = useAuthStore((s) => s.setSession)

  useEffect(() => {
    let cancelled = false

    async function bootstrap() {
      try {
        const { data } = await apiClient.post<AccessTokenResponse>("/api/users/refresh")
        if (cancelled) return
        setSession(data.accessToken)
        const user = await getMe()
        if (!cancelled) setSession(data.accessToken, user)
      } catch {
        // No valid session cookie — stay logged out.
      } finally {
        if (!cancelled) setIsReady(true)
      }
    }

    bootstrap()
    return () => {
      cancelled = true
    }
  }, [setSession])

  return isReady
}
