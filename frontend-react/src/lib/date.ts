// entryDate must always be derived from local time, never UTC (new Date().toISOString()
// would shift across midnight for users behind UTC). 'sv' (Swedish) locale formats as
// yyyy-MM-dd, which happens to match the API's date format exactly.
export function toLocalDateString(date: Date): string {
  return date.toLocaleDateString("sv")
}

export function today(): string {
  return toLocalDateString(new Date())
}

export function addDays(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split("-").map(Number)
  const date = new Date(y, m - 1, d)
  date.setDate(date.getDate() + days)
  return toLocalDateString(date)
}

export function isFutureDate(dateStr: string): boolean {
  return dateStr > today()
}

export function formatLongDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number)
  const date = new Date(y, m - 1, d)
  return date.toLocaleDateString("en-US", {
    weekday: "long",
    month: "long",
    day: "numeric",
    year: "numeric",
  })
}

export function formatShortDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number)
  const date = new Date(y, m - 1, d)
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" })
}

export function formatTime(dateTimeStr: string): string {
  return new Date(dateTimeStr).toLocaleTimeString("en-US", {
    hour: "numeric",
    minute: "2-digit",
  })
}

export function startOfWeekMonday(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number)
  const date = new Date(y, m - 1, d)
  const day = date.getDay()
  const diff = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + diff)
  return toLocalDateString(date)
}

export function startOfMonth(dateStr: string): string {
  const [y, m] = dateStr.split("-")
  return `${y}-${m}-01`
}

export function startOfYear(dateStr: string): string {
  const [y] = dateStr.split("-")
  return `${y}-01-01`
}

export function formatMonthYear(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number)
  const date = new Date(y, m - 1, d)
  return date.toLocaleDateString("en-US", { month: "long", year: "numeric" })
}
