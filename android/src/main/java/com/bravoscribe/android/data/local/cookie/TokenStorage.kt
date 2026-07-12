package com.bravoscribe.android.data.local.cookie

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** accessToken storage — EncryptedSharedPreferences per SPEC.md's Auth flow section. */
@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bravoscribe_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Volatile
    private var cachedAccessToken: String? = prefsAccessTokenOrNull()

    var accessToken: String?
        get() = cachedAccessToken
        set(value) {
            cachedAccessToken = value
            prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()
        }

    fun clear() {
        cachedAccessToken = null
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    private fun prefsAccessTokenOrNull(): String? =
        runCatching { prefs.getString(KEY_ACCESS_TOKEN, null) }.getOrNull()

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
    }
}
