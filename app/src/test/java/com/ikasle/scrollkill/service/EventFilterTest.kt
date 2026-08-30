package com.ikasle.scrollkill.service

import android.view.accessibility.AccessibilityEvent
import com.ikasle.scrollkill.service.EventFilter.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure test: `AccessibilityEvent.TYPE_*` are compile-time int constants, so no Robolectric.
 *
 * Timestamps start from [BASE] rather than 0 because the production caller passes
 * `SystemClock.uptimeMillis()` (millis since boot); the first-ever event for a package is
 * debounced against 0, so a first event at t &lt; debounceMs would be dropped.
 */
class EventFilterTest {

    private val watched = setOf("com.instagram.android", "com.zhiliaoapp.musically")
    private val state = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    private val content = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

    @Test
    fun `null package is ignored`() {
        assertSame(Outcome.Ignore, EventFilter().evaluate(null, state, watched, BASE))
    }

    @Test
    fun `package outside the watched set is ignored`() {
        assertSame(
            Outcome.Ignore,
            EventFilter().evaluate("com.example.other", state, watched, BASE),
        )
    }

    @Test
    fun `window state change on a watched package is processed`() {
        assertEquals(
            Outcome.Process("com.instagram.android", fromWindowStateChange = true),
            EventFilter().evaluate("com.instagram.android", state, watched, BASE),
        )
    }

    @Test
    fun `window content change on a watched package is processed`() {
        assertEquals(
            Outcome.Process("com.instagram.android", fromWindowStateChange = false),
            EventFilter().evaluate("com.instagram.android", content, watched, BASE),
        )
    }

    @Test
    fun `irrelevant event types are ignored`() {
        val filter = EventFilter()
        assertSame(
            Outcome.Ignore,
            filter.evaluate("com.instagram.android", AccessibilityEvent.TYPE_VIEW_CLICKED, watched, BASE),
        )
        assertSame(
            Outcome.Ignore,
            filter.evaluate("com.instagram.android", AccessibilityEvent.TYPE_VIEW_SCROLLED, watched, BASE + 10_000L),
        )
    }

    @Test
    fun `an irrelevant event does not arm the debounce`() {
        val filter = EventFilter(debounceMs = 250L)
        filter.evaluate("com.instagram.android", AccessibilityEvent.TYPE_VIEW_SCROLLED, watched, BASE)
        assertEquals(
            Outcome.Process("com.instagram.android", fromWindowStateChange = false),
            filter.evaluate("com.instagram.android", content, watched, BASE + 10L),
        )
    }

    @Test
    fun `a second event within the debounce window is ignored`() {
        val filter = EventFilter(debounceMs = 250L)
        filter.evaluate("com.instagram.android", content, watched, BASE)
        assertSame(
            Outcome.Ignore,
            filter.evaluate("com.instagram.android", content, watched, BASE + 249L),
        )
    }

    @Test
    fun `an event exactly at the debounce boundary is processed`() {
        val filter = EventFilter(debounceMs = 250L)
        filter.evaluate("com.instagram.android", content, watched, BASE)
        assertEquals(
            Outcome.Process("com.instagram.android", fromWindowStateChange = false),
            filter.evaluate("com.instagram.android", content, watched, BASE + 250L),
        )
    }

    @Test
    fun `debounce is tracked per package`() {
        val filter = EventFilter(debounceMs = 250L)
        filter.evaluate("com.instagram.android", content, watched, BASE)
        assertEquals(
            Outcome.Process("com.zhiliaoapp.musically", fromWindowStateChange = false),
            filter.evaluate("com.zhiliaoapp.musically", content, watched, BASE),
        )
    }

    @Test
    fun `debounce blocks a burst then releases after the window passes`() {
        val filter = EventFilter(debounceMs = 250L)
        assertEquals(
            Outcome.Process("com.instagram.android", fromWindowStateChange = true),
            filter.evaluate("com.instagram.android", state, watched, BASE),
        )
        assertSame(
            Outcome.Ignore,
            filter.evaluate("com.instagram.android", content, watched, BASE + 100L),
        )
        assertEquals(
            Outcome.Process("com.instagram.android", fromWindowStateChange = false),
            filter.evaluate("com.instagram.android", content, watched, BASE + 300L),
        )
    }

    @Test
    fun `clear resets the debounce state`() {
        val filter = EventFilter(debounceMs = 250L)
        filter.evaluate("com.instagram.android", content, watched, BASE)
        filter.clear()
        assertEquals(
            Outcome.Process("com.instagram.android", fromWindowStateChange = false),
            filter.evaluate("com.instagram.android", content, watched, BASE + 100L),
        )
    }

    private companion object {
        /** Stand-in for a realistic `SystemClock.uptimeMillis()` value. */
        const val BASE = 5_000_000L
    }
}
