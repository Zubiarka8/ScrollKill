package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Signal
import com.ikasle.scrollkill.detection.DetectionResult.Surface

/**
 * Detects when YouTube is showing the Shorts player, an infinite vertical-video feed,
 * reported as [Surface.SHORT_VIDEO].
 *
 * Same confidence-based, multi-signal approach as [InstagramDetector]: the package name
 * alone never reaches [MATCH_THRESHOLD], so at least two independent UI cues must agree
 * before this reports a match. Detection and blocking stay separate: this class only
 * returns a [DetectionResult].
 *
 * The token lists are matched against real YouTube builds and are expected to drift;
 * they live here so this detector can be updated on its own.
 */
class YouTubeShortsDetector : AppDetector {

    override val targetPackage: String = PACKAGE

    override fun detect(snapshot: ScreenSnapshot): DetectionResult {
        if (snapshot.packageName != PACKAGE) return DetectionResult.none(snapshot.packageName)

        var confidence = WEIGHT_PACKAGE
        val signals = mutableSetOf(Signal.PACKAGE_NAME)

        if (snapshot.viewIds.containsAnyToken(SHORTS_VIEW_ID_TOKENS)) {
            confidence += WEIGHT_VIEW_ID
            signals += Signal.VIEW_ID
        }
        if (snapshot.classNames.containsAnyToken(SHORTS_CLASS_TOKENS)) {
            confidence += WEIGHT_CLASS_NAME
            signals += Signal.CLASS_NAME
        }
        if (snapshot.contentDescriptions.containsAnyToken(SHORTS_CONTENT_DESC_TOKENS)) {
            confidence += WEIGHT_CONTENT_DESC
            signals += Signal.CONTENT_DESCRIPTION
        }
        if (snapshot.texts.containsAnyToken(SHORTS_TEXT_TOKENS)) {
            confidence += WEIGHT_TEXT
            signals += Signal.TEXT
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
        const val PACKAGE = "com.google.android.youtube"

        // Scoring: PACKAGE alone (0.10) is below MATCH_THRESHOLD, and so is any single
        // non-package signal added to it, so a match needs at least two real cues.
        const val WEIGHT_PACKAGE = 0.10f
        const val WEIGHT_VIEW_ID = 0.45f
        const val WEIGHT_CLASS_NAME = 0.25f
        const val WEIGHT_CONTENT_DESC = 0.25f
        const val WEIGHT_TEXT = 0.25f
        const val MATCH_THRESHOLD = 0.60f

        // TODO(youtube): verify against current YouTube build; expected to drift.
        val SHORTS_VIEW_ID_TOKENS = listOf("reel_recycler", "reel_player_page", "reel_watch_pager", "shorts_container")

        // TODO(youtube): verify against current YouTube build; expected to drift.
        val SHORTS_CLASS_TOKENS = listOf("ReelWatchFragment", "ShortsPlayerFragment")

        // TODO(youtube): verify against current YouTube build; expected to drift.
        val SHORTS_CONTENT_DESC_TOKENS = listOf("Shorts player", "Short number", "Shorts feed")

        // No text-only label identified yet for this surface (docs/maintenance/detector-token-
        // recheck.md gap B-2 flags the other three detectors' CONTENT_DESCRIPTION tokens as
        // likely text; none of these three were called out the same way). Left empty rather
        // than guessing new tokens.
        val SHORTS_TEXT_TOKENS = emptyList<String>()
    }
}
