package com.ikasle.scrollkill.ui.home

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ikasle.scrollkill.data.session.ScrollKillDatabase
import com.ikasle.scrollkill.data.session.SessionEntity
import com.ikasle.scrollkill.data.session.SessionRepository
import com.ikasle.scrollkill.data.settings.SettingsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    private fun viewModel() = HomeViewModel(sessionRepository, settingsRepository, clock = { now })

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
    fun `empty usage resolves to no rows and not loading`() = runTest {
        val state = viewModel().uiState.first { !it.loading }

        assertTrue(state.apps.isEmpty())
        assertFalse(state.loading)
    }

    @Test
    fun `setInterveneEnabled is reflected in state`() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.first { !it.loading }.interveneEnabled)

        vm.setInterveneEnabled(false)

        assertFalse(vm.uiState.first { !it.interveneEnabled }.interveneEnabled)
    }

    @Test
    fun `onResume publishes the accessibility service state`() = runTest {
        val vm = viewModel()
        assertFalse(vm.uiState.first { !it.loading }.serviceEnabled)

        vm.onResume(serviceEnabledNow = true)

        assertTrue(vm.uiState.first { it.serviceEnabled }.serviceEnabled)
    }
}
