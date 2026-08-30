package com.ikasle.scrollkill.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikasle.scrollkill.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether the first-run rationale still needs to be shown. */
data class OnboardingUiState(
    /** True until the stored flag has been read once; render nothing meanwhile. */
    val loading: Boolean = true,
    /** The rationale has been shown and the user made an affirmative choice. */
    val onboardingComplete: Boolean = false,
) {
    val showOnboarding: Boolean get() = !loading && !onboardingComplete
}

/**
 * Owns the first-run routing decision: reads [SettingsRepository.onboardingComplete] and
 * flips it once the user acts on the rationale. Both rationale buttons call [completeOnboarding]
 * (the affirmative consent); the caller decides whether to also open Accessibility settings.
 */
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<OnboardingUiState> = settingsRepository.settings
        .map { OnboardingUiState(loading = false, onboardingComplete = it.onboardingComplete) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OnboardingUiState())

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
