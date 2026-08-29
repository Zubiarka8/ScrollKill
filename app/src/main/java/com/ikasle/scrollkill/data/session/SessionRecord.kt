package com.ikasle.scrollkill.data.session

import com.ikasle.scrollkill.detection.DetectionResult.Surface

/**
 * A persisted session as read back by callers (ViewModel later). Epoch-based and typed on
 * [Surface], unlike the transient monotonic [com.ikasle.scrollkill.session.Session] the
 * tracker emits and unlike the storage-shaped [SessionEntity].
 */
data class SessionRecord(
    val id: Long,
    val packageName: String,
    val surface: Surface,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long,
    val detectionCount: Int,
    val interventionCount: Int,
)

internal fun SessionEntity.toRecord() = SessionRecord(
    id = id,
    packageName = packageName,
    surface = runCatching { Surface.valueOf(surface) }.getOrDefault(Surface.UNKNOWN),
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    durationMs = durationMs,
    detectionCount = detectionCount,
    interventionCount = interventionCount,
)
