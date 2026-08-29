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
    fun `home feed snapshot is not detected as Reels`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf("com.instagram.android:id/feed_timeline_recycler_view"),
                classNames = setOf("androidx.recyclerview.widget.RecyclerView"),
                contentDescriptions = listOf("Photo by another.user", "Like"),
            ),
        )

        assertFalse(result.isMatch)
    }
}
