package com.ikasle.scrollkill.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikasle.scrollkill.data.session.PerAppUsage
import com.ikasle.scrollkill.data.session.SessionRepository
import com.ikasle.scrollkill.data.settings.ScrollKillSettings
import com.ikasle.scrollkill.data.settings.SettingsRepository
import com.ikasle.scrollkill.data.settings.dailyLimitFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * The "ViewModel" stage of the CLAUDE.md pipeline. Reads the two repositories, folds the
 * per-app usage of the selected window, the "today" usage with its daily-limit progress, the
 * settings toggle and the accessibility-service status into one [HomeUiState].
 *
 * [clock] and [zone] are injectable for tests. [onResume] is called by the screen so the
 * windows re-anchor and the service status is re-checked when the user comes back from settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: TimeZone = TimeZone.getDefault(),
) : ViewModel() {

    private val serviceEnabled = MutableStateFlow(false)
    private val windowAnchorMs = MutableStateFlow(clock())

    /** Epoch cutoff for the usage query; only changes when the window or the anchor does. */
    private val statsSince = combine(windowAnchorMs, settingsRepository.settings) { anchor, settings ->
        anchor - settings.statsWindow.durationMs
    }.distinctUntilChanged()

    private val usage = statsSince.flatMapLatest { since ->
        sessionRepository.observePerAppUsageSince(since)
    }

    /** Local midnight for the current anchor; re-anchors on [onResume]. */
    private val todaySince = windowAnchorMs
        .map { startOfTodayMillis(it, zone) }
        .distinctUntilChanged()

    private val todayUsage = todaySince.flatMapLatest { since ->
        sessionRepository.observePerAppUsageSince(since)
    }

    val uiState: StateFlow<HomeUiState> =
        combine(settingsRepository.settings, serviceEnabled, usage, todayUsage) { settings, enabled, rows, todayRows ->
            HomeUiState(
                loading = false,
                serviceEnabled = enabled,
                interveneEnabled = settings.interveneEnabled,
                todayTotalDuration = formatDuration(todayRows.sumOf { it.totalDurationMs }),
                todayApps = todayRows
                    .sortedByDescending { it.totalDurationMs }
                    .map { row -> row.toTodayApp(settings) },
                windowLabel = settings.statsWindow.label,
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

    private fun PerAppUsage.toTodayApp(settings: ScrollKillSettings): TodayAppUi {
        val budgetMs = settings.dailyLimitFor(packageName).budgetMs
        val overLimit = budgetMs != null && totalDurationMs >= budgetMs
        val caption = when {
            budgetMs == null -> "No limit set"
            overLimit -> "${formatDuration(totalDurationMs)} / ${formatDuration(budgetMs)} - over"
            else -> "${formatDuration(totalDurationMs)} / ${formatDuration(budgetMs)}"
        }
        return TodayAppUi(
            packageName = packageName,
            displayName = KnownApps.label(packageName),
            usedToday = formatDuration(totalDurationMs),
            limitCaption = caption,
            progress = budgetMs?.let { (totalDurationMs.toFloat() / it).coerceIn(0f, 1f) },
            overLimit = overLimit,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
