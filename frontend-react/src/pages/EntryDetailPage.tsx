import { Link, Navigate, useNavigate, useParams } from "react-router-dom"
import ReactMarkdown from "react-markdown"
import { ArrowLeft, Pencil } from "lucide-react"
import { useEntryByDate } from "@/hooks/useEntryByDate"
import { useDocumentTitle } from "@/hooks/useDocumentTitle"
import { formatLongDate, formatTime, today } from "@/lib/date"
import { getEntryTitle, getWordCount } from "@/lib/entry"
import { MOOD_EMOJI, MOOD_LABEL } from "@/constants/mood"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { DeleteEntryButton } from "@/components/DeleteEntryButton"

export function EntryDetailPage() {
  const { date } = useParams<{ date: string }>()
  const navigate = useNavigate()
  useDocumentTitle(date ? `${date} · Bravoscribe` : "Bravoscribe")

  const { data: entry, isLoading } = useEntryByDate(date ?? "")

  if (!date) return <Navigate to="/entries" replace />
  if (date === today()) return <Navigate to="/" replace />

  if (isLoading || entry === undefined) {
    return (
      <div className="mx-auto max-w-2xl px-6 py-10">
        <Skeleton className="h-6 w-32" />
        <Skeleton className="mt-6 h-8 w-64" />
        <Skeleton className="mt-4 h-32 w-full" />
      </div>
    )
  }

  if (entry === null) {
    return (
      <div className="mx-auto max-w-2xl px-6 py-10">
        <Link to="/entries" className="mb-6 flex items-center gap-1 text-sm text-ink-3 hover:text-ink">
          <ArrowLeft size={16} />
          All entries
        </Link>
        <p className="text-sm text-ink-2">
          No entry for {formatLongDate(date)}.{" "}
          <Link to={`/entries/${date}/edit`} className="font-medium text-brand-accent hover:underline">
            Write an entry for this day
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl px-6 py-10">
      <div className="mb-6 flex items-center justify-between">
        <Link to="/entries" className="flex items-center gap-1 text-sm text-ink-3 hover:text-ink">
          <ArrowLeft size={16} />
          All entries
        </Link>
        <div className="flex items-center gap-1">
          <Button variant="outline" size="sm" asChild>
            <Link to={`/entries/${date}/edit`}>
              <Pencil size={14} />
              Edit entry
            </Link>
          </Button>
          <DeleteEntryButton entryId={entry.id} onDeleted={() => navigate("/entries")} />
        </div>
      </div>

      <p className="mb-2 text-sm text-ink-3">{formatLongDate(date)}</p>

      {entry.mood && (
        <p className="mb-3 flex items-center gap-1.5 text-sm text-ink-2">
          <span className="text-lg leading-none">{MOOD_EMOJI[entry.mood]}</span>
          {MOOD_LABEL[entry.mood]}
        </p>
      )}

      {entry.tags.length > 0 && (
        <div className="mb-4 flex flex-wrap gap-1.5">
          {entry.tags.map((tag) => (
            <Badge key={tag.id} variant="secondary" className="bg-accent-light text-brand-accent">
              {tag.name}
            </Badge>
          ))}
        </div>
      )}

      {entry.title && (
        <h2 className="mb-3 font-serif text-2xl font-medium text-ink">{getEntryTitle(entry)}</h2>
      )}

      <div className="prose prose-sm mb-6 max-w-none font-serif text-ink italic prose-p:italic">
        <ReactMarkdown>{entry.content}</ReactMarkdown>
      </div>

      <p className="text-xs text-ink-4">
        {getWordCount(entry.content)} words · saved at {formatTime(entry.updatedAt)}
      </p>
    </div>
  )
}
