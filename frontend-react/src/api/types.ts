export type Mood = "GREAT" | "GOOD" | "NEUTRAL" | "BAD" | "TERRIBLE"

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
}

export interface Page<T> {
  totalElements: number
  totalPages: number
  size: number
  content: T[]
  number: number
  first: boolean
  last: boolean
  numberOfElements: number
  empty: boolean
}

// ---- User Service ----

export interface UserResponse {
  id: string
  name: string
  email: string
  role: string
  active: boolean
  createdAt: string
}

export interface AccessTokenResponse {
  accessToken: string
}

export interface RegisterRequest {
  name: string
  email: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface UpdateProfileRequest {
  name: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface PasswordResetRequestBody {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface PreferencesResponse {
  reminderTime: string
  weeklySummaryEnabled: boolean
}

export interface UpdatePreferencesRequest {
  reminderTime?: string
  weeklySummaryEnabled?: boolean
}

// ---- Journal Service ----

export interface TagResponse {
  id: string
  name: string
}

export interface CreateTagRequest {
  name: string
}

export interface JournalEntryResponse {
  id: string
  entryDate: string
  title: string | null
  content: string
  mood: Mood | null
  tags: TagResponse[]
  createdAt: string
  updatedAt: string
}

export interface CreateEntryRequest {
  entryDate: string
  title?: string
  content: string
  mood?: Mood
  tagIds?: string[]
}

export interface UpdateEntryRequest {
  title?: string
  content: string
  mood?: Mood
  tagIds?: string[]
}

export interface StatsResponse {
  totalEntries: number
  totalWords: number
  currentStreak: number
  longestStreak: number
  firstEntryDate: string | null
}
