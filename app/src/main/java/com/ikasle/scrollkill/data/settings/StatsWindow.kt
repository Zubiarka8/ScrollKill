package com.ikasle.scrollkill.data.settings

/** Rolling window the home screen aggregates usage over. */
enum class StatsWindow(val durationMs: Long, val label: String) {
    LAST_24_HOURS(24L * 60 * 60 * 1000, "Last 24 hours"),
    LAST_7_DAYS(7L * 24 * 60 * 60 * 1000, "Last 7 days"),
    LAST_30_DAYS(30L * 24 * 60 * 60 * 1000, "Last 30 days"),
}
