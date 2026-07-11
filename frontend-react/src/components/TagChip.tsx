import { X } from "lucide-react"
import { Badge } from "@/components/ui/badge"

interface TagChipProps {
  name: string
  onRemove: () => void
}

export function TagChip({ name, onRemove }: TagChipProps) {
  return (
    <Badge
      variant="secondary"
      className="group/tag gap-1 bg-accent-light text-brand-accent hover:bg-accent-light"
    >
      {name}
      <button
        type="button"
        onClick={onRemove}
        aria-label={`Remove tag ${name}`}
        className="ml-0.5 hidden rounded-full group-hover/tag:inline-flex hover:text-destructive"
      >
        <X size={12} />
      </button>
    </Badge>
  )
}
