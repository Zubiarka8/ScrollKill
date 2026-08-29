package com.ikasle.scrollkill.detection

/**
 * Outcome of a single detector evaluating one UI state.
 *
 * Detectors never act on their own: they return a [DetectionResult] and the
 * BlockingEngine decides what to do with it. Detection is confidence-based and
 * must combine several [Signal]s rather than relying on one text match
 * (see CLAUDE.md "Detection rules").
 *
 * Framework-free on purpose so detectors stay unit-testable.
 */
data class DetectionResult(
    /** Package the evaluation ran against, e.g. "com.instagram.android". */
    val packageName: String,
    /** Surface the detector believes is on screen. */
    val surface: Surface,
    /** 0.0 = no match, 1.0 = certain. Callers apply their own threshold. */
    val confidence: Float,
    /** Signals that contributed, kept for debugging and threshold tuning. */
    val matchedSignals: Set<Signal> = emptySet(),
    /** Source event time (SystemClock.uptimeMillis), 0 when synthetic. */
    val timestampMs: Long = 0L,
) {
    init {
        require(confidence in 0f..1f) { "confidence must be in 0..1, was $confidence" }
    }

    val isMatch: Boolean get() = surface != Surface.UNKNOWN && confidence > 0f

    /** Surfaces worth blocking. Extend as per-app detectors are added. */
    enum class Surface {
        UNKNOWN,
        FEED,         // infinite-scroll home feed
        SHORT_VIDEO,  // Reels / Shorts / TikTok-style vertical video
        EXPLORE,      // discovery / explore grid
    }

    /**
     * Node attributes a detector may key on. A detector should weight and
     * combine multiple of these, never trust one alone.
     */
    enum class Signal {
        PACKAGE_NAME,
        WINDOW_STATE,
        CLASS_NAME,
        VIEW_ID,
        TEXT,
        CONTENT_DESCRIPTION,
        NODE_HIERARCHY,
        ACTIONS,
    }

    companion object {
        /** "This detector saw nothing." */
        fun none(packageName: String, timestampMs: Long = 0L): DetectionResult =
            DetectionResult(packageName, Surface.UNKNOWN, 0f, emptySet(), timestampMs)
    }
}
