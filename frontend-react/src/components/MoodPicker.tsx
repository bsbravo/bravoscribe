import type { Mood } from "@/api/types"
import { MOODS, MOOD_EMOJI, MOOD_LABEL } from "@/constants/mood"
import { cn } from "@/lib/utils"

interface MoodPickerProps {
  value: Mood | null
  onChange: (mood: Mood) => void
}

export function MoodPicker({ value, onChange }: MoodPickerProps) {
  return (
    <div className="flex items-center gap-1" role="radiogroup" aria-label="Mood">
      {MOODS.map((mood) => (
        <button
          key={mood}
          type="button"
          role="radio"
          aria-checked={value === mood}
          aria-label={MOOD_LABEL[mood]}
          title={MOOD_LABEL[mood]}
          onClick={() => onChange(mood)}
          className={cn(
            "flex size-9 items-center justify-center rounded-full text-lg transition-colors hover:bg-parchment-3",
            value === mood && "bg-gold-light ring-2 ring-gold"
          )}
        >
          {MOOD_EMOJI[mood]}
        </button>
      ))}
    </div>
  )
}
