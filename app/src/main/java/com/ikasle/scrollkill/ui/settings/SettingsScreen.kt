package com.ikasle.scrollkill.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ikasle.scrollkill.R
import com.ikasle.scrollkill.data.settings.DailyLimit
import com.ikasle.scrollkill.data.settings.RetentionWindow
import com.ikasle.scrollkill.data.settings.StatsWindow
import com.ikasle.scrollkill.ui.theme.ScrollKillTheme

/**
 * Stateless settings screen: the master intervention toggle (mirrors Home), a per-app
 * blocking toggle for every watched app, the daily-limit pickers (global default plus
 * per-app override), and the stats-window and history-retention choices. All state comes
 * from [SettingsViewModel] via [state].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onToggleIntervene: (Boolean) -> Unit,
    onToggleApp: (packageName: String, enabled: Boolean) -> Unit,
    onPickDefaultDailyLimit: (DailyLimit) -> Unit,
    onPickAppDailyLimit: (packageName: String, limit: DailyLimit?) -> Unit,
    onPickWindow: (StatsWindow) -> Unit,
    onPickRetention: (RetentionWindow) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.settings_back)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ToggleRow(
                title = stringResource(R.string.nudge_toggle_title),
                subtitle = stringResource(R.string.nudge_toggle_subtitle),
                checked = state.interveneEnabled,
                onCheckedChange = onToggleIntervene,
            )

            Section(stringResource(R.string.settings_section_apps)) {
                state.apps.forEachIndexed { index, app ->
                    if (index > 0) HorizontalDivider()
                    ToggleRow(
                        title = app.displayName,
                        subtitle = null,
                        checked = app.blockingEnabled,
                        onCheckedChange = { onToggleApp(app.packageName, it) },
                    )
                }
            }

            Section(stringResource(R.string.settings_section_daily_limit)) {
                DailyLimit.entries.forEach { limit ->
                    ChoiceRow(
                        label = limit.label,
                        selected = limit == state.defaultDailyLimit,
                        onClick = { onPickDefaultDailyLimit(limit) },
                    )
                }
            }

            if (state.apps.isNotEmpty()) {
                Section(stringResource(R.string.settings_section_daily_limit_per_app)) {
                    state.apps.forEachIndexed { index, app ->
                        if (index > 0) HorizontalDivider()
                        Text(
                            app.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        ChoiceRow(
                            label = stringResource(
                                R.string.settings_daily_limit_use_default,
                                state.defaultDailyLimit.label,
                            ),
                            selected = !app.dailyLimitIsOverride,
                            onClick = { onPickAppDailyLimit(app.packageName, null) },
                        )
                        DailyLimit.entries.forEach { limit ->
                            ChoiceRow(
                                label = limit.label,
                                selected = app.dailyLimitIsOverride && limit == app.dailyLimit,
                                onClick = { onPickAppDailyLimit(app.packageName, limit) },
                            )
                        }
                    }
                }
            }

            Section(stringResource(R.string.settings_section_stats_window)) {
                StatsWindow.entries.forEach { window ->
                    ChoiceRow(
                        label = window.label,
                        selected = window == state.statsWindow,
                        onClick = { onPickWindow(window) },
                    )
                }
            }

            Section(stringResource(R.string.settings_section_retention)) {
                RetentionWindow.entries.forEach { retention ->
                    ChoiceRow(
                        label = retention.label,
                        selected = retention == state.historyRetention,
                        onClick = { onPickRetention(retention) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ScrollKillTheme {
        SettingsScreen(
            state = SettingsUiState(
                interveneEnabled = true,
                statsWindow = StatsWindow.LAST_7_DAYS,
                historyRetention = RetentionWindow.DAYS_90,
                defaultDailyLimit = DailyLimit.MIN_30,
                apps = listOf(
                    AppToggleUi("com.facebook.katana", "Facebook", true),
                    AppToggleUi(
                        "com.instagram.android", "Instagram", false,
                        dailyLimit = DailyLimit.MIN_10, dailyLimitIsOverride = true,
                    ),
                    AppToggleUi(
                        "com.google.android.youtube", "YouTube", true,
                        dailyLimit = DailyLimit.MIN_30, dailyLimitIsOverride = false,
                    ),
                ),
            ),
            onBack = {},
            onToggleIntervene = {},
            onToggleApp = { _, _ -> },
            onPickDefaultDailyLimit = {},
            onPickAppDailyLimit = { _, _ -> },
            onPickWindow = {},
            onPickRetention = {},
        )
    }
}
