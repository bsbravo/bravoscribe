package com.bravoscribe.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class AccessTokenResponse(val accessToken: String)

@Serializable
data class UserResponse(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val active: Boolean,
    val createdAt: String,
)

@Serializable
data class UpdateProfileRequest(val name: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class PasswordResetRequestBody(val email: String)

@Serializable
data class ResetPasswordRequest(val token: String, val newPassword: String)

@Serializable
data class PreferencesResponse(val reminderTime: String, val weeklySummaryEnabled: Boolean)

@Serializable
data class UpdatePreferencesRequest(
    val reminderTime: String? = null,
    val weeklySummaryEnabled: Boolean? = null,
)

@Serializable
data class ProblemDetail(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
)
