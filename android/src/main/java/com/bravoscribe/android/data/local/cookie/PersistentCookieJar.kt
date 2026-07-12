package com.bravoscribe.android.data.local.cookie

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The refreshToken cookie is httpOnly at the backend — the app never receives it
 * as a plain string to store manually (see SYSTEM_SPEC.md / user-service SPEC).
 * httpOnly only blocks JS `document.cookie` access in a browser; it doesn't
 * change the wire protocol, so OkHttp's CookieJar can capture and resend it
 * exactly like the browser does automatically. This persists that cookie jar
 * to EncryptedSharedPreferences so the session survives app restarts, without
 * the app ever needing to read or handle the token value itself.
 */
@Singleton
class PersistentCookieJar @Inject constructor(
    @ApplicationContext context: Context,
) : CookieJar {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bravoscribe_cookies",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // host -> cookie name -> serialized cookie
    private val memoryCache = ConcurrentHashMap<String, MutableMap<String, String>>()

    init {
        prefs.all.forEach { (key, value) ->
            val (host, name) = key.split(COOKIE_KEY_SEPARATOR, limit = 2).let {
                if (it.size == 2) it[0] to it[1] else return@forEach
            }
            (value as? String)?.let { serialized ->
                memoryCache.getOrPut(host) { mutableMapOf() }[name] = serialized
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val hostCookies = memoryCache.getOrPut(url.host) { mutableMapOf() }
        val editor = prefs.edit()
        for (cookie in cookies) {
            val serialized = cookie.toString()
            hostCookies[cookie.name] = serialized
            editor.putString("${url.host}$COOKIE_KEY_SEPARATOR${cookie.name}", serialized)
        }
        editor.apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val hostCookies = memoryCache[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return hostCookies.values.mapNotNull { Cookie.parse(url, it) }
            .filter { it.expiresAt > now }
    }

    fun clear() {
        memoryCache.clear()
        prefs.edit().clear().apply()
    }

    private companion object {
        const val COOKIE_KEY_SEPARATOR = "|"
    }
}
