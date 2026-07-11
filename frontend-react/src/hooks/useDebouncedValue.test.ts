import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { renderHook } from "@testing-library/react"
import { act } from "react"
import { useDebouncedValue } from "./useDebouncedValue"

describe("useDebouncedValue", () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it("returns the initial value immediately", () => {
    const { result } = renderHook(() => useDebouncedValue("first", 500))
    expect(result.current).toBe("first")
  })

  it("does not update before the delay elapses", () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 500), {
      initialProps: { value: "first" },
    })
    rerender({ value: "second" })
    act(() => vi.advanceTimersByTime(499))
    expect(result.current).toBe("first")
  })

  it("updates to the latest value once the delay elapses", () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 500), {
      initialProps: { value: "first" },
    })
    rerender({ value: "second" })
    act(() => vi.advanceTimersByTime(500))
    expect(result.current).toBe("second")
  })

  it("only reflects the final value from a rapid burst of changes (debounce, not throttle)", () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 500), {
      initialProps: { value: "a" },
    })
    rerender({ value: "ab" })
    act(() => vi.advanceTimersByTime(100))
    rerender({ value: "abc" })
    act(() => vi.advanceTimersByTime(100))
    rerender({ value: "abcd" })

    // Not enough time has passed since the last keystroke for any update yet.
    act(() => vi.advanceTimersByTime(499))
    expect(result.current).toBe("a")

    act(() => vi.advanceTimersByTime(1))
    expect(result.current).toBe("abcd")
  })
})
