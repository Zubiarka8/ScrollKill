package com.ikasle.scrollkill.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ikasle.scrollkill.data.settings.RetentionWindow
import com.ikasle.scrollkill.data.settings.SettingsRepository
import com.ikasle.scrollkill.data.settings.StatsWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
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

/** One DataStore write per test (see SettingsRepositoryTest for the Windows rename note). */
class SettingsViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newViewModel() = SettingsViewModel(
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tmp.root.resolve("settings.preferences_pb")
            },
        ),
    )

    @Test
    fun `apps lists every watched package, blocking enabled, sorted by name`() = runTest {
        val state = newViewModel().uiState.first { it.apps.isNotEmpty() }

        assertEquals(5, state.apps.size)
        assertTrue(state.apps.all { it.blockingEnabled })
        assertEquals(state.apps.map { it.displayName }.sorted(), state.apps.map { it.displayName })
    }

    @Test
    fun `toggling an app off marks only that row`() = runTest {
        val vm = newViewModel()

        vm.setAppBlockingEnabled("com.instagram.android", enabled = false)

        val state = vm.uiState.first { st -> st.apps.any { !it.blockingEnabled } }
        assertFalse(state.apps.first { it.packageName == "com.instagram.android" }.blockingEnabled)
        assertTrue(state.apps.filter { it.packageName != "com.instagram.android" }.all { it.blockingEnabled })
    }

    @Test
    fun `stats window choice is reflected`() = runTest {
        val vm = newViewModel()

        vm.setStatsWindow(StatsWindow.LAST_24_HOURS)

        assertEquals(StatsWindow.LAST_24_HOURS, vm.uiState.first { it.statsWindow == StatsWindow.LAST_24_HOURS }.statsWindow)
    }

    @Test
    fun `history retention choice is reflected`() = runTest {
        val vm = newViewModel()

        vm.setHistoryRetention(RetentionWindow.DAYS_30)

        assertEquals(RetentionWindow.DAYS_30, vm.uiState.first { it.historyRetention == RetentionWindow.DAYS_30 }.historyRetention)
    }

    @Test
    fun `master toggle is reflected`() = runTest {
        val vm = newViewModel()

        vm.setInterveneEnabled(false)

        assertFalse(vm.uiState.first { !it.interveneEnabled }.interveneEnabled)
    }
}
