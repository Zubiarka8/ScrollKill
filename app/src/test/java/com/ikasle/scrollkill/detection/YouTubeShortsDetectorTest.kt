package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Signal
import com.ikasle.scrollkill.detection.DetectionResult.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeShortsDetectorTest {

    private val detector = YouTubeShortsDetector()

    // Representative synthetic values that match the detector's token lists. Not
    // guaranteed to be real YouTube strings; these tests exercise the scoring logic.
    private val shortsViewId = "com.google.android.youtube:id/reel_watch_pager"
    private val shortsClass = "com.google.android.apps.youtube.app.shorts.ReelWatchFragment"
    private val shortsContentDesc = "Shorts player"

    @Test
    fun `ignores non-YouTube packages`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf(shortsViewId),
                contentDescriptions = listOf(shortsContentDesc),
            ),
        )

        assertFalse(result.isMatch)
        assertEquals(Surface.UNKNOWN, result.surface)
    }

    @Test
    fun `empty YouTube snapshot is not a match`() {
        val result = detector.detect(ScreenSnapshot(packageName = "com.google.android.youtube"))

        assertFalse(result.isMatch)
        assertEquals(Surface.UNKNOWN, result.surface)
    }

    @Test
    fun `package plus a single Shorts signal stays below threshold`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.google.android.youtube",
                viewIds = setOf(shortsViewId),
            ),
        )

        assertFalse(result.isMatch)
    }

    @Test
    fun `Shorts viewId and contentDescription together detect SHORT_VIDEO`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.google.android.youtube",
                viewIds = setOf(shortsViewId),
                contentDescriptions = listOf(shortsContentDesc),
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
                packageName = "com.google.android.youtube",
                viewIds = setOf(shortsViewId),
                classNames = setOf(shortsClass),
                contentDescriptions = listOf(shortsContentDesc),
            ),
        )

        assertTrue(result.isMatch)
        assertTrue("confidence was ${result.confidence}", result.confidence <= 1.0f)
    }

    @Test
    fun `watch page snapshot is not detected as Shorts`() {
        val result = detector.detect(
            ScreenSnapshot(
                packageName = "com.google.android.youtube",
                viewIds = setOf("com.google.android.youtube:id/watch_player"),
                classNames = setOf("com.google.android.apps.youtube.app.watch.WatchWhileActivity"),
                contentDescriptions = listOf("Play video", "Like this video"),
            ),
        )

        assertFalse(result.isMatch)
    }
}
