import type { ReactNode } from "react"

export function AuthCard({ children }: { children: ReactNode }) {
  return (
    <div className="w-full max-w-sm">
      <div className="mb-12 text-center">
        <h1 className="font-serif text-4xl font-normal tracking-tight text-ink">
          📓 Bravoscribe
        </h1>
        <p className="mt-2 font-serif text-sm text-ink-3 italic">Your daily writing companion</p>
      </div>
      {children}
    </div>
  )
}
