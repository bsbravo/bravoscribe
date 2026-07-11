import { useRef, useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import { Trash2 } from "lucide-react"
import { deleteEntry } from "@/api/journal"
import { Button } from "@/components/ui/button"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

interface DeleteEntryButtonProps {
  entryId: string
  onDeleted: () => void
}

export function DeleteEntryButton({ entryId, onDeleted }: DeleteEntryButtonProps) {
  const [confirmOpen, setConfirmOpen] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const queryClient = useQueryClient()

  function handleConfirm() {
    setConfirmOpen(false)
    onDeleted() // optimistically remove from view

    timerRef.current = setTimeout(async () => {
      try {
        await deleteEntry(entryId)
        queryClient.invalidateQueries({ queryKey: ["entries"] })
      } catch {
        toast.error("Failed to delete entry.")
      }
    }, 5000)

    toast("Entry deleted", {
      duration: 5000,
      action: {
        label: "Undo",
        onClick: () => {
          if (timerRef.current) {
            clearTimeout(timerRef.current)
            timerRef.current = null
          }
        },
      },
    })
  }

  return (
    <>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        aria-label="Delete entry"
        onClick={() => setConfirmOpen(true)}
      >
        <Trash2 size={16} />
      </Button>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this entry?</AlertDialogTitle>
            <AlertDialogDescription>This cannot be undone.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleConfirm}>Delete</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
