package com.ikasle.scrollkill.blocking

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyUsageMeterTest {

    // Small window so the ageing-out cases stay readable: 10s window, 1s buckets, 1s gap cap.
    private val meter = DailyUsageMeter(windowMs = 10_000L, maxGapMs = 1_000L, bucketMs = 1_000L)

    private val pkg = "com.instagram.android"

    @Test
    fun `the first event for a package credits nothing`() {
        meter.record(pkg, 0L)

        assertEquals(0L, meter.usedMs(pkg, 0L))
    }

    @Test
    fun `an unknown package reads zero`() {
        assertEquals(0L, meter.usedMs("com.example.unseen", 5_000L))
    }

    @Test
    fun `the gap between consecutive events is credited`() {
        meter.record(pkg, 0L)
        meter.record(pkg, 500L)
        meter.record(pkg, 900L)

        assertEquals(900L, meter.usedMs(pkg, 900L))
    }

    @Test
    fun `a gap longer than maxGapMs is capped`() {
        meter.record(pkg, 0L)
        meter.record(pkg, 5_000L) // 5s real gap, capped to 1s

        assertEquals(1_000L, meter.usedMs(pkg, 5_000L))
    }

    @Test
    fun `usage older than the window is dropped`() {
        meter.record(pkg, 0L)
        meter.record(pkg, 1_000L) // credits 1s into an early bucket

        assertEquals(0L, meter.usedMs(pkg, 20_000L))
    }

    @Test
    fun `seed makes historical usage immediately visible`() {
        meter.seed(mapOf(pkg to 5_000L), 0L)

        assertEquals(5_000L, meter.usedMs(pkg, 0L))
    }

    @Test
    fun `seeded usage ages out after the window`() {
        meter.seed(mapOf(pkg to 5_000L), 0L)

        assertEquals(0L, meter.usedMs(pkg, 20_000L))
    }

    @Test
    fun `seed adds to live usage rather than replacing it`() {
        meter.record(pkg, 0L)
        meter.record(pkg, 1_000L) // 1s live
        meter.seed(mapOf(pkg to 4_000L), 1_000L)

        assertEquals(5_000L, meter.usedMs(pkg, 1_000L))
    }

    @Test
    fun `reset forgets all usage`() {
        meter.seed(mapOf(pkg to 5_000L), 0L)
        meter.reset()

        assertEquals(0L, meter.usedMs(pkg, 0L))
    }
}
