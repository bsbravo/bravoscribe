import { create } from "zustand"
import type { UserResponse } from "@/api/types"

interface AuthState {
  accessToken: string | null
  user: UserResponse | null
  setSession: (accessToken: string, user?: UserResponse | null) => void
  setUser: (user: UserResponse) => void
  clear: () => void
}

// accessToken lives in memory only — never localStorage/sessionStorage.
// Refresh token is an httpOnly cookie the browser manages; React never touches it.
export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  setSession: (accessToken, user = null) => set({ accessToken, user }),
  setUser: (user) => set({ user }),
  clear: () => set({ accessToken: null, user: null }),
}))
