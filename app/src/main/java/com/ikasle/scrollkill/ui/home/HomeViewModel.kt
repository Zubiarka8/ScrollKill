package com.ikasle.scrollkill.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikasle.scrollkill.data.session.SessionRepository
import com.ikasle.scrollkill.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The "ViewModel" stage of the CLAUDE.md pipeline. Reads the two repositories, folds the
 * per-app usage of the last 7 days plus the settings toggle and the accessibility-service
 * status into one [HomeUiState].
 *
 * [clock] is injectable for tests. [onResume] is called by the screen so the window
 * re-anchors and the service status is re-checked when the user comes back from settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val serviceEnabled = MutableStateFlow(false)
    private val windowAnchorMs = MutableStateFlow(clock())

    private val usage = windowAnchorMs.flatMapLatest { anchor ->
        sessionRepository.observePerAppUsageSince(anchor - WINDOW_MS)
    }

    val uiState: StateFlow<HomeUiState> =
        combine(settingsRepository.settings, serviceEnabled, usage) { settings, enabled, rows ->
            HomeUiState(
                loading = false,
                serviceEnabled = enabled,
                interveneEnabled = settings.interveneEnabled,
                totalDuration = formatDuration(rows.sumOf { it.totalDurationMs }),
                apps = rows.sortedByDescending { it.totalDurationMs }.map { row ->
                    AppUsageUi(
                        packageName = row.packageName,
                        displayName = KnownApps.label(row.packageName),
                        duration = formatDuration(row.totalDurationMs),
                        sessionCount = row.sessionCount,
                        interventionCount = row.totalInterventions,
                    )
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun onResume(serviceEnabledNow: Boolean) {
        serviceEnabled.value = serviceEnabledNow
        windowAnchorMs.value = clock()
    }

    fun setInterveneEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setInterveneEnabled(enabled) }
    }

    private companion object {
        // TODO(settings): make the stats window user-selectable once a settings UI exists.
        const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
