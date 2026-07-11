package com.bravoscribe.android.domain.repository

import com.bravoscribe.android.domain.model.Preferences
import com.bravoscribe.android.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(name: String, email: String, password: String): Result<User>
    suspend fun logout()

    /** Attempts a silent refresh + getMe on app launch. Returns the restored user, or null if no valid session. */
    suspend fun tryRestoreSession(): User?

    suspend fun getMe(): Result<User>
    suspend fun updateProfile(name: String): Result<User>
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    suspend fun updatePreferences(reminderTime: String?, weeklySummaryEnabled: Boolean?): Result<Preferences>
    suspend fun requestPasswordReset(email: String): Result<Unit>
    suspend fun confirmPasswordReset(token: String, newPassword: String): Result<Unit>
}
