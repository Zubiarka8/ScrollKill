package com.ikasle.scrollkill.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class DayBoundaryTest {

    private val dayMs = 24 * 60 * 60 * 1000L

    /** 2023-11-14 22:13:20 UTC. */
    private val instant = 1_700_000_000_000L

    @Test
    fun `UTC midnight is the start of the same UTC calendar day`() {
        val start = startOfTodayMillis(instant, TimeZone.getTimeZone("UTC"))

        assertEquals(1_699_920_000_000L, start) // 2023-11-14 00:00:00 UTC
        assertTrue(instant - start in 0 until dayMs)
    }

    @Test
    fun `positive offset zone anchors to local midnight`() {
        // Asia/Tokyo is UTC+9; local time is 2023-11-15 07:13:20, local midnight 2023-11-15 00:00.
        val start = startOfTodayMillis(instant, TimeZone.getTimeZone("Asia/Tokyo"))

        assertEquals(1_699_974_000_000L, start) // 2023-11-15 00:00:00 +09:00
        assertTrue(instant - start in 0 until dayMs)
    }

    @Test
    fun `negative offset zone anchors to local midnight`() {
        // America/Los_Angeles is UTC-8 in November; local time is 2023-11-14 14:13:20.
        val start = startOfTodayMillis(instant, TimeZone.getTimeZone("America/Los_Angeles"))

        assertEquals(1_699_948_800_000L, start) // 2023-11-14 00:00:00 -08:00
        assertTrue(instant - start in 0 until dayMs)
    }
}
