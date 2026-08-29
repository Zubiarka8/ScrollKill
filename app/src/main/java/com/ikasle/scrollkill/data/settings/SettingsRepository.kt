package com.ikasle.scrollkill.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The "Repository" stage for preferences, backed by DataStore Preferences (CLAUDE.md
 * Storage: DataStore for preferences, Room only for stats).
 *
 * The [DataStore] is injected so tests can point it at a temp file. Read errors surface as
 * defaults rather than crashing the collector, per the DataStore guidance.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<ScrollKillSettings> = dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs ->
            ScrollKillSettings(
                interveneEnabled = prefs[INTERVENE_ENABLED] ?: ScrollKillSettings().interveneEnabled,
            )
        }

    suspend fun setInterveneEnabled(enabled: Boolean) {
        dataStore.edit { it[INTERVENE_ENABLED] = enabled }
    }

    private companion object {
        val INTERVENE_ENABLED = booleanPreferencesKey("intervene_enabled")
    }
}
