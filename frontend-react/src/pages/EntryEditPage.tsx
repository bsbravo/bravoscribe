import { Navigate, useParams } from "react-router-dom"
import { EntryEditor } from "@/components/EntryEditor"
import { useDocumentTitle } from "@/hooks/useDocumentTitle"
import { today } from "@/lib/date"

export function EntryEditPage() {
  const { date } = useParams<{ date: string }>()
  useDocumentTitle(date ? `${date} · Bravoscribe` : "Bravoscribe")

  if (!date) return <Navigate to="/entries" replace />
  if (date === today()) return <Navigate to="/" replace />

  return <EntryEditor date={date} />
}
