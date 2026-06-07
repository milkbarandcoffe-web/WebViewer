package com.webviewer.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.webviewer.app.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_MACRO_PACKAGE   = stringPreferencesKey("macro_package")
        val KEY_MACRO_NAME      = stringPreferencesKey("macro_name")
        val KEY_LAST_URL        = stringPreferencesKey("last_session_url")
        val KEY_ACTIVE_PROJECT  = longPreferencesKey("active_project_id")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            AppSettings(
                macroDroidPackage  = prefs[KEY_MACRO_PACKAGE]  ?: "com.arlosoft.macrodroid",
                macroDroidMacroName = prefs[KEY_MACRO_NAME]    ?: "WebViewerBolla",
                lastSessionUrl     = prefs[KEY_LAST_URL]       ?: "",
                activeProjectId    = prefs[KEY_ACTIVE_PROJECT] ?: -1L
            )
        }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MACRO_PACKAGE]  = settings.macroDroidPackage
            prefs[KEY_MACRO_NAME]     = settings.macroDroidMacroName
            prefs[KEY_LAST_URL]       = settings.lastSessionUrl
            prefs[KEY_ACTIVE_PROJECT] = settings.activeProjectId
        }
    }

    suspend fun saveLastUrl(url: String) {
        context.dataStore.edit { it[KEY_LAST_URL] = url }
    }

    suspend fun saveActiveProject(id: Long) {
        context.dataStore.edit { it[KEY_ACTIVE_PROJECT] = id }
    }
}
