package com.ikasle.scrollkill.session

import com.ikasle.scrollkill.blocking.BlockingDecision
import com.ikasle.scrollkill.detection.DetectionResult
import com.ikasle.scrollkill.detection.DetectionResult.Surface

/**
 * The "SessionTracker" stage of the CLAUDE.md pipeline: folds the per-event stream of
 * [DetectionResult] + [BlockingDecision] into [Session] records, entirely in memory.
 *
 * Pure and framework-free (unit-testable). It performs no I/O and no persistence -
 * aggregating in memory before the Repository stage is a CLAUDE.md battery rule. A session
 * is one continuous engagement with a single ([packageName], [surface]) pair among
 * [trackedSurfaces]; it ends when the user leaves that surface, switches app or surface,
 * goes idle for [idleTimeoutMs], or the service tears down. Sessions shorter than
 * [minSessionDurationMs] are discarded as noise.
 *
 * Not thread-safe: [track] is meant to be called from the single accessibility callback
 * thread. Only one app is ever in the foreground, so at most one session is open at a time.
 */
class SessionTracker(
    private val trackedSurfaces: Set<Surface> = DEFAULT_TRACKED_SURFACES,
    minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    minSessionDurationMs: Long = DEFAULT_MIN_SESSION_DURATION_MS,
) {

    /**
     * Confidence floor, idle timeout and minimum session length, seeded from the constructor
     * defaults and then kept in sync with the settings repository by the AccessibilityService
     * collector (a different thread). Plain-value swaps behind [Volatile], matching
     * BlockingEngine's settings-fed fields; a [track] read that is one settings emission stale
     * is harmless.
     */
    @Volatile
    var minConfidence: Float = minConfidence

    @Volatile
    var idleTimeoutMs: Long = idleTimeoutMs

    @Volatile
    var minSessionDurationMs: Long = minSessionDurationMs

    private class Open(
        val packageName: String,
        val surface: Surface,
        val startedAtMs: Long,
        var lastEventMs: Long,
        var detectionCount: Int,
        var interventionCount: Int,
    )

    private var open: Open? = null

    /**
     * Feed one evaluated event. Returns a [Session] only when this event closed one that
     * lasted at least [minSessionDurationMs]; otherwise null.
     *
     * @param nowMs a monotonic clock reading (e.g. SystemClock.uptimeMillis()).
     */
    fun track(result: DetectionResult, decision: BlockingDecision, nowMs: Long): Session? {
        val idleClosed = closeIfIdle(nowMs)

        val engaged = result.isMatch &&
            result.surface in trackedSurfaces &&
            result.confidence >= minConfidence

        if (!engaged) {
            return closeOpen() ?: idleClosed
        }

        val current = open
        if (current == null) {
            open = newSession(result, decision, nowMs)
            return idleClosed
        }

        if (current.packageName == result.packageName && current.surface == result.surface) {
            current.lastEventMs = nowMs
            current.detectionCount++
            if (decision is BlockingDecision.Intervene) current.interventionCount++
            return idleClosed
        }

        // Different package or surface: the previous session is over.
        val closed = closeOpen()
        open = newSession(result, decision, nowMs)
        return closed ?: idleClosed
    }

    /**
     * Close any open session (ended at its last event) and return it if it is long enough.
     * For service teardown / future Repository hand-off.
     */
    fun flush(@Suppress("UNUSED_PARAMETER") nowMs: Long): Session? = closeOpen()

    /** Drop the open session without emitting it (e.g. on service teardown). */
    fun reset() {
        open = null
    }

    private fun closeIfIdle(nowMs: Long): Session? {
        val current = open ?: return null
        if (nowMs - current.lastEventMs <= idleTimeoutMs) return null
        return closeOpen()
    }

    private fun closeOpen(): Session? {
        val current = open ?: return null
        open = null
        if (current.lastEventMs - current.startedAtMs < minSessionDurationMs) return null
        return Session(
            packageName = current.packageName,
            surface = current.surface,
            startedAtMs = current.startedAtMs,
            endedAtMs = current.lastEventMs,
            detectionCount = current.detectionCount,
            interventionCount = current.interventionCount,
        )
    }

    private fun newSession(result: DetectionResult, decision: BlockingDecision, nowMs: Long) = Open(
        packageName = result.packageName,
        surface = result.surface,
        startedAtMs = nowMs,
        lastEventMs = nowMs,
        detectionCount = 1,
        interventionCount = if (decision is BlockingDecision.Intervene) 1 else 0,
    )

    private companion object {
        // Confidence floor, idle timeout and minimum duration are settings-driven (see the
        // vars above and data.settings.DetectionPolicy); these constants are the shipped
        // defaults.
        // TODO(settings): trackedSurfaces is still fixed - needs a picker UI before it moves.
        val DEFAULT_TRACKED_SURFACES = setOf(Surface.FEED, Surface.SHORT_VIDEO, Surface.EXPLORE)
        const val DEFAULT_MIN_CONFIDENCE = 0.60f
        const val DEFAULT_IDLE_TIMEOUT_MS = 15_000L
        const val DEFAULT_MIN_SESSION_DURATION_MS = 1_000L
    }
}
