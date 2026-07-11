import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios"
import { useAuthStore } from "@/store/authStore"
import type { AccessTokenResponse } from "@/api/types"

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "",
  withCredentials: true, // send/receive the httpOnly refreshToken cookie
})

const REFRESH_URL = "/api/users/refresh"
const NO_AUTH_URLS = ["/api/users/login", "/api/users/register", "/api/users/refresh"]

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token && !NO_AUTH_URLS.some((url) => config.url?.startsWith(url))) {
    config.headers.set("Authorization", `Bearer ${token}`)
  }
  return config
})

let isRefreshing = false
let pendingQueue: Array<(token: string | null) => void> = []

function onRefreshed(token: string | null) {
  pendingQueue.forEach((resolve) => resolve(token))
  pendingQueue = []
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined

    if (
      error.response?.status !== 401 ||
      !original ||
      original._retry ||
      NO_AUTH_URLS.some((url) => original.url?.startsWith(url))
    ) {
      return Promise.reject(error)
    }

    original._retry = true

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        pendingQueue.push((token) => {
          if (!token) {
            reject(error)
            return
          }
          original.headers.set("Authorization", `Bearer ${token}`)
          resolve(apiClient(original))
        })
      })
    }

    isRefreshing = true
    try {
      const { data } = await apiClient.post<AccessTokenResponse>(REFRESH_URL)
      useAuthStore.getState().setSession(data.accessToken, useAuthStore.getState().user)
      onRefreshed(data.accessToken)
      original.headers.set("Authorization", `Bearer ${data.accessToken}`)
      return apiClient(original)
    } catch (refreshError) {
      onRefreshed(null)
      useAuthStore.getState().clear()
      const reason = "session-expired"
      if (window.location.pathname !== "/login") {
        window.location.href = `/login?reason=${reason}`
      }
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  }
)
