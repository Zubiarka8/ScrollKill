package com.ikasle.scrollkill.ui.home

import java.util.Calendar
import java.util.TimeZone

/**
 * Epoch millis of the most recent local midnight at or before [nowMs].
 *
 * Uses [Calendar] rather than java.time so it is safe on minSdk 24 without core-library
 * desugaring. [zone] is injectable so tests stay independent of the machine time zone.
 */
internal fun startOfTodayMillis(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val calendar = Calendar.getInstance(zone).apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}
