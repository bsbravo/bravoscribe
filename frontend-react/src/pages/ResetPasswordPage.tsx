import { useEffect, useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { isAxiosError } from "axios"
import { AuthCard } from "@/components/AuthCard"
import { PasswordInput } from "@/components/PasswordInput"
import { Button } from "@/components/ui/button"
import { confirmPasswordReset } from "@/api/auth"
import { resetPasswordSchema, type ResetPasswordFormValues } from "@/lib/validation"
import { cn } from "@/lib/utils"

function readTokenFromHash(): string | null {
  const match = /token=([^&]+)/.exec(window.location.hash)
  return match ? decodeURIComponent(match[1]) : null
}

export function ResetPasswordPage() {
  const navigate = useNavigate()
  const [token] = useState(readTokenFromHash)
  const [status, setStatus] = useState<"idle" | "success" | "invalid">("idle")

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordFormValues>({ resolver: zodResolver(resetPasswordSchema) })

  const newPassword = watch("newPassword") ?? ""

  useEffect(() => {
    if (status !== "success") return
    const timer = setTimeout(() => navigate("/login"), 2000)
    return () => clearTimeout(timer)
  }, [status, navigate])

  async function onSubmit(values: ResetPasswordFormValues) {
    if (!token) {
      setStatus("invalid")
      return
    }
    try {
      await confirmPasswordReset({ token, newPassword: values.newPassword })
      setStatus("success")
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 400) {
        setStatus("invalid")
      } else {
        setStatus("invalid")
      }
    }
  }

  if (status === "success") {
    return (
      <AuthCard>
        <p className="text-sm text-ink-2">Password changed. Please sign in.</p>
      </AuthCard>
    )
  }

  if (status === "invalid") {
    return (
      <AuthCard>
        <p className="text-sm text-destructive">
          This reset link is invalid or has expired. Request a new one.
        </p>
        <Link
          to="/forgot-password"
          className="mt-3 inline-block text-sm font-medium text-brand-accent hover:underline"
        >
          Request a new link
        </Link>
      </AuthCard>
    )
  }

  return (
    <AuthCard>
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3">
        <div>
          <PasswordInput
            placeholder="New password"
            autoComplete="new-password"
            {...register("newPassword")}
          />
          <p className={cn("mt-1 text-xs text-ink-4", newPassword.length >= 8 && "text-teal")}>
            At least 8 characters
          </p>
          {errors.newPassword && (
            <p className="mt-1 text-xs text-destructive">{errors.newPassword.message}</p>
          )}
        </div>

        <div>
          <PasswordInput
            placeholder="Confirm new password"
            autoComplete="new-password"
            {...register("confirmPassword")}
          />
          {errors.confirmPassword && (
            <p className="mt-1 text-xs text-destructive">{errors.confirmPassword.message}</p>
          )}
        </div>

        <Button type="submit" className="mt-2 w-full rounded-full" disabled={isSubmitting}>
          {isSubmitting ? "Updating…" : "Reset password"}
        </Button>
      </form>
    </AuthCard>
  )
}
