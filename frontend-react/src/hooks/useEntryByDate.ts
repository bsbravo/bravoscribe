import { useQuery } from "@tanstack/react-query"
import { isAxiosError } from "axios"
import { getEntryByDate } from "@/api/journal"

// Resolves to `null` (not an error) when no entry exists for that date yet.
export function useEntryByDate(date: string) {
  return useQuery({
    queryKey: ["entry", "date", date],
    queryFn: async () => {
      try {
        return await getEntryByDate(date)
      } catch (err) {
        if (isAxiosError(err) && err.response?.status === 404) return null
        throw err
      }
    },
  })
}
