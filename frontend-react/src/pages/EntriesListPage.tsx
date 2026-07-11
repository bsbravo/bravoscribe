import { useState } from "react"
import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { CalendarIcon, ChevronLeft, ChevronRight, Download, Search, X } from "lucide-react"
import { listEntries } from "@/api/journal"
import { useDebouncedValue } from "@/hooks/useDebouncedValue"
import { useDocumentTitle } from "@/hooks/useDocumentTitle"
import { EntryCard } from "@/components/EntryCard"
import { EntryCalendar } from "@/components/EntryCalendar"
import { ExportModal } from "@/components/ExportModal"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { formatLongDate, toLocalDateString } from "@/lib/date"

const PAGE_SIZE = 20

export function EntriesListPage() {
  useDocumentTitle("All entries · Bravoscribe")

  const [page, setPage] = useState(0)
  const [query, setQuery] = useState("")
  const [selectedDate, setSelectedDate] = useState<Date | undefined>(undefined)
  const debouncedQuery = useDebouncedValue(query, 500)
  const dateStr = selectedDate ? toLocalDateString(selectedDate) : undefined

  const { data, isLoading } = useQuery({
    queryKey: ["entries", "list", page, debouncedQuery, dateStr],
    queryFn: () =>
      listEntries({
        page,
        size: PAGE_SIZE,
        q: debouncedQuery || undefined,
        from: dateStr,
        to: dateStr,
      }),
  })

  const { data: unfiltered } = useQuery({
    queryKey: ["entries", "list", "unfiltered-count"],
    queryFn: () => listEntries({ page: 0, size: 1 }),
  })

  function clearFilters() {
    setQuery("")
    setSelectedDate(undefined)
    setPage(0)
  }

  function heading() {
    if (debouncedQuery && dateStr) return `Results for '${debouncedQuery}' on ${formatLongDate(dateStr)}`
    if (debouncedQuery) return `Results for '${debouncedQuery}'`
    if (dateStr) return formatLongDate(dateStr)
    return "All entries"
  }

  const isAccountEmpty = unfiltered?.totalElements === 0
  const hasFilters = !!debouncedQuery || !!dateStr
  const noResults = !isLoading && data?.content.length === 0

  return (
    <div className="mx-auto max-w-3xl px-6 py-10">
      <div className="mb-6 flex items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-medium text-ink">{heading()}</h1>
        <ExportModal
          trigger={
            <Button variant="outline" size="sm">
              <Download size={14} />
              Export
            </Button>
          }
        />
      </div>

      {!isAccountEmpty && (
        <div className="mb-6 flex items-center gap-2">
          <div className="relative flex-1">
            <Search size={16} className="absolute top-1/2 left-3 -translate-y-1/2 text-ink-4" />
            <Input
              placeholder="Search entries…"
              value={query}
              maxLength={200}
              onChange={(e) => {
                setQuery(e.target.value)
                setPage(0)
              }}
              className="pl-9"
            />
          </div>
          <Popover>
            <PopoverTrigger asChild>
              <Button variant="outline" size="icon" aria-label="Filter by date">
                <CalendarIcon size={16} />
              </Button>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-auto p-0">
              <EntryCalendar
                selected={selectedDate}
                onSelect={(d) => {
                  setSelectedDate(d)
                  setPage(0)
                }}
              />
            </PopoverContent>
          </Popover>
          {hasFilters && (
            <Button variant="ghost" size="icon" onClick={clearFilters} aria-label="Clear filters">
              <X size={16} />
            </Button>
          )}
        </div>
      )}

      {isAccountEmpty && (
        <div className="rounded-lg border border-dashed border-border bg-parchment-2 px-6 py-16 text-center">
          <p className="mb-4 text-sm text-ink-2">No entries yet. Go write your first one!</p>
          <Button asChild>
            <Link to="/">Start writing</Link>
          </Button>
        </div>
      )}

      {!isAccountEmpty && noResults && (
        <div className="rounded-lg border border-dashed border-border bg-parchment-2 px-6 py-16 text-center">
          <p className="mb-4 text-sm text-ink-2">No entries found.</p>
          <Button variant="outline" onClick={clearFilters}>
            Clear filters
          </Button>
        </div>
      )}

      {!isAccountEmpty && !noResults && (
        <div className="flex flex-col gap-3">
          {data?.content.map((entry) => (
            <EntryCard key={entry.id} entry={entry} />
          ))}
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <Button
            variant="outline"
            size="sm"
            disabled={data.first}
            onClick={() => setPage((p) => p - 1)}
          >
            <ChevronLeft size={14} />
            Previous
          </Button>
          <span className="text-sm text-ink-3">
            Page {data.number + 1} of {data.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={data.last}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
            <ChevronRight size={14} />
          </Button>
        </div>
      )}
    </div>
  )
}
