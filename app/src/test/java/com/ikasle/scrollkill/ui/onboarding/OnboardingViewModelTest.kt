package com.ikasle.scrollkill.ui.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ikasle.scrollkill.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** One DataStore write per test (see SettingsRepositoryTest for the Windows rename note). */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newViewModel() = OnboardingViewModel(
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tmp.root.resolve("settings.preferences_pb")
            },
        ),
    )

    @Test
    fun `onboarding is pending on a fresh install`() = runTest {
        val state = newViewModel().uiState.first { !it.loading }

        assertFalse(state.onboardingComplete)
        assertTrue(state.showOnboarding)
    }

    @Test
    fun `completeOnboarding records consent and dismisses the rationale`() = runTest {
        val vm = newViewModel()

        vm.completeOnboarding()

        val state = vm.uiState.first { it.onboardingComplete }
        assertFalse(state.showOnboarding)
    }
}
