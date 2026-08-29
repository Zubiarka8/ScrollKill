package com.ikasle.scrollkill.blocking

import com.ikasle.scrollkill.detection.DetectionResult
import com.ikasle.scrollkill.detection.DetectionResult.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingEngineTest {

    private val engine = BlockingEngine()

    private fun match(
        pkg: String = "com.facebook.katana",
        surface: Surface = Surface.FEED,
        confidence: Float = 0.9f,
    ) = DetectionResult(packageName = pkg, surface = surface, confidence = confidence)

    @Test
    fun `non-match returns None`() {
        assertEquals(
            BlockingDecision.None,
            engine.decide(DetectionResult.none("com.facebook.katana"), 0L),
        )
    }

    @Test
    fun `match on a non-blockable surface returns None`() {
        val feedOnly = BlockingEngine(blockableSurfaces = setOf(Surface.FEED))

        assertEquals(
            BlockingDecision.None,
            feedOnly.decide(match(surface = Surface.SHORT_VIDEO), 0L),
        )
    }

    @Test
    fun `EXPLORE is blockable by default`() {
        assertTrue(engine.decide(match(surface = Surface.EXPLORE), 0L) is BlockingDecision.Intervene)
    }

    @Test
    fun `match below the confidence floor returns None`() {
        val strict = BlockingEngine(minConfidence = 0.8f)

        assertEquals(BlockingDecision.None, strict.decide(match(confidence = 0.7f), 0L))
    }

    @Test
    fun `first blockable match returns Intervene carrying the result fields`() {
        val decision = engine.decide(match(surface = Surface.SHORT_VIDEO, confidence = 0.82f), 0L)

        assertTrue(decision is BlockingDecision.Intervene)
        decision as BlockingDecision.Intervene
        assertEquals("com.facebook.katana", decision.packageName)
        assertEquals(Surface.SHORT_VIDEO, decision.surface)
        assertEquals(0.82f, decision.confidence, 0f)
    }

    @Test
    fun `a second match within the cooldown is suppressed`() {
        assertTrue(engine.decide(match(), 0L) is BlockingDecision.Intervene)

        assertEquals(BlockingDecision.None, engine.decide(match(), 1_000L))
        assertEquals(BlockingDecision.None, engine.decide(match(), 44_999L))
    }

    @Test
    fun `a match after the cooldown elapses intervenes again`() {
        assertTrue(engine.decide(match(), 0L) is BlockingDecision.Intervene)

        assertTrue(engine.decide(match(), 45_000L) is BlockingDecision.Intervene)
    }

    @Test
    fun `leaving the surface and returning re-arms before the cooldown`() {
        assertTrue(engine.decide(match(), 0L) is BlockingDecision.Intervene)
        assertEquals(
            BlockingDecision.None,
            engine.decide(DetectionResult.none("com.facebook.katana"), 1_000L),
        )

        assertTrue(engine.decide(match(), 2_000L) is BlockingDecision.Intervene)
    }

    @Test
    fun `switching to another watched package then back re-arms before the cooldown`() {
        assertTrue(engine.decide(match(pkg = "com.facebook.katana"), 0L) is BlockingDecision.Intervene)
        assertTrue(
            engine.decide(match(pkg = "com.instagram.android", surface = Surface.SHORT_VIDEO), 1_000L)
                is BlockingDecision.Intervene,
        )

        assertTrue(engine.decide(match(pkg = "com.facebook.katana"), 2_000L) is BlockingDecision.Intervene)
    }

    @Test
    fun `reset clears the cooldown memory`() {
        assertTrue(engine.decide(match(), 0L) is BlockingDecision.Intervene)
        engine.reset()

        assertTrue(engine.decide(match(), 1_000L) is BlockingDecision.Intervene)
    }

    @Test
    fun `a blockable match for a package with blocking disabled returns None`() {
        engine.blockingDisabledPackages = setOf("com.facebook.katana")

        assertEquals(BlockingDecision.None, engine.decide(match(), 0L))
    }

    @Test
    fun `clearing the disabled set restores intervention`() {
        engine.blockingDisabledPackages = setOf("com.facebook.katana")
        assertEquals(BlockingDecision.None, engine.decide(match(), 0L))

        engine.blockingDisabledPackages = emptySet()

        assertTrue(engine.decide(match(), 1_000L) is BlockingDecision.Intervene)
    }
}
