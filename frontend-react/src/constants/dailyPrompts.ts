export const DAILY_PROMPTS = [
  "What made you smile today?",
  "What's one thing you're grateful for?",
  "Describe a challenge you faced and how you handled it.",
  "What's something you learned recently?",
  "What do you wish you had more time for?",
  "Describe your ideal day.",
  "What's something you're looking forward to?",
  "What would you tell your past self?",
  "What's something you accomplished today, no matter how small?",
  "What's been on your mind lately?",
  "Who inspired you today and why?",
  "What's one habit you'd like to build?",
  "Describe a moment of peace you experienced recently.",
  "What's something you want to improve about yourself?",
  "What made today different from yesterday?",
] as const

// Rotates by day of year — same prompt all day, changes at midnight (local time)
export function getTodayPrompt(): string {
  const now = new Date()
  const dayOfYear = Math.floor(
    (now.getTime() - new Date(now.getFullYear(), 0, 0).getTime()) / 86_400_000
  )
  return DAILY_PROMPTS[dayOfYear % DAILY_PROMPTS.length]
}
