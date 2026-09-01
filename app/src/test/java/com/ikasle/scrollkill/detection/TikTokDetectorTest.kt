package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Signal
import com.ikasle.scrollkill.detection.DetectionResult.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokDetectorTest {

    private val detector = TikTokDetector()

    // Representative synthetic values that match the detector's token lists. Not
    // guaranteed to be real TikTok strings; these tests exercise the scoring logic.
    private val feedViewId = "com.zhiliaoapp.musically:id/feed_recycler_view"
    private val feedClass = "com.ss.android.ugc.aweme.feed.ui.FeedRecommendFragment"
    private val feedContentDesc = "For You"

    @Test
    fun `ignores non-TikTok packages`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.google.android.youtube",
                viewIds = setOf(feedViewId),
                contentDescriptions = listOf(feedContentDesc),
            ),
        )

        assertFalse(result.isMatch)
        assertEquals(Surface.UNKNOWN, result.surface)
    }

    @Test
    fun `empty TikTok snapshot is not a match`() {
        val result = detector.detect(ScreenSnapshot(packageName = "com.zhiliaoapp.musically"))

        assertFalse(result.isMatch)
        assertEquals(Surface.UNKNOWN, result.surface)
    }

    @Test
    fun `package plus a single feed signal stays below threshold`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.zhiliaoapp.musically",
                viewIds = setOf(feedViewId),
            ),
        )

        assertFalse(result.isMatch)
    }

    @Test
    fun `For You feed viewId and contentDescription together detect SHORT_VIDEO`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.zhiliaoapp.musically",
                viewIds = setOf(feedViewId),
                contentDescriptions = listOf(feedContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.SHORT_VIDEO, result.surface)
        assertTrue("confidence was ${result.confidence}", result.confidence >= 0.60f)
        assertTrue(
            result.matchedSignals.containsAll(setOf(Signal.VIEW_ID, Signal.CONTENT_DESCRIPTION)),
        )
    }

    @Test
    fun `For You feed viewId and text (no contentDescription) still detect SHORT_VIDEO`() {
        // Reproduces the "TikTok daily limit never blocks" bug: on real TikTok builds "For
        // You" / "Following" are android:text on the tab bar, not contentDescription, so
        // relying on contentDescriptions alone left this detector capped at 0.55 (never
        // matching). See docs/maintenance/detector-token-recheck.md gap B-2.
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.zhiliaoapp.musically",
                viewIds = setOf(feedViewId),
                texts = listOf(feedContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.SHORT_VIDEO, result.surface)
        assertTrue("confidence was ${result.confidence}", result.confidence >= 0.60f)
        assertTrue(result.matchedSignals.containsAll(setOf(Signal.VIEW_ID, Signal.TEXT)))
    }

    @Test
    fun `confidence is clamped to 1 when every signal fires`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.zhiliaoapp.musically",
                viewIds = setOf(feedViewId),
                classNames = setOf(feedClass),
                contentDescriptions = listOf(feedContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertTrue("confidence was ${result.confidence}", result.confidence <= 1.0f)
    }

    @Test
    fun `profile grid snapshot is not detected as feed`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.zhiliaoapp.musically",
                viewIds = setOf("com.zhiliaoapp.musically:id/profile_video_grid"),
                classNames = setOf("androidx.recyclerview.widget.RecyclerView"),
                contentDescriptions = listOf("Followers count", "Edit profile"),
            ),
        )

        assertFalse(result.isMatch)
    }
}
