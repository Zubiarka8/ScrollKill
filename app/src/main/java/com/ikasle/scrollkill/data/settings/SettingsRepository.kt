package com.ikasle.scrollkill.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The "Repository" stage for preferences, backed by DataStore Preferences (CLAUDE.md
 * Storage: DataStore for preferences, Room only for stats).
 *
 * The [DataStore] is injected so tests can point it at a temp file. Read errors surface as
 * defaults rather than crashing the collector, per the DataStore guidance; unknown enum
 * strings likewise fall back to the default.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<ScrollKillSettings> = dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs ->
            val defaults = ScrollKillSettings()
            ScrollKillSettings(
                interveneEnabled = prefs[INTERVENE_ENABLED] ?: defaults.interveneEnabled,
                blockingDisabledPackages = prefs[BLOCKING_DISABLED_PACKAGES] ?: defaults.blockingDisabledPackages,
                statsWindow = prefs[STATS_WINDOW]
                    ?.let { name -> runCatching { StatsWindow.valueOf(name) }.getOrNull() }
                    ?: defaults.statsWindow,
                historyRetention = prefs[HISTORY_RETENTION]
                    ?.let { name -> runCatching { RetentionWindow.valueOf(name) }.getOrNull() }
                    ?: defaults.historyRetention,
            )
        }

    suspend fun setInterveneEnabled(enabled: Boolean) {
        dataStore.edit { it[INTERVENE_ENABLED] = enabled }
    }

    /** Turn the BACK-press intervention on or off for a single package. */
    suspend fun setBlockingEnabledForPackage(packageName: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[BLOCKING_DISABLED_PACKAGES] ?: emptySet()
            prefs[BLOCKING_DISABLED_PACKAGES] =
                if (enabled) current - packageName else current + packageName
        }
    }

    suspend fun setStatsWindow(window: StatsWindow) {
        dataStore.edit { it[STATS_WINDOW] = window.name }
    }

    suspend fun setHistoryRetention(retention: RetentionWindow) {
        dataStore.edit { it[HISTORY_RETENTION] = retention.name }
    }

    private companion object {
        val INTERVENE_ENABLED = booleanPreferencesKey("intervene_enabled")
        val BLOCKING_DISABLED_PACKAGES = stringSetPreferencesKey("blocking_disabled_packages")
        val STATS_WINDOW = stringPreferencesKey("stats_window")
        val HISTORY_RETENTION = stringPreferencesKey("history_retention")
    }
}
