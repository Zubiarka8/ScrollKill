package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Signal
import com.ikasle.scrollkill.detection.DetectionResult.Surface

/**
 * Detects when Instagram is showing the Reels player, an infinite vertical-video feed,
 * reported as [Surface.SHORT_VIDEO].
 *
 * Confidence-based and multi-signal on purpose (CLAUDE.md detection rules): the package
 * name alone never reaches [MATCH_THRESHOLD], so at least two independent UI cues must
 * agree before this reports a match. Detection and blocking stay separate: this class
 * only returns a [DetectionResult].
 *
 * The token lists are matched against real Instagram builds and are expected to drift;
 * they live here so this detector can be updated on its own.
 */
class InstagramDetector : AppDetector {

    override val targetPackage: String = PACKAGE

    override fun detect(snapshot: ScreenSnapshot): DetectionResult {
        if (snapshot.packageName != PACKAGE) return DetectionResult.none(snapshot.packageName)

        var confidence = WEIGHT_PACKAGE
        val signals = mutableSetOf(Signal.PACKAGE_NAME)

        if (snapshot.viewIds.anyContains(REELS_VIEW_ID_TOKENS)) {
            confidence += WEIGHT_VIEW_ID
            signals += Signal.VIEW_ID
        }
        if (snapshot.classNames.anyContains(REELS_CLASS_TOKENS)) {
            confidence += WEIGHT_CLASS_NAME
            signals += Signal.CLASS_NAME
        }
        if (snapshot.contentDescriptions.anyContains(REELS_CONTENT_DESC_TOKENS)) {
            confidence += WEIGHT_CONTENT_DESC
            signals += Signal.CONTENT_DESCRIPTION
        }

        confidence = confidence.coerceAtMost(1f)

        return if (confidence >= MATCH_THRESHOLD) {
            DetectionResult(
                packageName = PACKAGE,
                surface = Surface.SHORT_VIDEO,
                confidence = confidence,
                matchedSignals = signals,
            )
        } else {
            DetectionResult.none(snapshot.packageName)
        }
    }

    private companion object {
        const val PACKAGE = "com.instagram.android"

        // Scoring: PACKAGE alone (0.10) is below MATCH_THRESHOLD, and so is any single
        // non-package signal added to it, so a match needs at least two real cues.
        const val WEIGHT_PACKAGE = 0.10f
        const val WEIGHT_VIEW_ID = 0.45f
        const val WEIGHT_CLASS_NAME = 0.25f
        const val WEIGHT_CONTENT_DESC = 0.25f
        const val MATCH_THRESHOLD = 0.60f

        // TODO(instagram): verify against current Instagram build; expected to drift.
        val REELS_VIEW_ID_TOKENS = listOf("clips_viewer", "reels_tray", "reel_feed_timeline")

        // TODO(instagram): verify against current Instagram build; expected to drift.
        val REELS_CLASS_TOKENS = listOf("ClipsViewerFragment", "ReelViewerFragment")

        // TODO(instagram): verify against current Instagram build; expected to drift.
        val REELS_CONTENT_DESC_TOKENS = listOf("Reel by", "Audio page", "Like number")

        private fun Iterable<String>.anyContains(tokens: List<String>): Boolean =
            any { value -> tokens.any { value.contains(it, ignoreCase = true) } }
    }
}
