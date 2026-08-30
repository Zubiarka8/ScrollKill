package com.ikasle.scrollkill.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [watchedPackagesFrom] is the pure seam the AccessibilityService uses to narrow its filter. */
class WatchedPackagesTest {

    private val candidates = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.facebook.katana",
    )

    @Test
    fun `with nothing unwatched every candidate is observed`() {
        assertEquals(candidates, ScrollKillSettings().watchedPackagesFrom(candidates))
    }

    @Test
    fun `an unwatched package is removed from the observed set`() {
        val settings = ScrollKillSettings(
            watchingDisabledPackages = setOf("com.zhiliaoapp.musically"),
        )

        assertEquals(
            setOf("com.instagram.android", "com.facebook.katana"),
            settings.watchedPackagesFrom(candidates),
        )
    }

    @Test
    fun `unwatching every candidate observes nothing`() {
        val settings = ScrollKillSettings(watchingDisabledPackages = candidates)

        assertTrue(settings.watchedPackagesFrom(candidates).isEmpty())
    }

    @Test
    fun `a stale unwatched entry not among the candidates is harmless`() {
        val settings = ScrollKillSettings(
            watchingDisabledPackages = setOf("com.example.uninstalled"),
        )

        assertEquals(candidates, settings.watchedPackagesFrom(candidates))
    }
}
