package com.ikasle.scrollkill.ui.home

/**
 * Compact human duration: "0s", "45s", "12m", "3h", "3h 7m". Sub-second precision is not
 * useful for usage stats, so everything rounds down to whole seconds/minutes.
 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0s"
    val totalSeconds = ms / 1000
    if (totalSeconds < 60) return "${totalSeconds}s"
    val totalMinutes = totalSeconds / 60
    if (totalMinutes < 60) return "${totalMinutes}m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
}
