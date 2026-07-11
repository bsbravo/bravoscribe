import { apiClient } from "@/api/client"
import type {
  CreateEntryRequest,
  CreateTagRequest,
  JournalEntryResponse,
  Page,
  StatsResponse,
  TagResponse,
  UpdateEntryRequest,
} from "@/api/types"

export interface ListEntriesParams {
  page?: number
  size?: number
  from?: string
  to?: string
  q?: string
}

export function listEntries(params: ListEntriesParams = {}) {
  return apiClient
    .get<Page<JournalEntryResponse>>("/api/journal/entries", { params })
    .then((r) => r.data)
}

export function getEntry(id: string) {
  return apiClient.get<JournalEntryResponse>(`/api/journal/entries/${id}`).then((r) => r.data)
}

export function getEntryByDate(date: string) {
  return apiClient
    .get<JournalEntryResponse>(`/api/journal/entries/date/${date}`)
    .then((r) => r.data)
}

export function getEntryDates(from: string, to: string) {
  return apiClient
    .get<string[]>("/api/journal/entries/dates", { params: { from, to } })
    .then((r) => r.data)
}

export function createEntry(body: CreateEntryRequest) {
  return apiClient.post<JournalEntryResponse>("/api/journal/entries", body).then((r) => r.data)
}

export function updateEntry(id: string, body: UpdateEntryRequest) {
  return apiClient
    .put<JournalEntryResponse>(`/api/journal/entries/${id}`, body)
    .then((r) => r.data)
}

export function deleteEntry(id: string) {
  return apiClient.delete<void>(`/api/journal/entries/${id}`)
}

export function exportEntries(from: string, to: string) {
  return apiClient
    .get(`/api/journal/entries/export`, { params: { from, to }, responseType: "blob" })
    .then((r) => r.data as Blob)
}

export function getStats() {
  return apiClient.get<StatsResponse>("/api/journal/stats").then((r) => r.data)
}

export function listTags() {
  return apiClient.get<TagResponse[]>("/api/journal/tags").then((r) => r.data)
}

export function createTag(body: CreateTagRequest) {
  return apiClient.post<TagResponse>("/api/journal/tags", body).then((r) => r.data)
}

export function deleteTag(id: string) {
  return apiClient.delete<void>(`/api/journal/tags/${id}`)
}
