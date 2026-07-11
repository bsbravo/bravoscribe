import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { isAxiosError } from "axios"
import { Loader2 } from "lucide-react"
import { exportEntries, getStats } from "@/api/journal"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  today,
  startOfWeekMonday,
  startOfMonth,
  startOfYear,
} from "@/lib/date"

type RangeOption = "today" | "week" | "month" | "year" | "all" | "custom"

const RANGE_LABELS: Record<RangeOption, string> = {
  today: "Today",
  week: "This week",
  month: "This month",
  year: "This year",
  all: "All time",
  custom: "Custom range",
}

export function ExportModal({ trigger }: { trigger: React.ReactNode }) {
  const [open, setOpen] = useState(false)
  const [range, setRange] = useState<RangeOption>("today")
  const [customFrom, setCustomFrom] = useState(today())
  const [customTo, setCustomTo] = useState(today())
  const [status, setStatus] = useState<"idle" | "loading" | "success" | "error">("idle")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const { data: stats } = useQuery({ queryKey: ["journal", "stats"], queryFn: getStats, enabled: open })

  function resolveRange(): { from: string; to: string } {
    const t = today()
    switch (range) {
      case "today":
        return { from: t, to: t }
      case "week":
        return { from: startOfWeekMonday(t), to: t }
      case "month":
        return { from: startOfMonth(t), to: t }
      case "year":
        return { from: startOfYear(t), to: t }
      case "all":
        return { from: stats?.firstEntryDate ?? t, to: t }
      case "custom":
        return { from: customFrom, to: customTo }
    }
  }

  async function handleDownload() {
    setStatus("loading")
    setErrorMessage(null)
    const { from, to } = resolveRange()
    try {
      const blob = await exportEntries(from, to)
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `bravoscribe-export-${from}-to-${to}.zip`
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
      setStatus("success")
      setTimeout(() => {
        setStatus("idle")
        setOpen(false)
      }, 1000)
    } catch (err) {
      setStatus("error")
      if (isAxiosError(err) && err.response?.status === 404) {
        setErrorMessage("No entries found for this date range.")
      } else if (isAxiosError(err) && err.response?.status === 400) {
        setErrorMessage("Date range cannot exceed 366 days.")
      } else {
        setErrorMessage("Export failed. Please try again.")
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={(v) => status !== "loading" && setOpen(v)}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Export entries</DialogTitle>
          <DialogDescription>
            Download your journal entries as a zip containing a single Markdown file.
          </DialogDescription>
        </DialogHeader>

        {status === "loading" && (
          <div className="flex items-center gap-2 py-4 text-sm text-ink-2">
            <Loader2 size={16} className="animate-spin" />
            Preparing your export…
          </div>
        )}

        {status === "success" && (
          <p className="py-4 text-sm text-teal">Download ready!</p>
        )}

        {(status === "idle" || status === "error") && (
          <div className="flex flex-col gap-3">
            <fieldset className="flex flex-col gap-2">
              {(Object.keys(RANGE_LABELS) as RangeOption[]).map((option) => (
                <label key={option} className="flex items-center gap-2 text-sm text-ink-2">
                  <input
                    type="radio"
                    name="export-range"
                    checked={range === option}
                    onChange={() => setRange(option)}
                  />
                  {RANGE_LABELS[option]}
                </label>
              ))}
            </fieldset>

            {range === "custom" && (
              <div className="flex items-center gap-2">
                <Input
                  type="date"
                  value={customFrom}
                  max={today()}
                  onChange={(e) => setCustomFrom(e.target.value)}
                />
                <span className="text-ink-3">to</span>
                <Input
                  type="date"
                  value={customTo}
                  max={today()}
                  onChange={(e) => setCustomTo(e.target.value)}
                />
              </div>
            )}

            {errorMessage && <p className="text-sm text-destructive">{errorMessage}</p>}
          </div>
        )}

        <DialogFooter>
          <Button onClick={handleDownload} disabled={status === "loading"}>
            Download
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
