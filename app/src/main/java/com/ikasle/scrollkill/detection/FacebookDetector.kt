package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Signal
import com.ikasle.scrollkill.detection.DetectionResult.Surface

/**
 * Detects Facebook's two infinite surfaces: the home News Feed ([Surface.FEED]) and the
 * Reels player ([Surface.SHORT_VIDEO]). Covers the main app `com.facebook.katana` and
 * Facebook Lite `com.facebook.lite`.
 *
 * Same confidence-based, multi-signal approach as [InstagramDetector] and the other
 * detectors: the package name alone never reaches [MATCH_THRESHOLD], so at least two
 * independent UI cues must agree. This detector scores the feed and the reels surface
 * separately from the same snapshot and reports whichever is stronger; a tie goes to
 * [Surface.SHORT_VIDEO] as the more specific state. Detection and blocking stay separate:
 * this class only returns a [DetectionResult].
 *
 * The token lists are best-effort against real Facebook builds and are expected to drift;
 * Facebook Lite has a different UI and may need its own lists once verified on a device.
 */
class FacebookDetector : AppDetector {

    override val targetPackage: String = PACKAGE

    override val targetPackages: Set<String> = setOf(PACKAGE, PACKAGE_LITE)

    override fun detect(snapshot: ScreenSnapshot): DetectionResult {
        if (snapshot.packageName !in targetPackages) return DetectionResult.none(snapshot.packageName)

        val feed = score(
            snapshot,
            FEED_VIEW_ID_TOKENS,
            FEED_CLASS_TOKENS,
            FEED_CONTENT_DESC_TOKENS,
        )
        val reels = score(
            snapshot,
            REELS_VIEW_ID_TOKENS,
            REELS_CLASS_TOKENS,
            REELS_CONTENT_DESC_TOKENS,
        )

        // Tie goes to reels: the Reels player is the more specific state.
        val (surface, match) = if (reels.confidence >= feed.confidence) {
            Surface.SHORT_VIDEO to reels
        } else {
            Surface.FEED to feed
        }

        return if (match.confidence >= MATCH_THRESHOLD) {
            DetectionResult(
                packageName = snapshot.packageName,
                surface = surface,
                confidence = match.confidence,
                matchedSignals = match.signals,
            )
        } else {
            DetectionResult.none(snapshot.packageName)
        }
    }

    /** One surface's score: package weight plus whichever of the three cue groups fired. */
    private fun score(
        snapshot: ScreenSnapshot,
        viewIdTokens: List<String>,
        classTokens: List<String>,
        contentDescTokens: List<String>,
    ): Score {
        var confidence = WEIGHT_PACKAGE
        val signals = mutableSetOf(Signal.PACKAGE_NAME)

        if (snapshot.viewIds.containsAnyToken(viewIdTokens)) {
            confidence += WEIGHT_VIEW_ID
            signals += Signal.VIEW_ID
        }
        if (snapshot.classNames.containsAnyToken(classTokens)) {
            confidence += WEIGHT_CLASS_NAME
            signals += Signal.CLASS_NAME
        }
        if (snapshot.contentDescriptions.containsAnyToken(contentDescTokens)) {
            confidence += WEIGHT_CONTENT_DESC
            signals += Signal.CONTENT_DESCRIPTION
        }

        return Score(confidence.coerceAtMost(1f), signals)
    }

    private data class Score(val confidence: Float, val signals: Set<Signal>)

    private companion object {
        const val PACKAGE = "com.facebook.katana"
        const val PACKAGE_LITE = "com.facebook.lite"

        // Scoring: PACKAGE alone (0.10) is below MATCH_THRESHOLD, and so is any single
        // non-package signal added to it, so a match needs at least two real cues.
        const val WEIGHT_PACKAGE = 0.10f
        const val WEIGHT_VIEW_ID = 0.45f
        const val WEIGHT_CLASS_NAME = 0.25f
        const val WEIGHT_CONTENT_DESC = 0.25f
        const val MATCH_THRESHOLD = 0.60f

        // TODO(facebook): verify against current Facebook build; expected to drift.
        val FEED_VIEW_ID_TOKENS = listOf("news_feed", "newsfeed_recycler_view", "feed_recycler", "newsfeed_container")

        // TODO(facebook): verify against current Facebook build; expected to drift.
        val FEED_CLASS_TOKENS = listOf("NewsFeedFragment", "FeedFragment")

        // TODO(facebook): verify against current Facebook build; expected to drift.
        val FEED_CONTENT_DESC_TOKENS = listOf("What's on your mind", "News Feed", "Stories tray")

        // TODO(facebook): verify against current Facebook build; expected to drift.
        val REELS_VIEW_ID_TOKENS = listOf("reels_viewer", "video_home_reels", "reels_tab", "reels_root")

        // TODO(facebook): verify against current Facebook build; expected to drift.
        val REELS_CLASS_TOKENS = listOf("ReelsViewerFragment", "ReelsPlayerFragment")

        // TODO(facebook): verify against current Facebook build; expected to drift.
        val REELS_CONTENT_DESC_TOKENS = listOf("Reel by", "Reels", "Play reel")
    }
}
