interface NoDayCardProps {
  onWriteNow: () => void
}

export function NoDayCard({ onWriteNow }: NoDayCardProps) {
  return (
    <button
      type="button"
      onClick={onWriteNow}
      className="w-full rounded-lg border border-dashed border-border bg-parchment-2 px-4 py-8 text-center text-sm text-ink-3 hover:bg-parchment-3"
    >
      No entry for this day.
      <span className="ml-1 font-medium text-brand-accent">Write an entry for this day</span>
    </button>
  )
}
