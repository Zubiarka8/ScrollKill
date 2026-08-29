package com.ikasle.scrollkill.ui.home

/** Everything the home screen renders. Produced by [HomeViewModel]. */
data class HomeUiState(
    val loading: Boolean = true,
    val serviceEnabled: Boolean = false,
    val interveneEnabled: Boolean = true,
    /** Total feed time since local midnight across all apps, preformatted. */
    val todayTotalDuration: String = "",
    /** Per-app usage since local midnight with daily-limit progress, most time first. */
    val todayApps: List<TodayAppUi> = emptyList(),
    /** Label of the active stats window, e.g. "Last 7 days". */
    val windowLabel: String = "",
    /** Total watched time across all apps in the window, preformatted. */
    val totalDuration: String = "",
    /** Per-app usage over the stats window, already sorted by time spent (most first). */
    val apps: List<AppUsageUi> = emptyList(),
)

data class AppUsageUi(
    val packageName: String,
    val displayName: String,
    val duration: String,
    val sessionCount: Int,
    val interventionCount: Int,
)

data class TodayAppUi(
    val packageName: String,
    val displayName: String,
    /** Feed time today, preformatted. */
    val usedToday: String,
    /** "12m / 30m", "12m / 30m - over", or "No limit set" when no budget applies. */
    val limitCaption: String,
    /** Bar fill 0f..1f, or null when no daily limit applies (no bar is drawn). */
    val progress: Float?,
    /** Today's usage has reached or passed the daily budget. */
    val overLimit: Boolean,
)
