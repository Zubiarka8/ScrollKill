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
                watchingDisabledPackages = prefs[WATCHING_DISABLED_PACKAGES] ?: defaults.watchingDisabledPackages,
                defaultDailyLimit = prefs[DEFAULT_DAILY_LIMIT]
                    ?.let(DailyLimit::parse)
                    ?: defaults.defaultDailyLimit,
                dailyLimitOverrides = prefs[DAILY_LIMIT_OVERRIDES]
                    ?.let(::parseDailyLimitOverrides)
                    ?: defaults.dailyLimitOverrides,
                detectionConfidenceFloor = prefs[DETECTION_CONFIDENCE_FLOOR]
                    ?.let { name -> runCatching { ConfidenceFloor.valueOf(name) }.getOrNull() }
                    ?: defaults.detectionConfidenceFloor,
                blockingCooldown = prefs[BLOCKING_COOLDOWN]
                    ?.let { name -> runCatching { BlockingCooldown.valueOf(name) }.getOrNull() }
                    ?: defaults.blockingCooldown,
                sessionIdleTimeout = prefs[SESSION_IDLE_TIMEOUT]
                    ?.let { name -> runCatching { IdleTimeout.valueOf(name) }.getOrNull() }
                    ?: defaults.sessionIdleTimeout,
                minSessionDuration = prefs[MIN_SESSION_DURATION]
                    ?.let { name -> runCatching { MinSessionDuration.valueOf(name) }.getOrNull() }
                    ?: defaults.minSessionDuration,
                statsWindow = prefs[STATS_WINDOW]
                    ?.let { name -> runCatching { StatsWindow.valueOf(name) }.getOrNull() }
                    ?: defaults.statsWindow,
                historyRetention = prefs[HISTORY_RETENTION]
                    ?.let { name -> runCatching { RetentionWindow.valueOf(name) }.getOrNull() }
                    ?: defaults.historyRetention,
                onboardingComplete = prefs[ONBOARDING_COMPLETE] ?: defaults.onboardingComplete,
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

    /**
     * Turn AccessibilityService observation on or off for a single package. When off the
     * service does not detect, track or enforce limits for it (see [watchedPackagesFrom]).
     */
    suspend fun setWatchingEnabledForPackage(packageName: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[WATCHING_DISABLED_PACKAGES] ?: emptySet()
            prefs[WATCHING_DISABLED_PACKAGES] =
                if (enabled) current - packageName else current + packageName
        }
    }

    /** Set the daily budget applied to every watched app by default. */
    suspend fun setDefaultDailyLimit(limit: DailyLimit) {
        dataStore.edit { it[DEFAULT_DAILY_LIMIT] = limit.storageToken }
    }

    /**
     * Override (or, with [limit] null, clear the override for) a single package's daily budget.
     * A cleared override falls back to [ScrollKillSettings.defaultDailyLimit].
     */
    suspend fun setDailyLimitOverride(packageName: String, limit: DailyLimit?) {
        dataStore.edit { prefs ->
            val kept = (prefs[DAILY_LIMIT_OVERRIDES] ?: emptySet())
                .filterNot { it.substringBeforeLast('=') == packageName }
                .toSet()
            prefs[DAILY_LIMIT_OVERRIDES] =
                if (limit == null) kept else kept + "$packageName=${limit.storageToken}"
        }
    }

    /** Detector confidence floor shared by the BlockingEngine and the SessionTracker. */
    suspend fun setDetectionConfidenceFloor(floor: ConfidenceFloor) {
        dataStore.edit { it[DETECTION_CONFIDENCE_FLOOR] = floor.name }
    }

    /** Quiet period after a BlockingEngine intervention before it may fire again. */
    suspend fun setBlockingCooldown(cooldown: BlockingCooldown) {
        dataStore.edit { it[BLOCKING_COOLDOWN] = cooldown.name }
    }

    /** Idle gap that ends an open SessionTracker engagement. */
    suspend fun setSessionIdleTimeout(timeout: IdleTimeout) {
        dataStore.edit { it[SESSION_IDLE_TIMEOUT] = timeout.name }
    }

    /** Shortest engagement the SessionTracker will emit. */
    suspend fun setMinSessionDuration(duration: MinSessionDuration) {
        dataStore.edit { it[MIN_SESSION_DURATION] = duration.name }
    }

    suspend fun setStatsWindow(window: StatsWindow) {
        dataStore.edit { it[STATS_WINDOW] = window.name }
    }

    suspend fun setHistoryRetention(retention: RetentionWindow) {
        dataStore.edit { it[HISTORY_RETENTION] = retention.name }
    }

    /** Record that the user has seen the first-run rationale and made an affirmative choice. */
    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    private companion object {
        val INTERVENE_ENABLED = booleanPreferencesKey("intervene_enabled")
        val BLOCKING_DISABLED_PACKAGES = stringSetPreferencesKey("blocking_disabled_packages")
        val WATCHING_DISABLED_PACKAGES = stringSetPreferencesKey("watching_disabled_packages")
        val DEFAULT_DAILY_LIMIT = stringPreferencesKey("default_daily_limit")
        val DAILY_LIMIT_OVERRIDES = stringSetPreferencesKey("daily_limit_overrides")
        val DETECTION_CONFIDENCE_FLOOR = stringPreferencesKey("detection_confidence_floor")
        val BLOCKING_COOLDOWN = stringPreferencesKey("blocking_cooldown")
        val SESSION_IDLE_TIMEOUT = stringPreferencesKey("session_idle_timeout")
        val MIN_SESSION_DURATION = stringPreferencesKey("min_session_duration")
        val STATS_WINDOW = stringPreferencesKey("stats_window")
        val HISTORY_RETENTION = stringPreferencesKey("history_retention")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

        /** Parse `"pkg=TOKEN"` entries; malformed entries and unknown tokens are dropped. */
        fun parseDailyLimitOverrides(tokens: Set<String>): Map<String, DailyLimit> =
            tokens.mapNotNull { token ->
                val name = token.substringAfterLast('=', missingDelimiterValue = "")
                val pkg = token.substringBeforeLast('=', missingDelimiterValue = "")
                if (pkg.isEmpty() || name.isEmpty()) return@mapNotNull null
                DailyLimit.parse(name)?.let { pkg to it }
            }.toMap()
    }
}
