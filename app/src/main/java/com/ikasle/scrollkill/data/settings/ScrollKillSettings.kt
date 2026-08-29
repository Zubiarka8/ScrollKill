package com.ikasle.scrollkill.data.settings

/**
 * User preferences, read as an immutable snapshot. A data class so fields can be added
 * without breaking callers; every field must have a default matching "unset".
 */
data class ScrollKillSettings(
    /** Master switch for the BACK-press intervention. */
    val interveneEnabled: Boolean = true,
    /** Packages the user has turned intervention OFF for; sessions are still tracked. */
    val blockingDisabledPackages: Set<String> = emptySet(),
    /** Daily budget applied to every watched app unless [dailyLimitOverrides] says otherwise. */
    val defaultDailyLimit: DailyLimit = DailyLimit.OFF,
    /** Per-app daily budget that wins over [defaultDailyLimit]. Absent = use the default. */
    val dailyLimitOverrides: Map<String, DailyLimit> = emptyMap(),
    /** Rolling window the home screen aggregates over. */
    val statsWindow: StatsWindow = StatsWindow.LAST_7_DAYS,
    /** How long session history is kept. */
    val historyRetention: RetentionWindow = RetentionWindow.DAYS_90,
)
