package com.ikasle.scrollkill.ui.settings

import com.ikasle.scrollkill.data.settings.RetentionWindow
import com.ikasle.scrollkill.data.settings.StatsWindow

/** Everything the settings screen renders. Produced by [SettingsViewModel]. */
data class SettingsUiState(
    val interveneEnabled: Boolean = true,
    val statsWindow: StatsWindow = StatsWindow.LAST_7_DAYS,
    val historyRetention: RetentionWindow = RetentionWindow.DAYS_90,
    /** One row per watched app, sorted by display name. */
    val apps: List<AppToggleUi> = emptyList(),
)

data class AppToggleUi(
    val packageName: String,
    val displayName: String,
    /** false = the user turned the BACK-press nudge off for this app. */
    val blockingEnabled: Boolean,
)
