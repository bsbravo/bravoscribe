package com.bravoscribe.android.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot-password"

    const val RESET_PASSWORD = "reset-password/{token}"
    fun resetPassword(token: String) = "reset-password/$token"

    const val HOME = "home"

    // {date} is an ISO date (yyyy-MM-dd), not an entry id — a past day may not have
    // an entry yet, and prev/next navigation must be able to land on an empty day.
    const val EDITOR = "editor/{date}"
    fun editor(date: String) = "editor/$date"

    const val ENTRIES = "entries"

    const val ENTRY_DETAIL = "entries/{entryId}"
    fun entryDetail(entryId: String) = "entries/$entryId"

    const val PROFILE = "profile"

    /** Top-level destinations shown in the bottom navigation bar. */
    val BOTTOM_NAV_ROUTES = setOf(HOME, ENTRIES, PROFILE)
}
