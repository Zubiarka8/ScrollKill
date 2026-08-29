package com.ikasle.scrollkill.session

import com.ikasle.scrollkill.blocking.BlockingDecision
import com.ikasle.scrollkill.detection.DetectionResult
import com.ikasle.scrollkill.detection.DetectionResult.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTrackerTest {

    private val tracker = SessionTracker()

    private fun on(surface: Surface, pkg: String = IG, confidence: Float = 0.9f) =
        DetectionResult(packageName = pkg, surface = surface, confidence = confidence)

    private fun gone(pkg: String = IG) = DetectionResult.none(pkg)

    private fun intervene(pkg: String = IG, surface: Surface = Surface.FEED) =
        BlockingDecision.Intervene(pkg, surface, 0.9f)

    private fun track(
        nowMs: Long,
        result: DetectionResult,
        decision: BlockingDecision = BlockingDecision.None,
    ) = tracker.track(result, decision, nowMs)

    @Test
    fun `first tracked match opens a session but emits nothing`() {
        assertNull(track(0L, on(Surface.FEED)))
    }

    @Test
    fun `consecutive matches on the same surface extend the session`() {
        assertNull(track(0L, on(Surface.FEED)))
        assertNull(track(500L, on(Surface.FEED)))
        assertNull(track(1_000L, on(Surface.FEED)))

        val session = track(3_000L, gone())

        assertNotNull(session)
        assertEquals(3, session!!.detectionCount)
    }

    @Test
    fun `leaving the tracked surface closes the session with the right fields`() {
        track(1_000L, on(Surface.FEED))
        track(3_000L, on(Surface.FEED))

        val session = track(5_000L, gone())!!

        assertEquals(IG, session.packageName)
        assertEquals(Surface.FEED, session.surface)
        assertEquals(1_000L, session.startedAtMs)
        assertEquals(3_000L, session.endedAtMs)
        assertEquals(2_000L, session.durationMs)
        assertEquals(2, session.detectionCount)
        assertEquals(0, session.interventionCount)
    }

    @Test
    fun `a session shorter than the minimum duration is discarded`() {
        track(0L, on(Surface.FEED))

        assertNull(track(500L, gone()))
    }

    @Test
    fun `a surface change within the same package ends one session and starts another`() {
        track(0L, on(Surface.FEED))
        track(2_000L, on(Surface.FEED))

        val first = track(3_000L, on(Surface.SHORT_VIDEO))!!
        assertEquals(Surface.FEED, first.surface)
        assertEquals(0L, first.startedAtMs)
        assertEquals(2_000L, first.endedAtMs)

        track(4_500L, on(Surface.SHORT_VIDEO))
        val second = track(6_000L, gone())!!
        assertEquals(Surface.SHORT_VIDEO, second.surface)
        assertEquals(3_000L, second.startedAtMs)
        assertEquals(4_500L, second.endedAtMs)
    }

    @Test
    fun `a package switch ends the running session`() {
        track(0L, on(Surface.FEED, pkg = IG))
        track(2_000L, on(Surface.FEED, pkg = IG))

        val session = track(3_000L, on(Surface.SHORT_VIDEO, pkg = YT))!!

        assertEquals(IG, session.packageName)
        assertEquals(0L, session.startedAtMs)
        assertEquals(2_000L, session.endedAtMs)
    }

    @Test
    fun `interventionCount counts only Intervene decisions`() {
        track(0L, on(Surface.FEED), BlockingDecision.None)
        track(500L, on(Surface.FEED), intervene())
        track(1_000L, on(Surface.FEED), intervene())
        track(1_500L, on(Surface.FEED), BlockingDecision.None)

        val session = track(3_000L, gone())!!

        assertEquals(4, session.detectionCount)
        assertEquals(2, session.interventionCount)
    }

    @Test
    fun `an event past the idle timeout closes the stale session and starts a fresh one`() {
        track(0L, on(Surface.FEED))
        track(2_000L, on(Surface.FEED))

        val stale = track(2_000L + IDLE_TIMEOUT_MS + 1, on(Surface.FEED))!!
        assertEquals(0L, stale.startedAtMs)
        assertEquals(2_000L, stale.endedAtMs)

        track(2_000L + IDLE_TIMEOUT_MS + 3_000, on(Surface.FEED))
        val fresh = track(2_000L + IDLE_TIMEOUT_MS + 5_000, gone())!!
        assertEquals(2_000L + IDLE_TIMEOUT_MS + 1, fresh.startedAtMs)
    }

    @Test
    fun `a gap equal to the idle timeout still extends the session`() {
        track(0L, on(Surface.FEED))

        assertNull(track(IDLE_TIMEOUT_MS, on(Surface.FEED)))

        val session = track(IDLE_TIMEOUT_MS + 1_000, gone())!!
        assertEquals(0L, session.startedAtMs)
        assertEquals(IDLE_TIMEOUT_MS, session.endedAtMs)
        assertEquals(2, session.detectionCount)
    }

    @Test
    fun `EXPLORE is tracked by default`() {
        track(0L, on(Surface.EXPLORE))
        track(2_000L, on(Surface.EXPLORE))

        val session = track(4_000L, gone())!!

        assertEquals(Surface.EXPLORE, session.surface)
    }

    @Test
    fun `a match below the confidence floor does not open a session`() {
        track(0L, on(Surface.FEED, confidence = 0.5f))

        assertNull(track(3_000L, gone()))
    }

    @Test
    fun `flush returns the open session then nothing`() {
        track(0L, on(Surface.FEED))
        track(2_000L, on(Surface.FEED))

        val session = tracker.flush(3_000L)!!
        assertEquals(0L, session.startedAtMs)
        assertEquals(2_000L, session.endedAtMs)

        assertNull(tracker.flush(4_000L))
    }

    @Test
    fun `flush discards a session shorter than the minimum`() {
        track(0L, on(Surface.FEED))

        assertNull(tracker.flush(500L))
    }

    @Test
    fun `reset drops the open session without emitting`() {
        track(0L, on(Surface.FEED))
        track(2_000L, on(Surface.FEED))

        tracker.reset()

        assertNull(tracker.flush(3_000L))
    }

    private companion object {
        const val IG = "com.instagram.android"
        const val YT = "com.google.android.youtube"

        /** Mirrors SessionTracker.DEFAULT_IDLE_TIMEOUT_MS. */
        const val IDLE_TIMEOUT_MS = 15_000L
    }
}
