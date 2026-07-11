import { createBrowserRouter, Navigate } from "react-router-dom"
import { AuthLayout } from "@/layouts/AuthLayout"
import { AppLayout } from "@/layouts/AppLayout"
import { ProtectedRoute } from "@/components/ProtectedRoute"
import { LoginPage } from "@/pages/LoginPage"
import { RegisterPage } from "@/pages/RegisterPage"
import { ForgotPasswordPage } from "@/pages/ForgotPasswordPage"
import { ResetPasswordPage } from "@/pages/ResetPasswordPage"
import { TodayPage } from "@/pages/TodayPage"
import { EntriesListPage } from "@/pages/EntriesListPage"
import { EntryDetailPage } from "@/pages/EntryDetailPage"
import { EntryEditPage } from "@/pages/EntryEditPage"
import { ProfilePage } from "@/pages/ProfilePage"

export const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      { path: "/login", element: <LoginPage /> },
      { path: "/register", element: <RegisterPage /> },
      { path: "/forgot-password", element: <ForgotPasswordPage /> },
      { path: "/reset-password", element: <ResetPasswordPage /> },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: "/", element: <TodayPage /> },
          { path: "/entries", element: <EntriesListPage /> },
          { path: "/entries/:date", element: <EntryDetailPage /> },
          { path: "/entries/:date/edit", element: <EntryEditPage /> },
          { path: "/profile", element: <ProfilePage /> },
        ],
      },
    ],
  },
  { path: "*", element: <Navigate to="/" replace /> },
])
