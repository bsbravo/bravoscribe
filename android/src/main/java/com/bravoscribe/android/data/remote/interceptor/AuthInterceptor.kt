package com.bravoscribe.android.data.remote.interceptor

import com.bravoscribe.android.data.local.cookie.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Attaches the access token to every request except the endpoints that don't need it. */
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val skipsAuth = NO_AUTH_PATHS.any { path.endsWith(it) }
        val token = tokenStorage.accessToken

        return if (skipsAuth || token == null) {
            chain.proceed(request)
        } else {
            chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        }
    }

    private companion object {
        val NO_AUTH_PATHS = listOf(
            "/api/users/login",
            "/api/users/register",
            "/api/users/refresh",
            "/api/users/password-reset/request",
            "/api/users/password-reset/confirm",
        )
    }
}
