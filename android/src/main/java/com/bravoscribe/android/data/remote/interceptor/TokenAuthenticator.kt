package com.bravoscribe.android.data.remote.interceptor

import com.bravoscribe.android.BuildConfig
import com.bravoscribe.android.data.local.SessionManager
import com.bravoscribe.android.data.local.cookie.TokenStorage
import com.bravoscribe.android.di.RefreshHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles 401s by calling POST /api/users/refresh (no body — the refreshToken
 * cookie is sent automatically by the shared CookieJar) and retrying once.
 *
 * Uses a separate, Authenticator-free OkHttpClient for the refresh call itself
 * to avoid infinite recursion (this class can't depend on the client it's
 * installed on). If refresh fails, clears stored credentials — SessionManager
 * observes this and navigates to Login with sessionExpired=true.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    @RefreshHttpClient private val refreshClient: OkHttpClient,
    private val tokenStorage: TokenStorage,
    private val sessionManager: SessionManager,
) : Authenticator {

    private val json = Json { ignoreUnknownKeys = true }

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (NO_RETRY_PATHS.any { path.endsWith(it) }) return null
        if (responseCount(response) >= 2) return null // already retried once

        val newToken = synchronized(this) {
            // Another request may have already refreshed while we waited for the lock.
            val current = tokenStorage.accessToken
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (current != null && current != failedToken) {
                current
            } else {
                refreshSync()
            }
        } ?: run {
            sessionManager.onSessionExpired()
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun refreshSync(): String? = runBlocking {
        runCatching {
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/users/refresh")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            refreshClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                val token = json.decodeFromString<AccessTokenBody>(body).accessToken
                tokenStorage.accessToken = token
                token
            }
        }.getOrNull()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    @kotlinx.serialization.Serializable
    private data class AccessTokenBody(val accessToken: String)

    private companion object {
        val NO_RETRY_PATHS = listOf("/api/users/login", "/api/users/register", "/api/users/refresh")
    }
}
