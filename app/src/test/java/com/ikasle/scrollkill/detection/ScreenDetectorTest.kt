package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenDetectorTest {

    @Test
    fun `unwatched package returns none`() {
        val screenDetector = ScreenDetector.default()

        val result = screenDetector.detect(ScreenSnapshot(packageName = "com.example.other"))

        assertFalse(result.isMatch)
        assertEquals(Surface.UNKNOWN, result.surface)
        assertEquals("com.example.other", result.packageName)
    }

    @Test
    fun `routes the snapshot to the detector that owns the package`() {
        val canned = DetectionResult(
            packageName = "com.fake.app",
            surface = Surface.FEED,
            confidence = 1f,
        )
        val fake = FakeDetector("com.fake.app", canned)
        val screenDetector = ScreenDetector(listOf(fake))

        val result = screenDetector.detect(ScreenSnapshot(packageName = "com.fake.app"))

        assertSame(canned, result)
        assertEquals(1, fake.calls)
    }

    @Test
    fun `default set routes an Instagram Reels snapshot to SHORT_VIDEO`() {
        val screenDetector = ScreenDetector.default()

        val result = screenDetector.detect(
            ScreenSnapshot(
                packageName = "com.instagram.android",
                viewIds = setOf("com.instagram.android:id/clips_viewer_view_pager"),
                contentDescriptions = listOf("Reel by someone"),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.SHORT_VIDEO, result.surface)
    }

    @Test
    fun `default set routes a Facebook News Feed snapshot to FEED`() {
        val screenDetector = ScreenDetector.default()

        val result = screenDetector.detect(
            ScreenSnapshot(
                packageName = "com.facebook.katana",
                viewIds = setOf("com.facebook.katana:id/newsfeed_recycler_view"),
                contentDescriptions = listOf("What's on your mind?"),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.FEED, result.surface)
    }

    @Test
    fun `default set routes com dot facebook dot lite to the Facebook detector`() {
        val screenDetector = ScreenDetector.default()

        val result = screenDetector.detect(
            ScreenSnapshot(
                packageName = "com.facebook.lite",
                viewIds = setOf("com.facebook.lite:id/news_feed"),
                contentDescriptions = listOf("What's on your mind?"),
            ),
        )

        assertTrue(result.isMatch)
        assertEquals(Surface.FEED, result.surface)
        assertEquals("com.facebook.lite", result.packageName)
    }

    @Test
    fun `watchedPackages is the union of every detector's targetPackages`() {
        val screenDetector = ScreenDetector.default()

        assertEquals(
            setOf(
                "com.instagram.android",
                "com.google.android.youtube",
                "com.zhiliaoapp.musically",
                "com.facebook.katana",
                "com.facebook.lite",
            ),
            screenDetector.watchedPackages,
        )
    }

    @Test
    fun `construction fails when two detectors claim the same package`() {
        val a = FakeDetector("com.dup.app", DetectionResult.none("com.dup.app"))
        val b = FakeDetector("com.dup.app", DetectionResult.none("com.dup.app"))

        assertThrows(IllegalArgumentException::class.java) {
            ScreenDetector(listOf(a, b))
        }
    }

    private class FakeDetector(
        override val targetPackage: String,
        private val result: DetectionResult,
    ) : AppDetector {
        var calls = 0
            private set

        override fun detect(snapshot: ScreenSnapshot): DetectionResult {
            calls++
            return result
        }
    }
}
