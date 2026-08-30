package com.ikasle.scrollkill.service

import android.view.accessibility.AccessibilityEvent

/**
 * The EventFilter stage of the detection pipeline (CLAUDE.md architecture), pulled out of
 * [ScrollKillAccessibilityService] so the drop rules are unit-testable without the framework.
 *
 * Applies, in order, the cheap early-outs that run on the accessibility callback thread for
 * every event:
 *  1. drop events with no package name;
 *  2. drop packages outside the currently observed set;
 *  3. drop event types other than window state / window content changes;
 *  4. debounce bursts per package ([debounceMs]).
 *
 * Only [AccessibilityEvent.getEventType] is read, and the caller passes it in, so this class
 * never touches an [AccessibilityEvent] instance. Not thread-safe: [evaluate] is only ever
 * called from the single accessibility callback thread, like the map it guards.
 */
class EventFilter(private val debounceMs: Long = DEFAULT_DEBOUNCE_MS) {

    /** Last accepted event time per package, in the caller's clock (uptime millis). */
    private val lastAcceptedMs = HashMap<String, Long>()

    sealed interface Outcome {
        /** Event is not relevant; do nothing. */
        data object Ignore : Outcome

        /**
         * Event should go down the detection pipeline.
         *
         * @param fromWindowStateChange true for `TYPE_WINDOW_STATE_CHANGED`, false for a
         *   `TYPE_WINDOW_CONTENT_CHANGED`.
         */
        data class Process(val packageName: String, val fromWindowStateChange: Boolean) : Outcome
    }

    /**
     * @param packageName [AccessibilityEvent.getPackageName], may be null.
     * @param eventType [AccessibilityEvent.getEventType].
     * @param watchedPackages packages currently observed; anything else is dropped.
     * @param nowMs monotonic now, e.g. `SystemClock.uptimeMillis()` (millis since boot).
     *   A package's first event is debounced against 0, so it must be &gt;= [debounceMs]
     *   for that event to pass; with a real uptime clock this is always true.
     */
    fun evaluate(
        packageName: String?,
        eventType: Int,
        watchedPackages: Set<String>,
        nowMs: Long,
    ): Outcome {
        if (packageName == null || packageName !in watchedPackages) return Outcome.Ignore

        val fromWindowStateChange = when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> true
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> false
            else -> return Outcome.Ignore
        }

        if (nowMs - (lastAcceptedMs[packageName] ?: 0L) < debounceMs) return Outcome.Ignore
        lastAcceptedMs[packageName] = nowMs

        return Outcome.Process(packageName, fromWindowStateChange)
    }

    /** Forget all per-package debounce state. */
    fun clear() = lastAcceptedMs.clear()

    private companion object {
        const val DEFAULT_DEBOUNCE_MS = 250L
    }
}
