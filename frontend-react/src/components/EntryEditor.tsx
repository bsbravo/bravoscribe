import { useCallback, useEffect, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import { useQueryClient } from "@tanstack/react-query"
import { ChevronLeft, ChevronRight, Download } from "lucide-react"
import { createEntry, updateEntry } from "@/api/journal"
import type { JournalEntryResponse, Mood, TagResponse } from "@/api/types"
import { useEntryByDate } from "@/hooks/useEntryByDate"
import { useAutosave } from "@/hooks/useAutosave"
import { useEditorShortcuts } from "@/hooks/useEditorShortcuts"
import { addDays, formatLongDate, formatShortDate, formatTime, isFutureDate, today } from "@/lib/date"
import { getWordCount } from "@/lib/entry"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Skeleton } from "@/components/ui/skeleton"
import { MoodPicker } from "@/components/MoodPicker"
import { TagInput } from "@/components/TagInput"
import { AutosaveStatus, type SaveStatus } from "@/components/AutosaveStatus"
import { EmptyToday } from "@/components/EmptyToday"
import { NoDayCard } from "@/components/NoDayCard"
import { StreakBar } from "@/components/StreakBar"
import { DeleteEntryButton } from "@/components/DeleteEntryButton"
import { ExportModal } from "@/components/ExportModal"
import { StreakResetBanner } from "@/components/StreakResetBanner"
import { UnsavedChangesGuard } from "@/components/UnsavedChangesGuard"
import { cn } from "@/lib/utils"

interface EditorSnapshot {
  title: string
  content: string
  mood: Mood | null
  tagIds: string[]
}

function snapshotOf(entry: JournalEntryResponse | null): EditorSnapshot {
  if (!entry) return { title: "", content: "", mood: null, tagIds: [] }
  return {
    title: entry.title ?? "",
    content: entry.content,
    mood: entry.mood,
    tagIds: entry.tags.map((t) => t.id).sort(),
  }
}

function snapshotsEqual(a: EditorSnapshot, b: EditorSnapshot): boolean {
  return (
    a.title === b.title &&
    a.content === b.content &&
    a.mood === b.mood &&
    a.tagIds.length === b.tagIds.length &&
    a.tagIds.every((id, i) => id === b.tagIds[i])
  )
}

