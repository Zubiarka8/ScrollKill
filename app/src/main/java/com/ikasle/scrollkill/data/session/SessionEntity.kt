package com.ikasle.scrollkill.data.session

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One persisted browsing session. The Room-facing counterpart of the transient
 * [com.ikasle.scrollkill.session.Session] the SessionTracker emits.
 *
 * Times are wall-clock epoch milliseconds (the tracker works in monotonic uptime; the
 * repository stamps epoch when it records). [durationMs] is denormalised so per-app
 * "time spent" aggregates do not have to subtract two columns per row. [surface] stores
 * the [com.ikasle.scrollkill.detection.DetectionResult.Surface] name; no TypeConverter.
 *
 * CLAUDE.md privacy: only a package name, a surface label, timestamps and counts. No
 * screen content, ever.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val surface: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long,
    val detectionCount: Int,
    val interventionCount: Int,
)
