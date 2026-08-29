package com.ikasle.scrollkill.ui.home

/** Everything the home screen renders. Produced by [HomeViewModel]. */
data class HomeUiState(
    val loading: Boolean = true,
    val serviceEnabled: Boolean = false,
    val interveneEnabled: Boolean = true,
    /** Label of the active stats window, e.g. "Last 7 days". */
    val windowLabel: String = "",
    /** Total watched time across all apps in the window, preformatted. */
    val totalDuration: String = "",
    /** Per-app usage, already sorted by time spent (most first). */
    val apps: List<AppUsageUi> = emptyList(),
)

data class AppUsageUi(
    val packageName: String,
    val displayName: String,
    val duration: String,
    val sessionCount: Int,
    val interventionCount: Int,
)
