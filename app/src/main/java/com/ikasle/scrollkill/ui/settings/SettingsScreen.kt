package com.ikasle.scrollkill.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    onToggleWatchApp: (packageName: String, enabled: Boolean) -> Unit,
    onPickDefaultDailyLimit: (DailyLimit) -> Unit,
    onPickAppDailyLimit: (packageName: String, limit: DailyLimit?) -> Unit,
    onPickWindow: (StatsWindow) -> Unit,
    onPickRetention: (RetentionWindow) -> Unit,
    // HAY QUE ELIMINAR (Session 10 battery profiling): debug-only readout, null on release builds.
    debugPanel: (@Composable () -> Unit)? = null,
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
        // Which daily-limit picker (if any) has its "Custom minutes" dialog open. Transient
        // view state, not app state, so it stays local to the screen.
        var customTarget by remember { mutableStateOf<CustomLimitTarget?>(null) }

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
                    AppRow(
                        app = app,
                        onToggleWatch = { onToggleWatchApp(app.packageName, it) },
                        onToggleNudge = { onToggleApp(app.packageName, it) },
                    )
                }
            }

            Section(stringResource(R.string.settings_section_daily_limit)) {
                DailyLimit.PRESETS.forEach { limit ->
                    ChoiceRow(
                        label = limit.label,
                        selected = limit == state.defaultDailyLimit,
                        onClick = { onPickDefaultDailyLimit(limit) },
                    )
                }
                CustomChoiceRow(
                    current = state.defaultDailyLimit,
                    onClick = { customTarget = CustomLimitTarget.Default },
                )
            }

            val limitApps = state.apps.filter { it.watchedEnabled }
            if (limitApps.isNotEmpty()) {
                Section(stringResource(R.string.settings_section_daily_limit_per_app)) {
                    limitApps.forEachIndexed { index, app ->
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
                        DailyLimit.PRESETS.forEach { limit ->
                            ChoiceRow(
                                label = limit.label,
                                selected = app.dailyLimitIsOverride && limit == app.dailyLimit,
                                onClick = { onPickAppDailyLimit(app.packageName, limit) },
                            )
                        }
                        CustomChoiceRow(
                            current = app.dailyLimit.takeIf { app.dailyLimitIsOverride },
                            onClick = { customTarget = CustomLimitTarget.App(app.packageName) },
                        )
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

            // HAY QUE ELIMINAR (Session 10 battery profiling)
            debugPanel?.invoke()
        }

        customTarget?.let { target ->
            val current = when (target) {
                CustomLimitTarget.Default -> state.defaultDailyLimit
                is CustomLimitTarget.App ->
                    state.apps.firstOrNull { it.packageName == target.packageName }?.dailyLimit
            }
            CustomDailyLimitDialog(
                initialMinutes = (current as? DailyLimit.Minutes)?.value,
                onDismiss = { customTarget = null },
                onConfirm = { minutes ->
                    val limit = DailyLimit.Minutes(minutes)
                    when (target) {
                        CustomLimitTarget.Default -> onPickDefaultDailyLimit(limit)
                        is CustomLimitTarget.App -> onPickAppDailyLimit(target.packageName, limit)
                    }
                    customTarget = null
                },
            )
        }
    }
}

/** Open-dialog selector for [SettingsScreen]'s custom daily-limit entry. */
private sealed interface CustomLimitTarget {
    data object Default : CustomLimitTarget
    data class App(val packageName: String) : CustomLimitTarget
}

/**
 * The "Custom..." radio row in a daily-limit picker. Selected (and shows the value) when
 * [current] is a minute budget with no preset row; a plain "Custom..." prompt otherwise.
 */
@Composable
private fun CustomChoiceRow(current: DailyLimit?, onClick: () -> Unit) {
    val isCustom = current != null && DailyLimit.isCustom(current)
    ChoiceRow(
        label = if (isCustom) {
            stringResource(R.string.settings_daily_limit_custom_current, current.label)
        } else {
            stringResource(R.string.settings_daily_limit_custom)
        },
        selected = isCustom,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDailyLimitDialog(
    initialMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initialMinutes?.toString().orEmpty()) }
    val minutes = text.toIntOrNull()
    val valid = minutes != null &&
        minutes in DailyLimit.MIN_CUSTOM_MINUTES..DailyLimit.MAX_CUSTOM_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_daily_limit_custom_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(3) },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                label = { Text(stringResource(R.string.settings_daily_limit_custom_dialog_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.settings_daily_limit_custom_dialog_hint,
                            DailyLimit.MIN_CUSTOM_MINUTES,
                            DailyLimit.MAX_CUSTOM_MINUTES,
                        ),
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { minutes?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.settings_daily_limit_custom_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_daily_limit_custom_dialog_cancel))
            }
        },
    )
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
private fun AppRow(
    app: AppToggleUi,
    onToggleWatch: (Boolean) -> Unit,
    onToggleNudge: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(app.displayName, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwitchWithLabel(
                label = stringResource(R.string.settings_app_watch),
                checked = app.watchedEnabled,
                enabled = true,
                onCheckedChange = onToggleWatch,
            )
            SwitchWithLabel(
                label = stringResource(R.string.settings_app_nudge),
                checked = app.blockingEnabled,
                // A nudge only makes sense while the app is watched.
                enabled = app.watchedEnabled,
                onCheckedChange = onToggleNudge,
            )
        }
        if (!app.watchedEnabled) {
            Text(
                text = stringResource(R.string.settings_app_unwatched_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchWithLabel(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
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
                defaultDailyLimit = DailyLimit.Minutes(30),
                apps = listOf(
                    AppToggleUi("com.facebook.katana", "Facebook", blockingEnabled = true),
                    AppToggleUi(
                        "com.instagram.android", "Instagram", blockingEnabled = false,
                        dailyLimit = DailyLimit.Minutes(10), dailyLimitIsOverride = true,
                    ),
                    AppToggleUi(
                        "com.google.android.youtube", "YouTube", blockingEnabled = true,
                        watchedEnabled = false,
                        dailyLimit = DailyLimit.Minutes(30), dailyLimitIsOverride = false,
                    ),
                ),
            ),
            onBack = {},
            onToggleIntervene = {},
            onToggleApp = { _, _ -> },
            onToggleWatchApp = { _, _ -> },
            onPickDefaultDailyLimit = {},
            onPickAppDailyLimit = { _, _ -> },
            onPickWindow = {},
            onPickRetention = {},
        )
    }
}
