import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Plus } from "lucide-react"
import { createTag, listTags } from "@/api/journal"
import type { TagResponse } from "@/api/types"
import { TagChip } from "@/components/TagChip"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Input } from "@/components/ui/input"

const MAX_TAGS = 10
const MAX_TAG_NAME = 50

interface TagInputProps {
  selectedTags: TagResponse[]
  onChange: (tags: TagResponse[]) => void
}

export function TagInput({ selectedTags, onChange }: TagInputProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState("")
  const queryClient = useQueryClient()

  const { data: allTags = [] } = useQuery({ queryKey: ["tags"], queryFn: listTags })

  const createTagMutation = useMutation({
    mutationFn: createTag,
    onSuccess: (tag) => {
      queryClient.invalidateQueries({ queryKey: ["tags"] })
      addTag(tag)
    },
  })

  const atLimit = selectedTags.length >= MAX_TAGS

  function addTag(tag: TagResponse) {
    if (selectedTags.some((t) => t.id === tag.id)) return
    onChange([...selectedTags, tag])
    setQuery("")
    setOpen(false)
  }

  function removeTag(id: string) {
    onChange(selectedTags.filter((t) => t.id !== id))
  }

  function handleSubmitQuery() {
    const trimmed = query.trim()
    if (!trimmed) return
    const existing = allTags.find((t) => t.name.toLowerCase() === trimmed.toLowerCase())
    if (existing) {
      addTag(existing)
    } else {
      createTagMutation.mutate({ name: trimmed })
    }
  }

  const suggestions = allTags.filter(
    (t) =>
      !selectedTags.some((s) => s.id === t.id) &&
      t.name.toLowerCase().includes(query.trim().toLowerCase())
  )

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {selectedTags.map((tag) => (
        <TagChip key={tag.id} name={tag.name} onRemove={() => removeTag(tag.id)} />
      ))}

      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <button
            type="button"
            disabled={atLimit}
            title={atLimit ? "Maximum 10 tags" : undefined}
            className="flex items-center gap-1 rounded-full border border-dashed border-border px-2 py-1 text-xs font-medium text-ink-3 hover:bg-parchment-3 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Plus size={12} />
            add tag
          </button>
        </PopoverTrigger>
        <PopoverContent className="w-56 p-2" align="start">
          <Input
            autoFocus
            placeholder="Tag name"
            value={query}
            maxLength={MAX_TAG_NAME}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault()
                handleSubmitQuery()
              }
            }}
          />
          {query.length >= 40 && (
            <p className="mt-1 text-right text-xs text-ink-4">
              {query.length} / {MAX_TAG_NAME}
            </p>
          )}
          {suggestions.length > 0 && (
            <ul className="mt-2 flex max-h-40 flex-col gap-0.5 overflow-y-auto">
              {suggestions.map((tag) => (
                <li key={tag.id}>
                  <button
                    type="button"
                    onClick={() => addTag(tag)}
                    className="w-full rounded px-2 py-1 text-left text-sm hover:bg-parchment-3"
                  >
                    {tag.name}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </PopoverContent>
      </Popover>
    </div>
  )
}
