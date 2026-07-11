import { useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { isAxiosError } from "axios"
import { AuthCard } from "@/components/AuthCard"
import { PasswordInput } from "@/components/PasswordInput"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { register as registerRequest } from "@/api/auth"
import { getMe } from "@/api/users"
import { useAuthStore } from "@/store/authStore"
import { registerSchema, type RegisterFormValues } from "@/lib/validation"
import { cn } from "@/lib/utils"

export function RegisterPage() {
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const setSession = useAuthStore((s) => s.setSession)

  const {
    register: registerField,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) })

  const password = watch("password") ?? ""

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null)
    try {
      const { accessToken } = await registerRequest(values)
      setSession(accessToken)
      const user = await getMe()
      setSession(accessToken, user)
      navigate("/")
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        setFormError("An account with this email already exists.")
      } else if (isAxiosError(err) && err.response?.status === 400) {
        setFormError(err.response.data?.detail ?? "Please check your details and try again.")
      } else {
        setFormError("Something went wrong. Please try again.")
      }
    }
  }

  return (
    <AuthCard>
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3">
        <div>
          <Input placeholder="Name" autoComplete="name" {...registerField("name")} />
          {errors.name && <p className="mt-1 text-xs text-destructive">{errors.name.message}</p>}
        </div>

        <div>
          <Input
            type="email"
            placeholder="Email"
            autoComplete="email"
            {...registerField("email")}
          />
          {errors.email && <p className="mt-1 text-xs text-destructive">{errors.email.message}</p>}
        </div>

        <div>
          <PasswordInput
            placeholder="Password"
            autoComplete="new-password"
            {...registerField("password")}
          />
          <p
            className={cn(
              "mt-1 text-xs text-ink-4",
              password.length >= 8 && "text-teal"
            )}
          >
            At least 8 characters
          </p>
          {errors.password && (
            <p className="mt-1 text-xs text-destructive">{errors.password.message}</p>
          )}
        </div>

        {formError && <p className="text-sm text-destructive">{formError}</p>}

        <Button type="submit" className="mt-2 w-full rounded-full" disabled={isSubmitting}>
          {isSubmitting ? "Creating account…" : "Sign Up"}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-ink-3">
        Already have an account?{" "}
        <Link to="/login" className="font-medium text-brand-accent hover:underline">
          Sign in
        </Link>
      </p>
    </AuthCard>
  )
}
