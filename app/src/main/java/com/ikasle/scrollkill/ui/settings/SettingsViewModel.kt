package com.ikasle.scrollkill.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikasle.scrollkill.data.settings.DailyLimit
import com.ikasle.scrollkill.data.settings.RetentionWindow
import com.ikasle.scrollkill.data.settings.SettingsRepository
import com.ikasle.scrollkill.data.settings.StatsWindow
import com.ikasle.scrollkill.data.settings.dailyLimitFor
import com.ikasle.scrollkill.detection.ScreenDetector
import com.ikasle.scrollkill.ui.home.KnownApps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the settings screen. The app list is the detector-watched set (pure,
 * framework-free); everything else is a projection of [SettingsRepository.settings], and
 * every action writes straight back to it.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val watchedPackages: List<String> =
        ScreenDetector.default().watchedPackages.sortedBy { KnownApps.label(it) }

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { settings ->
            SettingsUiState(
                interveneEnabled = settings.interveneEnabled,
                statsWindow = settings.statsWindow,
                historyRetention = settings.historyRetention,
                defaultDailyLimit = settings.defaultDailyLimit,
                apps = watchedPackages.map { pkg ->
                    AppToggleUi(
                        packageName = pkg,
                        displayName = KnownApps.label(pkg),
                        blockingEnabled = pkg !in settings.blockingDisabledPackages,
                        dailyLimit = settings.dailyLimitFor(pkg),
                        dailyLimitIsOverride = pkg in settings.dailyLimitOverrides,
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setInterveneEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setInterveneEnabled(enabled) }
    }

    fun setAppBlockingEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBlockingEnabledForPackage(packageName, enabled) }
    }

    /** Set the budget applied to every watched app without a per-app override. */
    fun setDefaultDailyLimit(limit: DailyLimit) {
        viewModelScope.launch { settingsRepository.setDefaultDailyLimit(limit) }
    }

    /** Override a single app's budget, or with [limit] null clear it back to the default. */
    fun setAppDailyLimit(packageName: String, limit: DailyLimit?) {
        viewModelScope.launch { settingsRepository.setDailyLimitOverride(packageName, limit) }
    }

    fun setStatsWindow(window: StatsWindow) {
        viewModelScope.launch { settingsRepository.setStatsWindow(window) }
    }

    fun setHistoryRetention(retention: RetentionWindow) {
        viewModelScope.launch { settingsRepository.setHistoryRetention(retention) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
