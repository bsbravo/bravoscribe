import { Flame } from "lucide-react"
import { useStreak } from "@/hooks/useStreak"
import { cn } from "@/lib/utils"

export function StreakBar() {
  const { currentStreak, sevenDay, isLoading } = useStreak()

  if (isLoading) return null

  return (
    <div className="flex items-center justify-between rounded-lg bg-parchment-3 px-4 py-3">
      <div className="flex items-center gap-1.5 text-sm font-medium text-ink-2">
        <Flame size={16} className="text-gold" />
        {currentStreak}-day streak
      </div>
      <div className="flex items-center gap-1.5">
        {sevenDay.map((day) => (
          <span
            key={day.date}
            title={day.date}
            className={cn(
              "size-2.5 rounded-full",
              day.isToday
                ? "bg-brand-accent"
                : day.hasEntry
                  ? "bg-teal"
                  : "bg-parchment border border-border"
            )}
          />
        ))}
      </div>
    </div>
  )
}
