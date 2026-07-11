import { useState } from "react"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { isAxiosError } from "axios"
import { AuthCard } from "@/components/AuthCard"
import { PasswordInput } from "@/components/PasswordInput"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { login } from "@/api/auth"
import { getMe } from "@/api/users"
import { useAuthStore } from "@/store/authStore"
import { loginSchema, type LoginFormValues } from "@/lib/validation"

export function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const reason = searchParams.get("reason")
  const message = searchParams.get("message")
  const [bannerDismissed, setBannerDismissed] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const setSession = useAuthStore((s) => s.setSession)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) })

  async function onSubmit(values: LoginFormValues) {
    setFormError(null)
    try {
      const { accessToken } = await login(values)
      setSession(accessToken)
      const user = await getMe()
      setSession(accessToken, user)
      navigate("/")
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 401) {
        setFormError("Incorrect email or password.")
      } else {
        setFormError("Something went wrong. Please try again.")
      }
    }
  }

  const showSessionExpired = reason === "session-expired" && !bannerDismissed

  return (
    <AuthCard>
      {showSessionExpired && (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          ⏱ Your session has expired. Please sign in again.
        </div>
      )}
      {message === "password-updated" && (
        <div className="mb-4 rounded-lg border border-teal-light bg-teal-light px-4 py-3 text-sm text-teal">
          Password updated. Please sign in with your new password.
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3">
        <div>
          <Input
            type="email"
            placeholder="Email"
            autoComplete="email"
            {...register("email", { onChange: () => setBannerDismissed(true) })}
          />
          {errors.email && <p className="mt-1 text-xs text-destructive">{errors.email.message}</p>}
        </div>

        <div>
          <PasswordInput
            placeholder="Password"
            autoComplete="current-password"
            {...register("password", { onChange: () => setBannerDismissed(true) })}
          />
          {errors.password && (
            <p className="mt-1 text-xs text-destructive">{errors.password.message}</p>
          )}
        </div>

        <div className="text-right">
          <Link to="/forgot-password" className="text-sm text-ink-3 hover:text-ink">
            Forgot password?
          </Link>
        </div>

        {formError && <p className="text-sm text-destructive">{formError}</p>}

        <Button type="submit" className="mt-2 w-full rounded-full" disabled={isSubmitting}>
          {isSubmitting ? "Signing in…" : "Sign In"}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-ink-3">
        Don&apos;t have an account?{" "}
        <Link to="/register" className="font-medium text-brand-accent hover:underline">
          Sign up
        </Link>
      </p>
    </AuthCard>
  )
}
