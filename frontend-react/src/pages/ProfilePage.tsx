import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { getStats } from "@/api/journal"
import { updateProfile } from "@/api/users"
import { logout as logoutRequest } from "@/api/auth"
import { useAuthStore } from "@/store/authStore"
import { useDocumentTitle } from "@/hooks/useDocumentTitle"
import { formatLongDate } from "@/lib/date"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Separator } from "@/components/ui/separator"
import { ChangePasswordModal } from "@/components/ChangePasswordModal"

export function ProfilePage() {
  useDocumentTitle("Profile · Bravoscribe")
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const setUser = useAuthStore((s) => s.setUser)
  const clearAuth = useAuthStore((s) => s.clear)

  const [name, setName] = useState(user?.name ?? "")
  const [saving, setSaving] = useState(false)
  const isDirty = name.trim() !== "" && name !== user?.name

  const { data: stats } = useQuery({ queryKey: ["journal", "stats"], queryFn: getStats })

  async function handleSaveName() {
    setSaving(true)
    try {
      const updated = await updateProfile({ name: name.trim() })
      setUser(updated)
    } finally {
      setSaving(false)
    }
  }

  async function handleSignOut() {
    try {
      await logoutRequest()
    } finally {
      clearAuth()
      navigate("/login")
    }
  }

  const memberSince = stats?.firstEntryDate ?? user?.createdAt.slice(0, 10)

  return (
    <div className="mx-auto max-w-xl px-6 py-10">
      <h1 className="mb-8 font-serif text-2xl font-medium text-ink">Profile</h1>

      {/* Account */}
      <section className="mb-8">
        <div className="mb-4 flex items-center gap-3">
          <Avatar className="size-12">
            <AvatarFallback className="bg-accent-light font-serif text-brand-accent">
              {user?.name?.[0]?.toUpperCase() ?? "?"}
            </AvatarFallback>
          </Avatar>
          <div>
            <p className="font-medium text-ink">{user?.name}</p>
            <p className="text-sm text-ink-3">{user?.email}</p>
          </div>
        </div>

        <label className="mb-1 block text-xs font-medium text-ink-3">Display name</label>
        <div className="flex items-center gap-2">
          <Input value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
          {isDirty && (
            <Button size="sm" onClick={handleSaveName} disabled={saving}>
              {saving ? "Saving…" : "Save"}
            </Button>
          )}
        </div>

        <div className="mt-4">
          <ChangePasswordModal trigger={<Button variant="outline">Change password</Button>} />
        </div>
      </section>

      <Separator className="mb-8" />

      {/* Preferences (Phase 2) */}
      <section className="mb-8">
        <h2 className="mb-3 text-sm font-medium tracking-wide text-ink-4 uppercase">
          Preferences
        </h2>

        <div className="mb-3 flex items-center justify-between rounded-lg border border-border bg-parchment-2 px-4 py-3">
          <div>
            <p className="text-sm font-medium text-ink">Daily reminder time</p>
            <p className="text-xs text-ink-3">Get a nudge to write each day</p>
          </div>
          <div className="flex items-center gap-2">
            <Input type="time" disabled defaultValue="20:00" className="w-28" />
            <Badge variant="secondary">Coming soon</Badge>
          </div>
        </div>

        <div className="flex items-center justify-between rounded-lg border border-border bg-parchment-2 px-4 py-3">
          <div>
            <p className="text-sm font-medium text-ink">Weekly summary email</p>
            <p className="text-xs text-ink-3">A recap of your week, every Sunday</p>
          </div>
          <div className="flex items-center gap-2">
            <input type="checkbox" disabled className="size-4" />
            <Badge variant="secondary">Coming soon</Badge>
          </div>
        </div>
      </section>

      <Separator className="mb-8" />

      {/* Stats */}
      <section className="mb-8">
        <h2 className="mb-3 text-sm font-medium tracking-wide text-ink-4 uppercase">Stats</h2>
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-lg border border-border bg-parchment-2 px-4 py-3">
            <p className="text-2xl font-medium text-ink">{stats?.totalEntries ?? "—"}</p>
            <p className="text-xs text-ink-3">Total entries</p>
          </div>
          <div className="rounded-lg border border-border bg-parchment-2 px-4 py-3">
            <p className="text-2xl font-medium text-ink">{stats?.totalWords ?? "—"}</p>
            <p className="text-xs text-ink-3">Total words</p>
          </div>
          <div className="rounded-lg border border-border bg-parchment-2 px-4 py-3">
            <p className="text-2xl font-medium text-ink">{stats?.currentStreak ?? "—"}</p>
            <p className="text-xs text-ink-3">Current streak</p>
          </div>
          <div className="rounded-lg border border-border bg-parchment-2 px-4 py-3">
            <p className="text-2xl font-medium text-ink">{stats?.longestStreak ?? "—"}</p>
            <p className="text-xs text-ink-3">Longest streak</p>
          </div>
        </div>
        {memberSince && (
          <p className="mt-3 text-xs text-ink-3">Member since {formatLongDate(memberSince)}</p>
        )}
      </section>

      <Separator className="mb-8" />

      <Button variant="outline" onClick={handleSignOut}>
        Sign out
      </Button>
    </div>
  )
}
