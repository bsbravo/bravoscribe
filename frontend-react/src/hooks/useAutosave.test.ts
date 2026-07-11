import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { renderHook } from "@testing-library/react"
import { act } from "react"
import { useAutosave } from "./useAutosave"

describe("useAutosave", () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it("does not fire immediately on mount", () => {
    const onSave = vi.fn()
    renderHook(() => useAutosave({ enabled: true, isDirty: true, onSave, delayMs: 30_000 }))
    expect(onSave).not.toHaveBeenCalled()
  })

  it("fires after delayMs when enabled and dirty", () => {
    const onSave = vi.fn()
    renderHook(() => useAutosave({ enabled: true, isDirty: true, onSave, delayMs: 30_000 }))
    act(() => vi.advanceTimersByTime(30_000))
    expect(onSave).toHaveBeenCalledTimes(1)
  })

  it("never fires when disabled (no entry saved yet), no matter how long", () => {
    const onSave = vi.fn()
    renderHook(() => useAutosave({ enabled: false, isDirty: true, onSave, delayMs: 30_000 }))
    act(() => vi.advanceTimersByTime(120_000))
    expect(onSave).not.toHaveBeenCalled()
  })

  it("never fires when there are no unsaved changes", () => {
    const onSave = vi.fn()
    renderHook(() => useAutosave({ enabled: true, isDirty: false, onSave, delayMs: 30_000 }))
    act(() => vi.advanceTimersByTime(60_000))
    expect(onSave).not.toHaveBeenCalled()
  })

  it("debounces — a change before the delay elapses restarts the timer instead of stacking a second call", () => {
    const onSave = vi.fn()
    const { rerender } = renderHook(
      ({ onSave }) => useAutosave({ enabled: true, isDirty: true, onSave, delayMs: 30_000 }),
      { initialProps: { onSave } }
    )

    act(() => vi.advanceTimersByTime(20_000)) // typing continues before the 30s mark
    const onSave2 = vi.fn()
    rerender({ onSave: onSave2 })

    act(() => vi.advanceTimersByTime(20_000)) // 40s total, but only 20s since the last change
    expect(onSave).not.toHaveBeenCalled()
    expect(onSave2).not.toHaveBeenCalled()

    act(() => vi.advanceTimersByTime(10_000)) // now 30s since the last change
    expect(onSave2).toHaveBeenCalledTimes(1)
    expect(onSave).not.toHaveBeenCalled()
  })

  it("clears the pending timer on unmount", () => {
    const onSave = vi.fn()
    const { unmount } = renderHook(() =>
      useAutosave({ enabled: true, isDirty: true, onSave, delayMs: 30_000 })
    )
    unmount()
    act(() => vi.advanceTimersByTime(30_000))
    expect(onSave).not.toHaveBeenCalled()
  })
})
