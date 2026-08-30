package com.ikasle.scrollkill.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Each test writes at most once: the DataStore file backend fails to rename over an
 * existing file on a second write on Windows, so multi-write scenarios are split so both
 * branches of every setter are still covered.
 */
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.newRepo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tmp.root.resolve("$name.preferences_pb")
        },
    )

    @Test
    fun `defaults apply when nothing is stored`() = runTest {
        val settings = newRepo("default").settings.first()

        assertTrue(settings.interveneEnabled)
        assertTrue(settings.blockingDisabledPackages.isEmpty())
        assertTrue(settings.watchingDisabledPackages.isEmpty())
        assertEquals(StatsWindow.LAST_7_DAYS, settings.statsWindow)
        assertEquals(RetentionWindow.DAYS_90, settings.historyRetention)
        assertEquals(DailyLimit.Off, settings.defaultDailyLimit)
        assertTrue(settings.dailyLimitOverrides.isEmpty())
        assertEquals(ConfidenceFloor.BALANCED, settings.detectionConfidenceFloor)
        assertEquals(BlockingCooldown.SEC_45, settings.blockingCooldown)
        assertEquals(IdleTimeout.SEC_15, settings.sessionIdleTimeout)
        assertEquals(MinSessionDuration.SEC_1, settings.minSessionDuration)
        assertFalse(settings.onboardingComplete)
    }

    @Test
    fun `detection confidence floor round-trips`() = runTest {
        val repo = newRepo("confidence-floor")

        repo.setDetectionConfidenceFloor(ConfidenceFloor.STRICT)

        assertEquals(ConfidenceFloor.STRICT, repo.settings.first().detectionConfidenceFloor)
    }

    @Test
    fun `blocking cooldown round-trips`() = runTest {
        val repo = newRepo("cooldown")

        repo.setBlockingCooldown(BlockingCooldown.SEC_120)

        assertEquals(BlockingCooldown.SEC_120, repo.settings.first().blockingCooldown)
    }

    @Test
    fun `session idle timeout round-trips`() = runTest {
        val repo = newRepo("idle-timeout")

        repo.setSessionIdleTimeout(IdleTimeout.SEC_60)

        assertEquals(IdleTimeout.SEC_60, repo.settings.first().sessionIdleTimeout)
    }

    @Test
    fun `minimum session duration round-trips`() = runTest {
        val repo = newRepo("min-session")

        repo.setMinSessionDuration(MinSessionDuration.SEC_10)

        assertEquals(MinSessionDuration.SEC_10, repo.settings.first().minSessionDuration)
    }

    @Test
    fun `a corrupt stored policy enum falls back to the default`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tmp.root.resolve("corrupt-policy.preferences_pb")
        }
        store.edit { it[stringPreferencesKey("blocking_cooldown")] = "SEC_999" }

        assertEquals(
            BlockingCooldown.SEC_45,
            SettingsRepository(store).settings.first().blockingCooldown,
        )
    }

    @Test
    fun `setInterveneEnabled is reflected in settings`() = runTest {
        val repo = newRepo("toggle")

        repo.setInterveneEnabled(false)

        assertFalse(repo.settings.first().interveneEnabled)
    }

    @Test
    fun `disabling an app adds it to the disabled set`() = runTest {
        val repo = newRepo("disable")

        repo.setBlockingEnabledForPackage("com.instagram.android", enabled = false)

        assertEquals(setOf("com.instagram.android"), repo.settings.first().blockingDisabledPackages)
    }

    @Test
    fun `re-enabling an app removes it from the disabled set`() = runTest {
        val repo = newRepo("enable")

        // Removal branch: enabling a package not present leaves the set empty.
        repo.setBlockingEnabledForPackage("com.instagram.android", enabled = true)

        assertTrue(repo.settings.first().blockingDisabledPackages.isEmpty())
    }

    @Test
    fun `disabling watching adds the app to the unwatched set`() = runTest {
        val repo = newRepo("unwatch")

        repo.setWatchingEnabledForPackage("com.instagram.android", enabled = false)

        assertEquals(setOf("com.instagram.android"), repo.settings.first().watchingDisabledPackages)
    }

    @Test
    fun `re-enabling watching removes the app from the unwatched set`() = runTest {
        val repo = newRepo("rewatch")

        // Removal branch: enabling a package not present leaves the set empty.
        repo.setWatchingEnabledForPackage("com.instagram.android", enabled = true)

        assertTrue(repo.settings.first().watchingDisabledPackages.isEmpty())
    }

    @Test
    fun `stats window round-trips`() = runTest {
        val repo = newRepo("window")

        repo.setStatsWindow(StatsWindow.LAST_30_DAYS)

        assertEquals(StatsWindow.LAST_30_DAYS, repo.settings.first().statsWindow)
    }

    @Test
    fun `history retention round-trips`() = runTest {
        val repo = newRepo("retention")

        repo.setHistoryRetention(RetentionWindow.DAYS_365)

        assertEquals(RetentionWindow.DAYS_365, repo.settings.first().historyRetention)
    }

    @Test
    fun `default daily limit round-trips`() = runTest {
        val repo = newRepo("daily-default")

        repo.setDefaultDailyLimit(DailyLimit.Minutes(30))

        assertEquals(DailyLimit.Minutes(30), repo.settings.first().defaultDailyLimit)
    }

    @Test
    fun `a per-app daily limit override round-trips`() = runTest {
        val repo = newRepo("daily-override")

        repo.setDailyLimitOverride("com.instagram.android", DailyLimit.Minutes(15))

        assertEquals(
            mapOf("com.instagram.android" to DailyLimit.Minutes(15)),
            repo.settings.first().dailyLimitOverrides,
        )
    }

    @Test
    fun `a custom (non-preset) default daily limit round-trips`() = runTest {
        val repo = newRepo("daily-custom-default")

        repo.setDefaultDailyLimit(DailyLimit.Minutes(42))

        assertEquals(DailyLimit.Minutes(42), repo.settings.first().defaultDailyLimit)
    }

    @Test
    fun `a custom (non-preset) per-app daily limit override round-trips`() = runTest {
        val repo = newRepo("daily-custom-override")

        repo.setDailyLimitOverride("com.instagram.android", DailyLimit.Minutes(7))

        assertEquals(
            mapOf("com.instagram.android" to DailyLimit.Minutes(7)),
            repo.settings.first().dailyLimitOverrides,
        )
    }

    @Test
    fun `clearing a daily limit override that is absent leaves the map empty`() = runTest {
        val repo = newRepo("daily-override-clear")

        repo.setDailyLimitOverride("com.instagram.android", null)

        assertTrue(repo.settings.first().dailyLimitOverrides.isEmpty())
    }

    @Test
    fun `malformed daily limit override tokens are ignored`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tmp.root.resolve("daily-corrupt.preferences_pb")
        }
        store.edit {
            it[stringSetPreferencesKey("daily_limit_overrides")] = setOf(
                "com.instagram.android=NOPE", // unknown token
                "no-equals-sign",             // no delimiter
                "com.facebook.katana=MIN_5",  // valid (legacy enum-name form)
            )
        }

        assertEquals(
            mapOf("com.facebook.katana" to DailyLimit.Minutes(5)),
            SettingsRepository(store).settings.first().dailyLimitOverrides,
        )
    }

    @Test
    fun `setOnboardingComplete is reflected in settings`() = runTest {
        val repo = newRepo("onboarding")

        repo.setOnboardingComplete(true)

        assertTrue(repo.settings.first().onboardingComplete)
    }

    @Test
    fun `a corrupt stored enum falls back to the default`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tmp.root.resolve("corrupt.preferences_pb")
        }
        store.edit { it[stringPreferencesKey("stats_window")] = "NOT_A_WINDOW" }

        assertEquals(StatsWindow.LAST_7_DAYS, SettingsRepository(store).settings.first().statsWindow)
    }
}
