package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Signal
import com.ikasle.scrollkill.detection.DetectionResult.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramDetectorTest {

    private val detector = InstagramDetector()

    // Representative synthetic values that match the detector's token lists. Not
    // guaranteed to be real Instagram strings; these tests exercise the scoring logic.
    private val reelsViewId = "com.instagram.android:id/clips_viewer_view_pager"
    private val reelsClass = "com.instagram.clips.viewer.ClipsViewerFragment"
    private val reelsContentDesc = "Reel by another.user"

    private val feedViewId = "com.instagram.android:id/feed_timeline_recycler_view"
    private val feedClass = "com.instagram.mainfeed.fragment.MainFeedFragment"
    private val feedContentDesc = "Photo by another.user"

    private val exploreViewId = "com.instagram.android:id/explore_grid"
    private val exploreClass = "com.instagram.explore.fragment.ExploreFragment"
    private val exploreContentDesc = "Search and explore"

    @Test
    fun `ignores non-Instagram packages`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.google.android.youtube",
                viewIds = setOf(reelsViewId),
                contentDescriptions = listOf(reelsContentDesc),
            ),
        )

        assertFalse(result.isMatch)
        assertEquals(Surface.UNKNOWN, result.surface)
    }

    @Test
    fun `empty Instagram snapshot is not a match`() {
        val result = detector.detect(ScreenSnapshot(packageName = "com.instagram.android"))

        assertFalse(result.isMatch)
        assertEquals(Surface.UNKNOWN, result.surface)
    }

    @Test
    fun `package plus a single Reels signal stays below threshold`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf(reelsViewId),
            ),
        )

        assertFalse(result.isMatch)
    }

    @Test
    fun `Reels viewId and contentDescription together detect SHORT_VIDEO`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf(reelsViewId),
                contentDescriptions = listOf(reelsContentDesc),
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
    fun `home feed viewId and contentDescription together detect FEED`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf(feedViewId),
                classNames = setOf("androidx.recyclerview.widget.RecyclerView"),
                contentDescriptions = listOf(feedContentDesc, "Like"),
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
    fun `Explore viewId and class together detect EXPLORE`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf(exploreViewId),
                classNames = setOf(exploreClass),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.EXPLORE, result.surface)
        assertTrue("confidence was ${result.confidence}", result.confidence >= 0.60f)
    }

    @Test
    fun `package plus a single feed signal stays below threshold`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf(feedViewId),
            ),
        )

        assertFalse(result.isMatch)
    }

    @Test
    fun `Reels cues outscore feed cues when both are present`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf(reelsViewId, feedViewId),
                classNames = setOf(reelsClass),
                contentDescriptions = listOf(reelsContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.SHORT_VIDEO, result.surface)
    }

    @Test
    fun `equal feed and explore scores resolve to the more specific EXPLORE`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf(feedViewId, exploreViewId),
                contentDescriptions = listOf(feedContentDesc, exploreContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.EXPLORE, result.surface)
    }

    @Test
    fun `confidence is clamped to 1 when every signal fires`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf(reelsViewId),
                classNames = setOf(reelsClass),
                contentDescriptions = listOf(reelsContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertTrue("confidence was ${result.confidence}", result.confidence <= 1.0f)
    }

    @Test
    fun `profile grid snapshot is not detected`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf("com.instagram.android:id/profile_grid_recycler_view"),
                classNames = setOf("androidx.recyclerview.widget.RecyclerView"),
                contentDescriptions = listOf("Edit profile", "Grid view"),
            ),
        )

        assertFalse(result.isMatch)
    }

    @Test
    fun `direct messages snapshot is not detected`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf("com.instagram.android:id/direct_inbox_recycler_view"),
                classNames = setOf("com.instagram.direct.fragment.DirectInboxFragment"),
                contentDescriptions = listOf("New message", "Active now"),
            ),
        )

        assertFalse(result.isMatch)
    }
}
