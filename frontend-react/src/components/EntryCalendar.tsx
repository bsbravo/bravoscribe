import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { getEntryDates } from "@/api/journal"
import { Calendar } from "@/components/ui/calendar"
import { toLocalDateString } from "@/lib/date"

interface EntryCalendarProps {
  selected?: Date
  onSelect: (date: Date | undefined) => void
}

export function EntryCalendar({ selected, onSelect }: EntryCalendarProps) {
  const [month, setMonth] = useState(selected ?? new Date())

  const from = toLocalDateString(new Date(month.getFullYear(), month.getMonth(), 1))
  const to = toLocalDateString(new Date(month.getFullYear(), month.getMonth() + 1, 0))

  const { data: dates = [] } = useQuery({
    queryKey: ["entries", "dates", from, to],
    queryFn: () => getEntryDates(from, to),
  })
  const dateSet = new Set(dates)

  return (
    <Calendar
      mode="single"
      selected={selected}
      onSelect={onSelect}
      month={month}
      onMonthChange={setMonth}
      modifiers={{ hasEntry: (date) => dateSet.has(toLocalDateString(date)) }}
      modifiersClassNames={{
        hasEntry:
          "after:absolute after:bottom-1 after:left-1/2 after:size-1 after:-translate-x-1/2 after:rounded-full after:bg-brand-accent after:content-['']",
      }}
    />
  )
}
