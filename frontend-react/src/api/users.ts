import { apiClient } from "@/api/client"
import type {
  ChangePasswordRequest,
  PreferencesResponse,
  UpdatePreferencesRequest,
  UpdateProfileRequest,
  UserResponse,
} from "@/api/types"

export function getMe() {
  return apiClient.get<UserResponse>("/api/users/me").then((r) => r.data)
}

export function updateProfile(body: UpdateProfileRequest) {
  return apiClient.put<UserResponse>("/api/users/me", body).then((r) => r.data)
}

export function changePassword(body: ChangePasswordRequest) {
  return apiClient.put<void>("/api/users/me/password", body)
}

export function updatePreferences(body: UpdatePreferencesRequest) {
  return apiClient.put<PreferencesResponse>("/api/users/me/preferences", body).then((r) => r.data)
}
