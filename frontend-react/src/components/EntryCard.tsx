import { Link } from "react-router-dom"
import type { JournalEntryResponse } from "@/api/types"
import { MOOD_BORDER_CLASS, MOOD_EMOJI } from "@/constants/mood"
import { getEntryPreview, getEntryTitle, getWordCount } from "@/lib/entry"
import { formatLongDate } from "@/lib/date"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

export function EntryCard({ entry }: { entry: JournalEntryResponse }) {
  return (
    <Link
      to={`/entries/${entry.entryDate}`}
      className={cn(
        "block rounded-lg border-l-4 bg-parchment px-4 py-3 shadow-sm ring-1 ring-border transition-shadow hover:shadow-md",
        entry.mood ? MOOD_BORDER_CLASS[entry.mood] : "border-l-border"
      )}
    >
      <div className="mb-1 flex items-center justify-between gap-2">
        <span className="text-xs text-ink-3">{formatLongDate(entry.entryDate)}</span>
        {entry.mood && <span className="text-base leading-none">{MOOD_EMOJI[entry.mood]}</span>}
      </div>
      <h3 className="mb-1 truncate font-serif text-lg font-medium text-ink">
        {getEntryTitle(entry)}
      </h3>
      <p className="mb-2 line-clamp-2 font-serif text-sm text-ink-2 italic">
        {getEntryPreview(entry)}
      </p>
      {entry.tags.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-1">
          {entry.tags.map((tag) => (
            <Badge key={tag.id} variant="secondary" className="bg-accent-light text-brand-accent">
              {tag.name}
            </Badge>
          ))}
        </div>
      )}
      <p className="text-xs text-ink-4">{getWordCount(entry.content)} words</p>
    </Link>
  )
}
