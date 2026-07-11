package com.bravoscribe.android.data.local

import com.bravoscribe.android.data.local.cookie.PersistentCookieJar
import com.bravoscribe.android.data.local.cookie.TokenStorage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the network layer (TokenAuthenticator, called on a background thread,
 * no access to Compose Navigation) and the UI layer (which navigates to Login
 * with sessionExpired=true when this fires). See SPEC.md's Auth flow section.
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val cookieJar: PersistentCookieJar,
) {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    fun onSessionExpired() {
        tokenStorage.clear()
        cookieJar.clear()
        _sessionExpired.tryEmit(Unit)
    }

    fun onLogout() {
        tokenStorage.clear()
        cookieJar.clear()
    }
}
