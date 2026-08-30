package com.ikasle.scrollkill.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyLimitTest {

    @Test
    fun `Off has no budget and a stable token`() {
        assertNull(DailyLimit.Off.budgetMs)
        assertEquals("OFF", DailyLimit.Off.storageToken)
    }

    @Test
    fun `Minutes exposes budget, label and token`() {
        val limit = DailyLimit.Minutes(12)

        assertEquals(12L * 60_000, limit.budgetMs)
        assertEquals("12 min/day", limit.label)
        assertEquals("MIN:12", limit.storageToken)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Minutes rejects a non-positive value`() {
        DailyLimit.Minutes(0)
    }

    @Test
    fun `parse round-trips every preset and a custom value`() {
        (DailyLimit.PRESETS + DailyLimit.Minutes(37)).forEach { limit ->
            assertEquals(limit, DailyLimit.parse(limit.storageToken))
        }
    }

    @Test
    fun `parse still accepts the legacy enum names`() {
        assertEquals(DailyLimit.Off, DailyLimit.parse("OFF"))
        assertEquals(DailyLimit.Minutes(5), DailyLimit.parse("MIN_5"))
        assertEquals(DailyLimit.Minutes(60), DailyLimit.parse("MIN_60"))
    }

    @Test
    fun `parse returns null for anything unrecognised`() {
        listOf("", "NOPE", "MIN:", "MIN:abc", "MIN:0", "MIN:-3", "12").forEach {
            assertNull("expected null for \"$it\"", DailyLimit.parse(it))
        }
    }

    @Test
    fun `isCustom is true only for a non-preset minute value`() {
        assertFalse(DailyLimit.isCustom(DailyLimit.Off))
        assertFalse(DailyLimit.isCustom(DailyLimit.Minutes(30))) // a preset
        assertTrue(DailyLimit.isCustom(DailyLimit.Minutes(31)))
    }

    @Test
    fun `PRESETS is Off then the five preset minute values, in order`() {
        assertEquals(
            listOf(
                DailyLimit.Off,
                DailyLimit.Minutes(5),
                DailyLimit.Minutes(10),
                DailyLimit.Minutes(15),
                DailyLimit.Minutes(30),
                DailyLimit.Minutes(60),
            ),
            DailyLimit.PRESETS,
        )
    }
}
