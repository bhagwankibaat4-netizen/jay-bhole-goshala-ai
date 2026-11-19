package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gosalaDataStore: DataStore<Preferences> by preferencesDataStore(name = "gosala_prefs")

class PreferencesManager(private val context: Context) {
    private val keyLang = stringPreferencesKey("app_language")
    private val keyTheme = stringPreferencesKey("theme_mode")

    val languageFlow: Flow<AppLanguage> = context.gosalaDataStore.data.map { prefs ->
        when (prefs[keyLang]) {
            AppLanguage.ENGLISH.code -> AppLanguage.ENGLISH
            else -> AppLanguage.HINDI
        }
    }

    val themeModeFlow: Flow<AppThemeMode> = context.gosalaDataStore.data.map { prefs ->
        enumValues<AppThemeMode>().firstOrNull { it.name == prefs[keyTheme] } ?: AppThemeMode.LIGHT
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.gosalaDataStore.edit { prefs ->
            prefs[keyLang] = language.code
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.gosalaDataStore.edit { prefs ->
            prefs[keyTheme] = mode.name
        }
    }
}
