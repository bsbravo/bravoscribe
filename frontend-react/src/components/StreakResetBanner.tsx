import { useState } from "react"
import { X } from "lucide-react"
import { useStreak } from "@/hooks/useStreak"
import { today } from "@/lib/date"

function storageKey() {
  return `bravoscribe-streak-banner-dismissed:${today()}`
}

export function StreakResetBanner() {
  const { currentStreak, longestStreak, isLoading } = useStreak()
  const [dismissed, setDismissed] = useState(() => localStorage.getItem(storageKey()) === "1")

  if (isLoading || dismissed || currentStreak !== 0 || longestStreak <= 0) return null

  function dismiss() {
    localStorage.setItem(storageKey(), "1")
    setDismissed(true)
  }

  return (
    <div className="mb-4 flex items-center justify-between rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
      <span>
        Your {longestStreak}-day streak ended. Start a new one today!
      </span>
      <button onClick={dismiss} aria-label="Dismiss" className="text-amber-800/70 hover:text-amber-800">
        <X size={14} />
      </button>
    </div>
  )
}
