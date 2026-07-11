package com.bravoscribe.android.data.remote.api

import com.bravoscribe.android.data.remote.dto.AccessTokenResponse
import com.bravoscribe.android.data.remote.dto.ChangePasswordRequest
import com.bravoscribe.android.data.remote.dto.LoginRequest
import com.bravoscribe.android.data.remote.dto.PasswordResetRequestBody
import com.bravoscribe.android.data.remote.dto.PreferencesResponse
import com.bravoscribe.android.data.remote.dto.RegisterRequest
import com.bravoscribe.android.data.remote.dto.ResetPasswordRequest
import com.bravoscribe.android.data.remote.dto.UpdatePreferencesRequest
import com.bravoscribe.android.data.remote.dto.UpdateProfileRequest
import com.bravoscribe.android.data.remote.dto.UserResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("api/users/login")
    suspend fun login(@Body body: LoginRequest): AccessTokenResponse

    @POST("api/users/register")
    suspend fun register(@Body body: RegisterRequest): AccessTokenResponse

    // No body — the refreshToken httpOnly cookie is sent automatically by OkHttp's CookieJar.
    @POST("api/users/refresh")
    suspend fun refresh(): AccessTokenResponse

    @DELETE("api/users/logout")
    suspend fun logout()

    @GET("api/users/me")
    suspend fun getMe(): UserResponse

    @PUT("api/users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): UserResponse

    @PUT("api/users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @PUT("api/users/me/preferences")
    suspend fun updatePreferences(@Body body: UpdatePreferencesRequest): PreferencesResponse

    @POST("api/users/password-reset/request")
    suspend fun requestPasswordReset(@Body body: PasswordResetRequestBody)

    @PUT("api/users/password-reset/confirm")
    suspend fun confirmPasswordReset(@Body body: ResetPasswordRequest)
}
