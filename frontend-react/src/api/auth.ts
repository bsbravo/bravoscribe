import { apiClient } from "@/api/client"
import type {
  AccessTokenResponse,
  LoginRequest,
  PasswordResetRequestBody,
  RegisterRequest,
  ResetPasswordRequest,
} from "@/api/types"

export function login(body: LoginRequest) {
  return apiClient.post<AccessTokenResponse>("/api/users/login", body).then((r) => r.data)
}

export function register(body: RegisterRequest) {
  return apiClient.post<AccessTokenResponse>("/api/users/register", body).then((r) => r.data)
}

export function logout() {
  return apiClient.delete<void>("/api/users/logout")
}

export function requestPasswordReset(body: PasswordResetRequestBody) {
  return apiClient.post<void>("/api/users/password-reset/request", body)
}

export function confirmPasswordReset(body: ResetPasswordRequest) {
  return apiClient.put<void>("/api/users/password-reset/confirm", body)
}
