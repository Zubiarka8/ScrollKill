package com.ikasle.scrollkill.ui.settings

// HAY QUE ELIMINAR (Session 10 battery profiling): temporary on-device detection readout.
// The whole file is scaffolding added to diagnose "TikTok never blocked" without a stable
// adb link. Delete it together with every block marked
// "// HAY QUE ELIMINAR (Session 10 battery profiling)" in ScrollKillAccessibilityService,
// BlockingEngine, SettingsScreen and MainActivity. Tracked in .claude/checklist.md (10.4).

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ikasle.scrollkill.service.DebugSnapshot
import com.ikasle.scrollkill.service.ScrollKillAccessibilityService
import com.ikasle.scrollkill.ui.home.KnownApps
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Debug-only card at the bottom of Settings. Polls the running AccessibilityService once a
 * second for its last [DetectionResult] / [BlockingDecision] and each watched app's metered
 * daily-limit time, and copies the same text as a plain block on tap. No effect on release
 * builds: the call site in MainActivity only passes this in when BuildConfig.DEBUG.
 */
// LocalClipboardManager is deprecated for LocalClipboard (suspend); the sync API is fine for
// this throwaway panel and keeps it to one call site.
@Suppress("DEPRECATION")
@Composable
fun DebugDetectionPanel(modifier: Modifier = Modifier) {
    var snapshot by remember {
        mutableStateOf(ScrollKillAccessibilityService.debugInstance?.debugSnapshot())
    }
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = ScrollKillAccessibilityService.debugInstance?.debugSnapshot()
            delay(POLL_MS)
        }
    }

    val clipboard = LocalClipboardManager.current
    val text = renderSnapshot(snapshot)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "DEBUG - HAY QUE ELIMINAR (Session 10)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Button(onClick = { clipboard.setText(AnnotatedString(text)) }) {
            Text("Copy diagnostics")
        }
    }
}

private const val POLL_MS = 1_000L

private fun renderSnapshot(s: DebugSnapshot?): String {
    if (s == null) {
        return "AccessibilityService not running.\nEnable it, scroll the app, then reopen this screen."
    }
    val b = StringBuilder()
    b.appendLine("foreground : ${s.foregroundPackage ?: "-"}")
    b.appendLine(
        "last match : ${if (s.matched) s.surface else "none"}  " +
            "(conf ${String.format(Locale.US, "%.2f", s.confidence)})",
    )
    b.appendLine("signals    : ${s.signals.ifEmpty { "-" }}")
    b.appendLine("decision   : ${s.decision}")
    if (s.usage.isEmpty()) {
        b.appendLine("usage      : (no watched apps)")
    } else {
        for (u in s.usage) {
            val budget = u.budgetMs?.let(::formatMs) ?: "no limit"
            b.appendLine("${KnownApps.label(u.packageName)} : ${formatMs(u.usedMs)} / $budget")
        }
    }
    // HAY QUE ELIMINAR (Session 13 detector token verify): raw signals from the last snapshot.
    val t = s.tokens
    if (t == null) {
        b.appendLine()
        b.appendLine("tokens     : (none yet - scroll the app's feed, then reopen)")
    } else {
        b.appendLine()
        b.appendLine("--- last snapshot tokens (${s.foregroundPackage ?: "-"}) ---")
        b.appendLine("viewIds (${t.viewIds.size}):")
        t.viewIds.forEach { b.appendLine("  $it") }
        b.appendLine("classNames (${t.classNames.size}):")
        t.classNames.forEach { b.appendLine("  $it") }
        b.appendLine("contentDescriptions (${t.contentDescriptions.size}, digit-free <=40ch):")
        t.contentDescriptions.forEach { b.appendLine("  $it") }
        // HAY QUE ELIMINAR (Session 13 detector token verify): texts are extracted but no
        // detector reads them; needed to tell token drift from a wrong-bucket token.
        b.appendLine("texts (${t.texts.size}, digit-free <=40ch):")
        t.texts.forEach { b.appendLine("  $it") }
    }
    return b.toString().trimEnd()
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1_000
    return "${totalSec / 60}m ${(totalSec % 60).toString().padStart(2, '0')}s"
}
