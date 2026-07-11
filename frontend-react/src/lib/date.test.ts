import { describe, it, expect, beforeEach, afterEach, vi } from "vitest"
import {
  toLocalDateString,
  today,
  addDays,
  isFutureDate,
  formatLongDate,
  formatShortDate,
  formatTime,
  startOfWeekMonday,
  startOfMonth,
  startOfYear,
  formatMonthYear,
} from "./date"

describe("toLocalDateString", () => {
  it("formats as yyyy-MM-dd", () => {
    expect(toLocalDateString(new Date(2026, 6, 11))).toBe("2026-07-11")
  })

  it("zero-pads single-digit month and day", () => {
    expect(toLocalDateString(new Date(2026, 0, 5))).toBe("2026-01-05")
  })
})

describe("today", () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it("reflects local system time, not UTC — a late-evening date near midnight UTC should not roll forward", () => {
    // 11pm local on June 6 — if this ever used toISOString() (UTC) instead of
    // local formatting, a user west of UTC would see June 7 here. Flaw G.
    vi.setSystemTime(new Date(2026, 5, 6, 23, 0, 0))
    expect(today()).toBe("2026-06-06")
  })
})

describe("addDays", () => {
  it("adds positive days within a month", () => {
    expect(addDays("2026-07-11", 5)).toBe("2026-07-16")
  })

  it("subtracts days", () => {
    expect(addDays("2026-07-11", -5)).toBe("2026-07-06")
  })

  it("rolls over a month boundary", () => {
    expect(addDays("2026-01-31", 1)).toBe("2026-02-01")
  })

  it("rolls backward over a month boundary into a shorter month", () => {
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28")
  })

  it("handles a leap-year February correctly", () => {
    expect(addDays("2024-02-28", 1)).toBe("2024-02-29")
    expect(addDays("2024-02-29", 1)).toBe("2024-03-01")
  })

  it("rolls over a year boundary", () => {
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01")
  })
})

describe("isFutureDate", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 11))
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it("is true for a date after today", () => {
    expect(isFutureDate("2026-07-12")).toBe(true)
  })

  it("is false for today", () => {
    expect(isFutureDate("2026-07-11")).toBe(false)
  })

  it("is false for a past date", () => {
    expect(isFutureDate("2026-07-10")).toBe(false)
  })
})

describe("formatLongDate", () => {
  it("formats with weekday, month, day, year", () => {
    expect(formatLongDate("2026-07-11")).toBe("Saturday, July 11, 2026")
  })
})

describe("formatShortDate", () => {
  it("formats as abbreviated month + day", () => {
    expect(formatShortDate("2026-07-11")).toBe("Jul 11")
  })
})

describe("formatTime", () => {
  it("formats a datetime string as a local time", () => {
    // Use a local (no 'Z') ISO string so this doesn't depend on the CI runner's timezone.
    const result = formatTime("2026-07-11T09:41:00")
    expect(result).toMatch(/9:41\s?AM/i)
  })
})

describe("startOfWeekMonday", () => {
  it("returns the same date when given a Monday", () => {
    // 2026-07-13 is a Monday
    expect(startOfWeekMonday("2026-07-13")).toBe("2026-07-13")
  })

  it("goes back to Monday when given a mid-week date", () => {
    // 2026-07-15 is a Wednesday
    expect(startOfWeekMonday("2026-07-15")).toBe("2026-07-13")
  })

  it("goes back to the previous Monday when given a Sunday", () => {
    // 2026-07-19 is a Sunday — should resolve to Monday 2026-07-13, not 2026-07-19
    expect(startOfWeekMonday("2026-07-19")).toBe("2026-07-13")
  })
})

describe("startOfMonth", () => {
  it("returns the 1st of the given month", () => {
    expect(startOfMonth("2026-07-11")).toBe("2026-07-01")
  })
})

describe("startOfYear", () => {
  it("returns January 1st of the given year", () => {
    expect(startOfYear("2026-07-11")).toBe("2026-01-01")
  })
})

describe("formatMonthYear", () => {
  it("formats as full month name + year", () => {
    expect(formatMonthYear("2026-07-11")).toBe("July 2026")
  })
})
