package com.bravoscribe.android.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.themeDataStore by preferencesDataStore(name = "theme_preferences")

private val IS_DARK_KEY = booleanPreferencesKey("is_dark")

class ThemeDataStore(private val context: Context) {

    val isDark: Flow<Boolean> = context.themeDataStore.data.map { it[IS_DARK_KEY] ?: false }

    suspend fun setDark(isDark: Boolean) {
        context.themeDataStore.edit { it[IS_DARK_KEY] = isDark }
    }
}
