package com.ikasle.scrollkill.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ikasle.scrollkill.ui.theme.ScrollKillTheme

/**
 * Stateless home screen: a "today" summary with daily-limit progress, the accessibility-service
 * status, the intervention toggle and the per-app usage over the chosen stats window. All state
 * comes from [HomeViewModel] via [state]; the three lambdas are the only side effects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onToggleIntervene: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("ScrollKill") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TodayCard(state)
            ServiceStatusCard(state.serviceEnabled, onOpenAccessibilitySettings)
            InterveneToggleRow(state.interveneEnabled, onToggleIntervene)
            HistoryCard(state)
        }
    }
}

@Composable
private fun TodayCard(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = state.todayTotalDuration.ifEmpty { "0s" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Feed time today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.todayApps.isEmpty()) {
                Text(
                    text = "No feed time today yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                state.todayApps.forEach { app -> TodayAppRow(app) }
            }
        }
    }
}

@Composable
private fun TodayAppRow(app: TodayAppUi) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(app.displayName, fontWeight = FontWeight.Medium)
            Text(app.usedToday, style = MaterialTheme.typography.bodyMedium)
        }
        if (app.progress != null) {
            LinearProgressIndicator(
                progress = { app.progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (app.overLimit) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Text(
            text = app.limitCaption,
            style = MaterialTheme.typography.bodySmall,
            color = if (app.overLimit) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ServiceStatusCard(enabled: Boolean, onOpenSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (enabled) "Detection active" else "Detection is off",
                style = MaterialTheme.typography.titleMedium,
            )
            if (enabled) {
                Text(
                    text = "ScrollKill can see when an infinite feed opens.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = "ScrollKill needs the accessibility service to notice feeds. " +
                        "Nothing is stored or sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenSettings) { Text("Open Accessibility settings") }
            }
        }
    }
}

@Composable
private fun InterveneToggleRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 16.dp)) {
            Text("Nudge me off feeds", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Press Back automatically when a feed is detected.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HistoryCard(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(state.windowLabel, style = MaterialTheme.typography.titleMedium)

            when {
                state.loading -> {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator()
                }

                state.apps.isEmpty() -> {
                    Text(
                        text = "No feed time recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    Text(
                        text = "${state.totalDuration} total",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    state.apps.forEachIndexed { index, app ->
                        if (index > 0) HorizontalDivider()
                        AppUsageRow(app)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(app: AppUsageUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(app.displayName, fontWeight = FontWeight.Medium)
            Text(
                text = "${app.sessionCount} sessions - ${app.interventionCount} nudges",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(app.duration, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ScrollKillTheme {
        HomeScreen(
            state = HomeUiState(
                loading = false,
                serviceEnabled = false,
                interveneEnabled = true,
                todayTotalDuration = "37m",
                todayApps = listOf(
                    TodayAppUi(
                        packageName = "com.instagram.android",
                        displayName = "Instagram",
                        usedToday = "22m",
                        limitCaption = "22m / 30m",
                        progress = 0.73f,
                        overLimit = false,
                    ),
                    TodayAppUi(
                        packageName = "com.zhiliaoapp.musically",
                        displayName = "TikTok",
                        usedToday = "15m",
                        limitCaption = "15m / 10m - over",
                        progress = 1f,
                        overLimit = true,
                    ),
                    TodayAppUi(
                        packageName = "com.google.android.youtube",
                        displayName = "YouTube",
                        usedToday = "0s",
                        limitCaption = "No limit set",
                        progress = null,
                        overLimit = false,
                    ),
                ),
                windowLabel = "Last 7 days",
                totalDuration = "1h 12m",
                apps = listOf(
                    AppUsageUi("com.instagram.android", "Instagram", "48m", 6, 4),
                    AppUsageUi("com.google.android.youtube", "YouTube", "24m", 3, 1),
                ),
            ),
            onToggleIntervene = {},
            onOpenAccessibilitySettings = {},
            onOpenSettings = {},
        )
    }
}
