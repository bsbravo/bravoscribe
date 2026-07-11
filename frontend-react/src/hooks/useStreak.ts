import { useQuery } from "@tanstack/react-query"
import { getEntryDates, getStats } from "@/api/journal"
import { addDays, today } from "@/lib/date"

export interface StreakDay {
  date: string
  hasEntry: boolean
  isToday: boolean
}

// Stats (current/longest streak counts) come from the backend's already-cached
// endpoint. The 7-day visual bar is derived client-side from the lightweight
// dates endpoint — no dedicated streak endpoint needed for that part.
export function useStreak() {
  const statsQuery = useQuery({
    queryKey: ["journal", "stats"],
    queryFn: getStats,
  })

  const todayStr = today()
  const from = addDays(todayStr, -6)

  const datesQuery = useQuery({
    queryKey: ["entries", "dates", from, todayStr],
    queryFn: () => getEntryDates(from, todayStr),
  })

  const dateSet = new Set(datesQuery.data ?? [])
  const sevenDay: StreakDay[] = Array.from({ length: 7 }, (_, i) => {
    const date = addDays(todayStr, i - 6)
    return { date, hasEntry: dateSet.has(date), isToday: date === todayStr }
  })

  return {
    currentStreak: statsQuery.data?.currentStreak ?? 0,
    longestStreak: statsQuery.data?.longestStreak ?? 0,
    sevenDay,
    isLoading: statsQuery.isLoading || datesQuery.isLoading,
  }
}
