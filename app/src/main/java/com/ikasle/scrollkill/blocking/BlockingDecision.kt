package com.ikasle.scrollkill.blocking

import com.ikasle.scrollkill.detection.DetectionResult

/**
 * What [BlockingEngine] concluded for one [DetectionResult].
 *
 * The engine only names the intent; the AccessibilityService performs the action, so
 * actuation can evolve (BACK press today, an overlay later) without touching this type or
 * the engine.
 */
sealed interface BlockingDecision {

    /** Nothing to do. */
    data object None : BlockingDecision

    /** The user should be nudged off [packageName]'s [surface]. */
    data class Intervene(
        val packageName: String,
        val surface: DetectionResult.Surface,
        val confidence: Float,
    ) : BlockingDecision
}
