import { useEffect } from "react"
import { useBlocker } from "react-router-dom"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

interface UnsavedChangesGuardProps {
  hasUnsavedChanges: boolean
  onStayAndSave: () => void | Promise<void>
}

export function UnsavedChangesGuard({
  hasUnsavedChanges,
  onStayAndSave,
}: UnsavedChangesGuardProps) {
  useEffect(() => {
    function handler(e: BeforeUnloadEvent) {
      if (hasUnsavedChanges) {
        e.preventDefault()
        e.returnValue = ""
      }
    }
    window.addEventListener("beforeunload", handler)
    return () => window.removeEventListener("beforeunload", handler)
  }, [hasUnsavedChanges])

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      hasUnsavedChanges && currentLocation.pathname !== nextLocation.pathname
  )

  return (
    <AlertDialog open={blocker.state === "blocked"}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Leave without saving?</AlertDialogTitle>
          <AlertDialogDescription>Your changes will be lost.</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogAction
            variant="destructive"
            onClick={() => {
              if (blocker.state === "blocked") blocker.proceed()
            }}
          >
            Leave anyway
          </AlertDialogAction>
          <AlertDialogAction
            onClick={async () => {
              await onStayAndSave()
              if (blocker.state === "blocked") blocker.reset()
            }}
          >
            Stay and save
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
