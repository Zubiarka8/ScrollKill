package com.ikasle.scrollkill.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    @Test
    fun `zero and negative render as 0s`() {
        assertEquals("0s", formatDuration(0))
        assertEquals("0s", formatDuration(-5_000))
    }

    @Test
    fun `sub-minute renders seconds`() {
        assertEquals("5s", formatDuration(5_000))
        assertEquals("59s", formatDuration(59_999))
    }

    @Test
    fun `sub-hour renders whole minutes`() {
        assertEquals("1m", formatDuration(60_000))
        assertEquals("1m", formatDuration(90_000))
        assertEquals("59m", formatDuration(59 * 60_000L))
    }

    @Test
    fun `hours drop a zero minute part`() {
        assertEquals("1h", formatDuration(3_600_000))
    }

    @Test
    fun `hours keep a non-zero minute part`() {
        assertEquals("1h 1m", formatDuration(3_660_000))
        assertEquals("2h 5m", formatDuration(7_500_000))
    }
}