export function EntryEditor({ date }: { date: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: entry, isLoading } = useEntryByDate(date)

  const [entryId, setEntryId] = useState<string | null>(null)
  const [title, setTitle] = useState("")
  const [content, setContent] = useState("")
  const [mood, setMood] = useState<Mood | null>(null)
  const [tags, setTags] = useState<TagResponse[]>([])
  const [lastSaved, setLastSaved] = useState<EditorSnapshot>(snapshotOf(null))
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("not-saved")
  const [deletedOptimistic, setDeletedOptimistic] = useState(false)

  // Hydrate local editor state from the server once per date. Guarded so that our
  // own setQueryData() calls after a save (which change `entry`'s reference) don't
  // re-run this and clobber keystrokes typed during the save's network round-trip.
  const hydratedDateRef = useRef<string | null>(null)
  useEffect(() => {
    if (entry === undefined) return // still loading
    if (hydratedDateRef.current === date) return
    hydratedDateRef.current = date
    setEntryId(entry?.id ?? null)
    setTitle(entry?.title ?? "")
    setContent(entry?.content ?? "")
    setMood(entry?.mood ?? null)
    setTags(entry?.tags ?? [])
    setLastSaved(snapshotOf(entry))
    setSaveStatus(entry ? "saved" : "not-saved")
    setDeletedOptimistic(false)
  }, [entry, date])

  const current: EditorSnapshot = {
    title,
    content,
    mood,
    tagIds: tags.map((t) => t.id).sort(),
  }
  const isDirty = !snapshotsEqual(current, lastSaved)
  const hasUnsavedChanges = !!entryId && isDirty
  const isSaveEnabled = content.trim().length > 0 && (isDirty || !entryId)

  const performSave = useCallback(async () => {
    setSaveStatus("saving")
    const payload = {
      title: title.trim() || undefined,
      content,
      mood: mood ?? undefined,
      tagIds: tags.map((t) => t.id),
    }
    try {
      if (!entryId) {
        const created = await createEntry({ entryDate: date, ...payload })
        setEntryId(created.id)
        setLastSaved(snapshotOf(created))
        setSaveStatus("saved")
        queryClient.setQueryData(["entry", "date", date], created)
        queryClient.invalidateQueries({ queryKey: ["entries"] })
      } else {
        const updated = await updateEntry(entryId, payload)
        setLastSaved(snapshotOf(updated))
        setSaveStatus("saved")
        queryClient.setQueryData(["entry", "date", date], updated)
      }
    } catch {
      setSaveStatus("error")
    }
  }, [content, date, entryId, mood, queryClient, tags, title])

  useAutosave({ enabled: !!entryId, isDirty, onSave: performSave })
  useEditorShortcuts(isSaveEnabled, performSave)

  useEffect(() => {
    if (isDirty && saveStatus === "saved") setSaveStatus("unsaved")
  }, [isDirty, saveStatus])

  const isToday = date === today()
  const nextDate = addDays(date, 1)
  const prevDate = addDays(date, -1)

  function goTo(d: string) {
    navigate(d === today() ? "/" : `/entries/${d}`)
  }

  if (isLoading || entry === undefined) {
    return (
      <div className="mx-auto max-w-2xl px-6 py-10">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="mt-4 h-10 w-full" />
        <Skeleton className="mt-3 h-40 w-full" />
        <Skeleton className="mt-3 h-9 w-48" />
      </div>
    )
  }

  if (deletedOptimistic) {
    return (
      <div className="mx-auto max-w-2xl px-6 py-10 text-sm text-ink-3">
        Entry deleted.{" "}
        <button className="text-brand-accent hover:underline" onClick={() => navigate("/entries")}>
          Back to all entries
        </button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl px-6 py-10">
      <UnsavedChangesGuard hasUnsavedChanges={hasUnsavedChanges} onStayAndSave={performSave} />

      {isToday && <StreakResetBanner />}

      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3 text-sm text-ink-3">
          <button onClick={() => goTo(prevDate)} className="hover:text-ink" aria-label="Previous day">
            <ChevronLeft size={16} className="inline" /> {formatShortDate(prevDate)}
          </button>
          <span className="font-serif text-lg text-ink">{formatLongDate(date)}</span>
          <button
            onClick={() => goTo(nextDate)}
            disabled={isFutureDate(nextDate) || nextDate === date}
            className="hover:text-ink disabled:pointer-events-none disabled:opacity-30"
            aria-label="Next day"
          >
            {formatShortDate(nextDate)} <ChevronRight size={16} className="inline" />
          </button>
        </div>
        <div className="flex items-center gap-1">
          {isToday && (
            <ExportModal
              trigger={
                <Button variant="ghost" size="icon" aria-label="Export entries">
                  <Download size={16} />
                </Button>
              }
            />
          )}
          {entryId && (
            <DeleteEntryButton entryId={entryId} onDeleted={() => setDeletedOptimistic(true)} />
          )}
        </div>
      </div>

      <Input
        placeholder="Title (optional)"
        value={title}
        maxLength={255}
        onChange={(e) => setTitle(e.target.value)}
        className="mb-1 border-none px-0 font-serif text-2xl font-medium text-ink shadow-none focus-visible:ring-0"
      />
      {title.length >= 200 && (
        <p className="mb-2 text-right text-xs text-ink-4">{title.length} / 255</p>
      )}

      <div className="mb-3">
        <TagInput selectedTags={tags} onChange={setTags} />
      </div>

      <Textarea
        id="entry-content"
        placeholder="Start writing…"
        value={content}
        maxLength={10000}
        onChange={(e) => setContent(e.target.value)}
        className="min-h-52 resize-none border-none px-0 font-serif text-base italic text-ink shadow-none focus-visible:ring-0"
      />
      <p
        className={cn(
          "mb-4 text-right text-xs text-ink-4",
          content.length >= 9500 && "text-destructive"
        )}
      >
        {content.length.toLocaleString()} / 10,000
      </p>

      {!entryId && content.trim() === "" && isToday && (
        <div className="mb-4">
          <EmptyToday
            onFocusContent={() => document.getElementById("entry-content")?.focus()}
          />
        </div>
      )}
      {!entryId && content.trim() === "" && !isToday && (
        <div className="mb-4">
          <NoDayCard onWriteNow={() => document.getElementById("entry-content")?.focus()} />
        </div>
      )}

      <div className="mb-6 flex items-center justify-between">
        <MoodPicker value={mood} onChange={setMood} />
        <div className="flex items-center gap-3">
          <AutosaveStatus status={saveStatus} />
          <Button onClick={performSave} disabled={!isSaveEnabled || saveStatus === "saving"}>
            Save
          </Button>
        </div>
      </div>

      <p className="mb-6 text-xs text-ink-4">
        {getWordCount(content)} words
        {entry?.updatedAt ? ` · saved at ${formatTime(entry.updatedAt)}` : ""}
      </p>

      {isToday && <StreakBar />}
    </div>
  )
}
