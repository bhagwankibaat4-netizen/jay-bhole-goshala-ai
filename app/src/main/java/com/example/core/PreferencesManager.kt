package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gosalaDataStore: DataStore<Preferences> by preferencesDataStore(name = "gosala_prefs")

class PreferencesManager(private val context: Context) {
    private val keyLang = stringPreferencesKey("app_language")
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyAlertsEnabled = booleanPreferencesKey("alerts_enabled")
    private val keyRemindersEnabled = booleanPreferencesKey("reminders_enabled")
    private val keyVetPhone = stringPreferencesKey("vet_phone_number")

    val languageFlow: Flow<AppLanguage> = context.gosalaDataStore.data.map { prefs ->
        when (prefs[keyLang]) {
            AppLanguage.ENGLISH.code -> AppLanguage.ENGLISH
            else -> AppLanguage.HINDI
        }
    }

    val themeModeFlow: Flow<AppThemeMode> = context.gosalaDataStore.data.map { prefs ->
        enumValues<AppThemeMode>().firstOrNull { it.name == prefs[keyTheme] } ?: AppThemeMode.LIGHT
    }

    val alertsEnabledFlow: Flow<Boolean> = context.gosalaDataStore.data.map { prefs ->
        prefs[keyAlertsEnabled] ?: true
    }

    val remindersEnabledFlow: Flow<Boolean> = context.gosalaDataStore.data.map { prefs ->
        prefs[keyRemindersEnabled] ?: true
    }

    val vetPhoneFlow: Flow<String> = context.gosalaDataStore.data.map { prefs ->
        prefs[keyVetPhone].orEmpty()
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

    suspend fun setAlertsEnabled(enabled: Boolean) {
        context.gosalaDataStore.edit { prefs ->
            prefs[keyAlertsEnabled] = enabled
        }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.gosalaDataStore.edit { prefs ->
            prefs[keyRemindersEnabled] = enabled
        }
    }

    suspend fun setVetPhone(phone: String) {
        context.gosalaDataStore.edit { prefs ->
            prefs[keyVetPhone] = phone.trim().take(20)
        }
    }
}
