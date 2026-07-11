import { getTodayPrompt } from "@/constants/dailyPrompts"

interface EmptyTodayProps {
  onFocusContent: () => void
}

export function EmptyToday({ onFocusContent }: EmptyTodayProps) {
  return (
    <button
      type="button"
      onClick={onFocusContent}
      className="w-full rounded-lg border border-dashed border-border bg-parchment-2 px-4 py-3 text-left text-sm text-ink-2 hover:bg-parchment-3"
    >
      <span className="font-medium">✨ Daily prompt</span>
      <span className="ml-2 font-serif italic">{getTodayPrompt()}</span>
    </button>
  )
}
