package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Signal
import com.ikasle.scrollkill.detection.DetectionResult.Surface

/**
 * Detects Instagram's three infinite surfaces: the Reels player ([Surface.SHORT_VIDEO]),
 * the home feed ([Surface.FEED]) and the Explore grid ([Surface.EXPLORE]).
 *
 * Same confidence-based, multi-signal approach as [FacebookDetector] (CLAUDE.md detection
 * rules): the package name alone never reaches [MATCH_THRESHOLD], so at least two
 * independent UI cues must agree. Each surface is scored separately from the same snapshot
 * and the strongest one wins; ties resolve most-specific-first
 * (Reels, then Explore, then the home feed). Detection and blocking stay separate: this
 * class only returns a [DetectionResult].
 *
 * The token lists are best-effort against real Instagram builds and are expected to drift;
 * they live here so this detector can be updated on its own.
 */
class InstagramDetector : AppDetector {

    override val targetPackage: String = PACKAGE

    override fun detect(snapshot: ScreenSnapshot): DetectionResult {
        if (snapshot.packageName != PACKAGE) return DetectionResult.none(snapshot.packageName)

        // Priority order: the first surface with the top score wins a tie, so keep the
        // more specific surfaces ahead of the broad home feed.
        val scored = listOf(
            Surface.SHORT_VIDEO to score(snapshot, REELS_VIEW_ID_TOKENS, REELS_CLASS_TOKENS, REELS_CONTENT_DESC_TOKENS),
            Surface.EXPLORE to score(snapshot, EXPLORE_VIEW_ID_TOKENS, EXPLORE_CLASS_TOKENS, EXPLORE_CONTENT_DESC_TOKENS),
            Surface.FEED to score(snapshot, FEED_VIEW_ID_TOKENS, FEED_CLASS_TOKENS, FEED_CONTENT_DESC_TOKENS),
        )
        val (surface, best) = scored.maxByOrNull { it.second.confidence }!!

        return if (best.confidence >= MATCH_THRESHOLD) {
            DetectionResult(
                packageName = PACKAGE,
                surface = surface,
                confidence = best.confidence,
                matchedSignals = best.signals,
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
        const val PACKAGE = "com.instagram.android"

        // Scoring: PACKAGE alone (0.10) is below MATCH_THRESHOLD, and so is any single
        // non-package signal added to it, so a match needs at least two real cues.
        const val WEIGHT_PACKAGE = 0.10f
        const val WEIGHT_VIEW_ID = 0.45f
        const val WEIGHT_CLASS_NAME = 0.25f
        const val WEIGHT_CONTENT_DESC = 0.25f
        const val MATCH_THRESHOLD = 0.60f

        // TODO(instagram): verify all token lists against current Instagram build; expected to drift.
        val REELS_VIEW_ID_TOKENS = listOf("clips_viewer", "reel_feed_timeline", "reels_viewer")
        val REELS_CLASS_TOKENS = listOf("ClipsViewerFragment", "ReelViewerFragment")
        val REELS_CONTENT_DESC_TOKENS = listOf("Reel by", "Audio page", "Like number")

        val EXPLORE_VIEW_ID_TOKENS = listOf("explore_grid", "explore_recycler_view", "search_and_explore")
        val EXPLORE_CLASS_TOKENS = listOf("ExploreFragment", "ExploreGridFragment", "DiscoverFragment")
        val EXPLORE_CONTENT_DESC_TOKENS = listOf("Search and explore", "Explore", "Trending")

        val FEED_VIEW_ID_TOKENS = listOf("feed_timeline_recycler_view", "main_feed_recycler_view", "feed_recycler_view")
        val FEED_CLASS_TOKENS = listOf("MainFeedFragment", "FeedTimelineFragment")
        val FEED_CONTENT_DESC_TOKENS = listOf("Photo by", "New posts", "Your story", "Suggested for you")
    }
}
