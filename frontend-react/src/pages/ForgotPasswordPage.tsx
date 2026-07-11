import { useState } from "react"
import { Link } from "react-router-dom"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { ArrowLeft } from "lucide-react"
import { AuthCard } from "@/components/AuthCard"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { requestPasswordReset } from "@/api/auth"
import { forgotPasswordSchema, type ForgotPasswordFormValues } from "@/lib/validation"

export function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordFormValues>({ resolver: zodResolver(forgotPasswordSchema) })

  async function onSubmit(values: ForgotPasswordFormValues) {
    // Always shown regardless of outcome — never reveals whether the email exists.
    await requestPasswordReset(values).catch(() => {})
    setSubmitted(true)
  }

  return (
    <AuthCard>
      <Link
        to="/login"
        className="mb-6 -mt-6 flex items-center gap-1 text-sm text-ink-3 hover:text-ink"
      >
        <ArrowLeft size={16} />
        Back to login
      </Link>

      {submitted ? (
        <p className="text-sm text-ink-2">
          If an account exists for that email, a reset link has been sent.
        </p>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3">
          <div>
            <Input
              type="email"
              placeholder="Email"
              autoComplete="email"
              {...register("email")}
            />
            {errors.email && (
              <p className="mt-1 text-xs text-destructive">{errors.email.message}</p>
            )}
          </div>

          <Button type="submit" className="mt-2 w-full rounded-full" disabled={isSubmitting}>
            {isSubmitting ? "Sending…" : "Send reset link"}
          </Button>
        </form>
      )}
    </AuthCard>
  )
}
