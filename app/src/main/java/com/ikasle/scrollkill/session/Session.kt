package com.ikasle.scrollkill.session

import com.ikasle.scrollkill.detection.DetectionResult

/**
 * One continuous engagement with a watched app's infinite-content surface, aggregated in
 * memory by [SessionTracker].
 *
 * The "SessionTracker" stage of the CLAUDE.md pipeline hands these to the Repository once
 * that exists; for now the AccessibilityService just logs them. Deliberately minimal
 * (CLAUDE.md privacy rules): a package name, a surface enum, monotonic timestamps and two
 * counters. No screen content, no [android.view.accessibility.AccessibilityNodeInfo].
 *
 * All times are readings from a monotonic clock (SystemClock.uptimeMillis()); wall-clock
 * stamping is a persistence concern, not this type's.
 */
data class Session(
    /** Package the engagement happened in, e.g. "com.instagram.android". */
    val packageName: String,
    /** Surface the session ran on. A change of surface starts a new session. */
    val surface: DetectionResult.Surface,
    /** Monotonic time of the first tracked detection. */
    val startedAtMs: Long,
    /** Monotonic time of the last tracked detection (not the moment the session closed). */
    val endedAtMs: Long,
    /** Tracked-surface detections observed during the session. Always >= 1. */
    val detectionCount: Int,
    /** [com.ikasle.scrollkill.blocking.BlockingDecision.Intervene] events during the session. */
    val interventionCount: Int,
) {
    init {
        require(endedAtMs >= startedAtMs) { "endedAtMs ($endedAtMs) precedes startedAtMs ($startedAtMs)" }
        require(detectionCount >= 1) { "detectionCount must be >= 1, was $detectionCount" }
        require(interventionCount >= 0) { "interventionCount must be >= 0, was $interventionCount" }
    }

    val durationMs: Long get() = endedAtMs - startedAtMs
}
