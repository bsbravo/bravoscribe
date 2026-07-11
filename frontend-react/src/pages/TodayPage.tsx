import { EntryEditor } from "@/components/EntryEditor"
import { today } from "@/lib/date"
import { useDocumentTitle } from "@/hooks/useDocumentTitle"

export function TodayPage() {
  useDocumentTitle("Today · Bravoscribe")
  return <EntryEditor date={today()} />
}
