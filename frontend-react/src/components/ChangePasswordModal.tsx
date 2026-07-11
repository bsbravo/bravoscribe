import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { isAxiosError } from "axios"
import { changePassword } from "@/api/users"
import { useAuthStore } from "@/store/authStore"
import { changePasswordSchema, type ChangePasswordFormValues } from "@/lib/validation"
import { PasswordInput } from "@/components/PasswordInput"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { cn } from "@/lib/utils"

export function ChangePasswordModal({ trigger }: { trigger: React.ReactNode }) {
  const navigate = useNavigate()
  const clearAuth = useAuthStore((s) => s.clear)
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [countdown, setCountdown] = useState<number | null>(null)

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordFormValues>({ resolver: zodResolver(changePasswordSchema) })

  const newPassword = watch("newPassword") ?? ""

  useEffect(() => {
    if (countdown === null) return
    if (countdown === 0) {
      clearAuth()
      navigate("/login")
      return
    }
    const timer = setTimeout(() => setCountdown((c) => (c ?? 1) - 1), 1000)
    return () => clearTimeout(timer)
  }, [countdown, clearAuth, navigate])

  async function onSubmit(values: ChangePasswordFormValues) {
    setFormError(null)
    try {
      await changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      })
      setCountdown(3)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 400) {
        setFormError(err.response.data?.detail ?? "Current password is incorrect.")
      } else {
        setFormError("Something went wrong. Please try again.")
      }
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (countdown !== null) return
        setOpen(v)
        if (!v) {
          reset()
          setFormError(null)
        }
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Change password</DialogTitle>
          <DialogDescription>
            You&apos;ll need to sign in again on all devices after changing your password.
          </DialogDescription>
        </DialogHeader>

        {countdown !== null ? (
          <p className="text-sm text-teal">
            Password updated successfully. Signing you out in {countdown}...
          </p>
        ) : (
          <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3">
            <div>
              <PasswordInput
                placeholder="Current password"
                autoComplete="current-password"
                {...register("currentPassword")}
              />
              {errors.currentPassword && (
                <p className="mt-1 text-xs text-destructive">{errors.currentPassword.message}</p>
              )}
            </div>
            <div>
              <PasswordInput
                placeholder="New password"
                autoComplete="new-password"
                {...register("newPassword")}
              />
              <p
                className={cn("mt-1 text-xs text-ink-4", newPassword.length >= 8 && "text-teal")}
              >
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

            {formError && <p className="text-sm text-destructive">{formError}</p>}

            <DialogFooter>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Updating…" : "Change password"}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
