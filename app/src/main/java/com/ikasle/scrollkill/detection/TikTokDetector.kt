package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Signal
import com.ikasle.scrollkill.detection.DetectionResult.Surface

/**
 * Detects when TikTok is showing its vertical-video feed (the For You / Following tabs),
 * an infinite short-video surface, reported as [Surface.SHORT_VIDEO].
 *
 * Unlike Reels or Shorts, the feed is TikTok's home screen rather than a sub-section, so
 * "feed on screen" is effectively "TikTok open on its main surface". Multi-signal scoring
 * still keeps it from firing on the profile grid, inbox or search: same approach as
 * [InstagramDetector] and [YouTubeShortsDetector], where the package name alone never
 * reaches [MATCH_THRESHOLD] and at least two independent UI cues must agree. Detection and
 * blocking stay separate: this class only returns a [DetectionResult].
 *
 * The token lists are best-effort against real TikTok builds and are expected to drift;
 * they live here so this detector can be updated on its own.
 */
class TikTokDetector : AppDetector {

    override val targetPackage: String = PACKAGE

    override fun detect(snapshot: ScreenSnapshot): DetectionResult {
        if (snapshot.packageName != PACKAGE) return DetectionResult.none(snapshot.packageName)

        var confidence = WEIGHT_PACKAGE
        val signals = mutableSetOf(Signal.PACKAGE_NAME)

        if (snapshot.viewIds.containsAnyToken(FEED_VIEW_ID_TOKENS)) {
            confidence += WEIGHT_VIEW_ID
            signals += Signal.VIEW_ID
        }
        if (snapshot.classNames.containsAnyToken(FEED_CLASS_TOKENS)) {
            confidence += WEIGHT_CLASS_NAME
            signals += Signal.CLASS_NAME
        }
        if (snapshot.contentDescriptions.containsAnyToken(FEED_CONTENT_DESC_TOKENS)) {
            confidence += WEIGHT_CONTENT_DESC
            signals += Signal.CONTENT_DESCRIPTION
        }
        if (snapshot.texts.containsAnyToken(FEED_TEXT_TOKENS)) {
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
        const val PACKAGE = "com.zhiliaoapp.musically"

        // Scoring: PACKAGE alone (0.10) is below MATCH_THRESHOLD, and so is any single
        // non-package signal added to it, so a match needs at least two real cues.
        const val WEIGHT_PACKAGE = 0.10f
        const val WEIGHT_VIEW_ID = 0.45f
        const val WEIGHT_CLASS_NAME = 0.25f
        const val WEIGHT_CONTENT_DESC = 0.25f
        const val WEIGHT_TEXT = 0.25f
        const val MATCH_THRESHOLD = 0.60f

        // TODO(tiktok): verify against current TikTok build; expected to drift.
        val FEED_VIEW_ID_TOKENS = listOf("feed_recycler_view", "video_feed", "aweme_feed", "detail_feed")

        // TODO(tiktok): verify against current TikTok build; expected to drift.
        val FEED_CLASS_TOKENS = listOf("FeedRecommendFragment", "MainFragment", "FeedFragment", "VideoViewHolder")

        // TODO(tiktok): verify against current TikTok build; expected to drift.
        val FEED_CONTENT_DESC_TOKENS = listOf("For You", "Following", "Like number", "Speed dial")

        // "For You" / "Following" are the top-tab labels: almost certainly android:text on
        // the tab bar, not contentDescription (docs/maintenance/detector-token-recheck.md
        // gap B-2). Checked against snapshot.texts as well so a real capture where they only
        // land as text still crosses MATCH_THRESHOLD alongside VIEW_ID. Also localized, so
        // this still misses non-English devices - out of scope for this fix.
        val FEED_TEXT_TOKENS = listOf("For You", "Following")
    }
}
