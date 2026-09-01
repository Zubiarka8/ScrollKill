package com.ikasle.scrollkill.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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
// HAY QUE ELIMINAR (Session 10 battery profiling): the `debugPanel` param changes this
// function's signature, so the detekt baseline entries for its pre-existing LongParameterList
// (9 params) and LongMethod findings no longer match. Drop this @Suppress with the rest of
// the debug card (checklist 10.4); the baseline covers the function again once it does.
@Suppress("LongParameterList", "LongMethod")
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyLimitHeader()

                LimitRow(
                    leading = { Box(Modifier.size(40.dp)) },
                    title = stringResource(R.string.settings_daily_limit_default_row),
                    control = {
                        LimitControl(
                            value = state.defaultDailyLimit,
                            defaultLabel = null,
                            changeDescription = stringResource(
                                R.string.settings_daily_limit_change,
                                stringResource(R.string.settings_daily_limit_default_row),
                            ),
                            onUseDefault = {},
                            onPickPreset = onPickDefaultDailyLimit,
                            onPickCustom = { customTarget = CustomLimitTarget.Default },
                        )
                    },
                    trailing = {},
                )

                val limitApps = state.apps.filter { it.watchedEnabled }
                limitApps.forEach { app ->
                    HorizontalDivider()
                    LimitRow(
                        leading = { AppAvatar(app.packageName, app.displayName) },
                        title = app.displayName,
                        control = {
                            LimitControl(
                                value = app.dailyLimit,
                                defaultLabel = compactLimitLabel(state.defaultDailyLimit),
                                changeDescription = stringResource(
                                    R.string.settings_daily_limit_change,
                                    app.displayName,
                                ),
                                onUseDefault = { onPickAppDailyLimit(app.packageName, null) },
                                onPickPreset = { onPickAppDailyLimit(app.packageName, it) },
                                onPickCustom = {
                                    customTarget = CustomLimitTarget.App(app.packageName)
                                },
                            )
                        },
                        trailing = {
                            val enforceDescription = stringResource(
                                R.string.settings_daily_limit_enforce,
                                app.displayName,
                            )
                            Switch(
                                checked = app.blockingEnabled,
                                onCheckedChange = { onToggleApp(app.packageName, it) },
                                modifier = Modifier.semantics {
                                    contentDescription = enforceDescription
                                },
                            )
                        },
                    )
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

/** Section heading for the daily-limits list: a tinted badge, a title and a one-line hint. */
@Composable
private fun DailyLimitHeader() {
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("⏱", style = MaterialTheme.typography.titleMedium)
        }
        Column {
            Text(
                stringResource(R.string.settings_section_daily_limit),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.settings_daily_limit_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One line in the daily-limits list: [leading] (app icon or a spacer), [title] (app name),
 * then the [control] pill and an optional [trailing] switch, right-aligned.
 */
@Composable
private fun LimitRow(
    leading: @Composable () -> Unit,
    title: String,
    control: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
        )
        control()
        trailing()
    }
}

/**
 * The tappable limit pill plus its dropdown. Shows the compact form of [value]; the menu
 * offers "Use default" (per-app rows only, when [defaultLabel] is non-null), the
 * [DailyLimit.PRESETS] and "Custom…".
 */
@Composable
private fun LimitControl(
    value: DailyLimit,
    defaultLabel: String?,
    changeDescription: String,
    onUseDefault: () -> Unit,
    onPickPreset: (DailyLimit) -> Unit,
    onPickCustom: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable { open = true }
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = changeDescription },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(compactLimitLabel(value), style = MaterialTheme.typography.labelLarge)
            Text(
                "▾",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (defaultLabel != null) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.settings_daily_limit_use_default, defaultLabel))
                    },
                    onClick = { open = false; onUseDefault() },
                )
            }
            DailyLimit.PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(compactLimitLabel(preset)) },
                    onClick = { open = false; onPickPreset(preset) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_daily_limit_custom)) },
                onClick = { open = false; onPickCustom() },
            )
        }
    }
}

/** Circular app icon from the installed package; a coloured monogram when it is not installed. */
@Composable
private fun AppAvatar(packageName: String, displayName: String) {
    val context = LocalContext.current
    val icon: ImageBitmap? = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = AVATAR_PX, height = AVATAR_PX)
                .asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Text(
                displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Compact limit caption for the pill and the menu: "No limit", "15 min", "1h", "1h 30m". */
@Composable
private fun compactLimitLabel(limit: DailyLimit): String = when (limit) {
    DailyLimit.Off -> stringResource(R.string.settings_daily_limit_pill_off)
    is DailyLimit.Minutes -> {
        val h = limit.value / MINUTES_PER_HOUR
        val m = limit.value % MINUTES_PER_HOUR
        when {
            h == 0 -> stringResource(R.string.settings_daily_limit_pill_minutes, m)
            m == 0 -> stringResource(R.string.settings_daily_limit_pill_hours, h)
            else -> stringResource(R.string.settings_daily_limit_pill_hours_minutes, h, m)
        }
    }
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

private const val AVATAR_PX = 144
private const val MINUTES_PER_HOUR = 60

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
