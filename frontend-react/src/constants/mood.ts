import type { Mood } from "@/api/types"

export const MOODS: Mood[] = ["GREAT", "GOOD", "NEUTRAL", "BAD", "TERRIBLE"]

export const MOOD_EMOJI: Record<Mood, string> = {
  GREAT: "😄",
  GOOD: "🙂",
  NEUTRAL: "😐",
  BAD: "😔",
  TERRIBLE: "😞",
}

export const MOOD_LABEL: Record<Mood, string> = {
  GREAT: "Great",
  GOOD: "Good",
  NEUTRAL: "Neutral",
  BAD: "Bad",
  TERRIBLE: "Terrible",
}

// Left-border color on entry cards
export const MOOD_BORDER_CLASS: Record<Mood, string> = {
  GREAT: "border-l-mood-great",
  GOOD: "border-l-mood-good",
  NEUTRAL: "border-l-mood-neutral",
  BAD: "border-l-mood-bad",
  TERRIBLE: "border-l-mood-terrible",
}
