package com.ikasle.scrollkill.ui.home

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ikasle.scrollkill.data.session.ScrollKillDatabase
import com.ikasle.scrollkill.data.session.SessionEntity
import com.ikasle.scrollkill.data.session.SessionRepository
import com.ikasle.scrollkill.data.settings.DailyLimit
import com.ikasle.scrollkill.data.settings.SettingsRepository
import com.ikasle.scrollkill.data.settings.StatsWindow
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_700_000_000_000L
    private val dayMs = 24 * 60 * 60 * 1000L

    private lateinit var db: ScrollKillDatabase
    private lateinit var sessionRepository: SessionRepository
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScrollKillDatabase::class.java,
        ).allowMainThreadQueries().build()
        sessionRepository = SessionRepository(db.sessionDao(), clock = { now })
        settingsRepository = SettingsRepository(
            PreferenceDataStoreFactory.create { tmp.root.resolve("settings.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun viewModel() = HomeViewModel(
        sessionRepository,
        settingsRepository,
        clock = { now },
        zone = TimeZone.getTimeZone("UTC"),
    )

    private suspend fun insertSession(
        pkg: String,
        endedAtEpochMs: Long,
        durationMs: Long,
        interventionCount: Int = 0,
    ) {
        db.sessionDao().insert(
            SessionEntity(
                packageName = pkg,
                surface = "FEED",
                startedAtEpochMs = endedAtEpochMs - durationMs,
                endedAtEpochMs = endedAtEpochMs,
                durationMs = durationMs,
                detectionCount = 1,
                interventionCount = interventionCount,
            ),
        )
    }

    @Test
    fun `per-app rows are sorted by time spent with mapped names and formatted durations`() = runTest {
        insertSession("com.google.android.youtube", endedAtEpochMs = now - 1_000, durationMs = 60_000, interventionCount = 1)
        insertSession("com.instagram.android", endedAtEpochMs = now - 1_000, durationMs = 3_600_000, interventionCount = 2)
        insertSession("com.instagram.android", endedAtEpochMs = now - 2_000, durationMs = 60_000, interventionCount = 1)

        val state = viewModel().uiState.first { !it.loading && it.apps.isNotEmpty() }

        assertEquals(listOf("Instagram", "YouTube"), state.apps.map { it.displayName })
        assertEquals("1h 1m", state.apps[0].duration)
        assertEquals(2, state.apps[0].sessionCount)
        assertEquals(3, state.apps[0].interventionCount)
        assertEquals("1m", state.apps[1].duration)
        assertEquals("1h 2m", state.totalDuration)
    }

    @Test
    fun `sessions outside the 7 day window are excluded`() = runTest {
        insertSession("com.instagram.android", endedAtEpochMs = now - 8 * dayMs, durationMs = 60_000)
        insertSession("com.google.android.youtube", endedAtEpochMs = now - 1_000, durationMs = 60_000)

        val state = viewModel().uiState.first { !it.loading && it.apps.isNotEmpty() }

        assertEquals(listOf("YouTube"), state.apps.map { it.displayName })
    }

    @Test
    fun `empty usage resolves to no rows, not loading, default window label`() = runTest {
        val state = viewModel().uiState.first { !it.loading }

        assertTrue(state.apps.isEmpty())
        assertFalse(state.loading)
        assertEquals("Last 7 days", state.windowLabel)
    }

    @Test
    fun `changing the stats window re-queries with the narrower cutoff`() = runTest {
        // Two days old: inside the default 7-day window, outside a 24-hour window.
        insertSession("com.instagram.android", endedAtEpochMs = now - 2 * dayMs, durationMs = 60_000)
        val vm = viewModel()
        assertEquals(
            listOf("Instagram"),
            vm.uiState.first { !it.loading && it.apps.isNotEmpty() }.apps.map { it.displayName },
        )

        settingsRepository.setStatsWindow(StatsWindow.LAST_24_HOURS)

        val narrowed = vm.uiState.first { it.windowLabel == "Last 24 hours" }
        assertTrue(narrowed.apps.isEmpty())
    }

    @Test
    fun `setInterveneEnabled is reflected in state`() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.first { !it.loading }.interveneEnabled)

        vm.setInterveneEnabled(false)

        assertFalse(vm.uiState.first { !it.interveneEnabled }.interveneEnabled)
    }

    @Test
    fun `today total counts only sessions since local midnight`() = runTest {
        // 1h ago: today. 25h ago: before midnight (UTC anchor at now - 22h13m).
        insertSession("com.instagram.android", endedAtEpochMs = now - 3_600_000, durationMs = 600_000)
        insertSession("com.instagram.android", endedAtEpochMs = now - 25 * 3_600_000L, durationMs = 600_000)

        val state = viewModel().uiState.first { !it.loading && it.todayApps.isNotEmpty() }

        assertEquals("10m", state.todayTotalDuration)
        assertEquals(1, state.todayApps.size)
        assertEquals("10m", state.todayApps[0].usedToday)
    }

    @Test
    fun `today limit progress uses the per-app override over the default`() = runTest {
        settingsRepository.setDefaultDailyLimit(DailyLimit.MIN_60)
        settingsRepository.setDailyLimitOverride("com.instagram.android", DailyLimit.MIN_30)
        insertSession("com.instagram.android", endedAtEpochMs = now - 3_600_000, durationMs = 15 * 60_000L)

        val app = viewModel().uiState
            .first { !it.loading && it.todayApps.isNotEmpty() }
            .todayApps[0]

        assertEquals(0.5f, app.progress!!, 0.001f) // 15m / 30m, not 15m / 60m
        assertEquals("15m / 30m", app.limitCaption)
        assertFalse(app.overLimit)
    }

    @Test
    fun `today row with no effective limit has null progress and a plain caption`() = runTest {
        insertSession("com.instagram.android", endedAtEpochMs = now - 3_600_000, durationMs = 15 * 60_000L)

        val app = viewModel().uiState
            .first { !it.loading && it.todayApps.isNotEmpty() }
            .todayApps[0]

        assertNull(app.progress)
        assertEquals("No limit set", app.limitCaption)
        assertFalse(app.overLimit)
    }

    @Test
    fun `today usage at or past the budget is flagged over limit`() = runTest {
        settingsRepository.setDefaultDailyLimit(DailyLimit.MIN_10)
        insertSession("com.instagram.android", endedAtEpochMs = now - 3_600_000, durationMs = 12 * 60_000L)

        val app = viewModel().uiState
            .first { !it.loading && it.todayApps.isNotEmpty() }
            .todayApps[0]

        assertTrue(app.overLimit)
        assertEquals(1f, app.progress!!, 0.001f)
        assertEquals("12m / 10m - over", app.limitCaption)
    }

    @Test
    fun `onResume publishes the accessibility service state`() = runTest {
        val vm = viewModel()
        assertFalse(vm.uiState.first { !it.loading }.serviceEnabled)

        vm.onResume(serviceEnabledNow = true)

        assertTrue(vm.uiState.first { it.serviceEnabled }.serviceEnabled)
    }
}
