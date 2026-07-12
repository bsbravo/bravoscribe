package com.bravoscribe.android.data.repository

import com.bravoscribe.android.data.local.SessionManager
import com.bravoscribe.android.data.local.cookie.TokenStorage
import com.bravoscribe.android.data.remote.api.AuthApi
import com.bravoscribe.android.data.remote.dto.ChangePasswordRequest
import com.bravoscribe.android.data.remote.dto.LoginRequest
import com.bravoscribe.android.data.remote.dto.PasswordResetRequestBody
import com.bravoscribe.android.data.remote.dto.RegisterRequest
import com.bravoscribe.android.data.remote.dto.ResetPasswordRequest
import com.bravoscribe.android.data.remote.dto.UpdatePreferencesRequest
import com.bravoscribe.android.data.remote.dto.UpdateProfileRequest
import com.bravoscribe.android.data.remote.dto.UserResponse
import com.bravoscribe.android.domain.model.Preferences
import com.bravoscribe.android.domain.model.User
import com.bravoscribe.android.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
    private val sessionManager: SessionManager,
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> = runCatching {
        val tokens = authApi.login(LoginRequest(email, password))
        tokenStorage.accessToken = tokens.accessToken
        authApi.getMe().toDomain()
    }

    override suspend fun register(name: String, email: String, password: String): Result<User> = runCatching {
        val tokens = authApi.register(RegisterRequest(name, email, password))
        tokenStorage.accessToken = tokens.accessToken
        authApi.getMe().toDomain()
    }

    override suspend fun logout() {
        runCatching { authApi.logout() }
        sessionManager.onLogout()
    }

    override suspend fun tryRestoreSession(): User? = runCatching {
        val tokens = authApi.refresh()
        tokenStorage.accessToken = tokens.accessToken
        authApi.getMe().toDomain()
    }.getOrNull()

    override suspend fun getMe(): Result<User> = runCatching { authApi.getMe().toDomain() }

    override suspend fun updateProfile(name: String): Result<User> = runCatching {
        authApi.updateProfile(UpdateProfileRequest(name)).toDomain()
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        authApi.changePassword(ChangePasswordRequest(currentPassword, newPassword))
    }

    override suspend fun updatePreferences(
        reminderTime: String?,
        weeklySummaryEnabled: Boolean?,
    ): Result<Preferences> = runCatching {
        val response = authApi.updatePreferences(UpdatePreferencesRequest(reminderTime, weeklySummaryEnabled))
        Preferences(response.reminderTime, response.weeklySummaryEnabled)
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> = runCatching {
        authApi.requestPasswordReset(PasswordResetRequestBody(email))
    }

    override suspend fun confirmPasswordReset(token: String, newPassword: String): Result<Unit> = runCatching {
        authApi.confirmPasswordReset(ResetPasswordRequest(token, newPassword))
    }

    private fun UserResponse.toDomain() = User(
        id = id,
        name = name,
        email = email,
        role = role,
        active = active,
        createdAt = createdAt,
    )
}
