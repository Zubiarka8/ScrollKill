package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Signal
import com.ikasle.scrollkill.detection.DetectionResult.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookDetectorTest {

    private val detector = FacebookDetector()

    // Representative synthetic values that match the detector's token lists. Not
    // guaranteed to be real Facebook strings; these tests exercise the scoring logic.
    private val feedViewId = "com.facebook.katana:id/newsfeed_recycler_view"
    private val feedClass = "com.facebook.katana.feed.NewsFeedFragment"
    private val feedContentDesc = "What's on your mind, Arkaitz?"

    private val reelsViewId = "com.facebook.katana:id/reels_viewer_root"
    private val reelsClass = "com.facebook.katana.reels.ReelsViewerFragment"
    private val reelsContentDesc = "Reel by another.user"

    @Test
    fun `ignores non-Facebook packages`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf(feedViewId),
                contentDescriptions = listOf(feedContentDesc),
            ),
        )

        assertFalse(result.isMatch)
        assertEquals(Surface.UNKNOWN, result.surface)
    }

    @Test
    fun `empty Facebook snapshot is not a match`() {
        val result = detector.detect(ScreenSnapshot(packageName = "com.facebook.katana"))

        assertFalse(result.isMatch)
        assertEquals(Surface.UNKNOWN, result.surface)
    }

    @Test
    fun `package plus a single feed signal stays below threshold`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.facebook.katana",
                viewIds = setOf(feedViewId),
            ),
        )

        assertFalse(result.isMatch)
    }

    @Test
    fun `News Feed viewId and contentDescription detect FEED`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.facebook.katana",
                viewIds = setOf(feedViewId),
                contentDescriptions = listOf(feedContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.FEED, result.surface)
        assertTrue("confidence was ${result.confidence}", result.confidence >= 0.60f)
        assertTrue(
            result.matchedSignals.containsAll(setOf(Signal.VIEW_ID, Signal.CONTENT_DESCRIPTION)),
        )
    }

    @Test
    fun `feed viewId and text (no contentDescription) still detect FEED`() {
        // "What's on your mind" is the composer hint: almost certainly android:text, not
        // contentDescription (see docs/maintenance/detector-token-recheck.md gap B-2).
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.facebook.katana",
                viewIds = setOf(feedViewId),
                texts = listOf(feedContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.FEED, result.surface)
        assertTrue("confidence was ${result.confidence}", result.confidence >= 0.60f)
        assertTrue(result.matchedSignals.containsAll(setOf(Signal.VIEW_ID, Signal.TEXT)))
    }

    @Test
    fun `Reels viewId and contentDescription detect SHORT_VIDEO`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.facebook.katana",
                viewIds = setOf(reelsViewId),
                contentDescriptions = listOf(reelsContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.SHORT_VIDEO, result.surface)
        assertTrue("confidence was ${result.confidence}", result.confidence >= 0.60f)
    }

    @Test
    fun `detector also fires for Facebook Lite`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.facebook.lite",
                viewIds = setOf("com.facebook.lite:id/news_feed"),
                contentDescriptions = listOf(feedContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.FEED, result.surface)
        assertEquals("com.facebook.lite", result.packageName)
    }

    @Test
    fun `reels cues outscore feed cues when both are present`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.facebook.katana",
                viewIds = setOf(reelsViewId),
                classNames = setOf(reelsClass, feedClass),
                contentDescriptions = listOf(reelsContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.SHORT_VIDEO, result.surface)
    }

    @Test
    fun `confidence is clamped to 1 when every signal fires`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.facebook.katana",
                viewIds = setOf(feedViewId, reelsViewId),
                classNames = setOf(feedClass, reelsClass),
                contentDescriptions = listOf(feedContentDesc, reelsContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertTrue("confidence was ${result.confidence}", result.confidence <= 1.0f)
    }

    @Test
    fun `profile screen snapshot is not detected`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.facebook.katana",
                viewIds = setOf("com.facebook.katana:id/timeline_recycler_view"),
                classNames = setOf("androidx.recyclerview.widget.RecyclerView"),
                contentDescriptions = listOf("Profile photo", "Edit public info"),
            ),
        )

        assertFalse(result.isMatch)
    }
}
