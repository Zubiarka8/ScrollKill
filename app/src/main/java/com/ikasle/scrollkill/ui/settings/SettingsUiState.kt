package com.ikasle.scrollkill.ui.settings

import com.ikasle.scrollkill.data.settings.DailyLimit
import com.ikasle.scrollkill.data.settings.RetentionWindow
import com.ikasle.scrollkill.data.settings.StatsWindow

/** Everything the settings screen renders. Produced by [SettingsViewModel]. */
data class SettingsUiState(
    val interveneEnabled: Boolean = true,
    val statsWindow: StatsWindow = StatsWindow.LAST_7_DAYS,
    val historyRetention: RetentionWindow = RetentionWindow.DAYS_90,
    /** Daily budget applied to every watched app that has no per-app override. */
    val defaultDailyLimit: DailyLimit = DailyLimit.OFF,
    /** One row per watched app, sorted by display name. */
    val apps: List<AppToggleUi> = emptyList(),
)

data class AppToggleUi(
    val packageName: String,
    val displayName: String,
    /** false = the user turned the BACK-press nudge off for this app. */
    val blockingEnabled: Boolean,
    /** The budget in effect for this app: its override if set, otherwise the global default. */
    val dailyLimit: DailyLimit = DailyLimit.OFF,
    /** true = [dailyLimit] comes from a per-app override; false = it is the inherited default. */
    val dailyLimitIsOverride: Boolean = false,
)
