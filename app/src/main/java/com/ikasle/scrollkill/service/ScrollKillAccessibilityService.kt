package com.ikasle.scrollkill.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ikasle.scrollkill.blocking.BlockingDecision
import com.ikasle.scrollkill.blocking.BlockingEngine
import com.ikasle.scrollkill.detection.DetectionResult
import com.ikasle.scrollkill.detection.ScreenDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Entry point for cross-app detection.
 *
 * Kept deliberately thin (CLAUDE.md battery/perf rules): receive events, drop
 * anything not from a watched package immediately, debounce bursts, then hand
 * survivors to the detection pipeline. The tree traversal in [SnapshotExtractor]
 * is bounded; no blocking I/O runs in the callback.
 *
 * Pipeline: this service (EventFilter) -> [SnapshotExtractor] -> [ScreenDetector]
 * -> per-app detector -> [DetectionResult] -> [BlockingEngine] -> [BlockingDecision].
 * Detection and blocking stay separate systems; this service is the only place that acts
 * on a decision (a single BACK press), gated by [interveneEnabled].
 *
 * Event types, flags and feedback are configured in
 * res/xml/accessibility_service_config.xml, not here.
 */
class ScrollKillAccessibilityService : AccessibilityService() {

    // TODO(detection): make the watched set user-configurable via setServiceInfo()
    // once the settings repository exists. For now it is derived from the detectors.
    private val screenDetector = ScreenDetector.default()
    private val snapshotExtractor = SnapshotExtractor()
    private val blockingEngine = BlockingEngine()

    // TODO(settings): replace with a user-facing toggle once settings exist.
    private val interveneEnabled = true

    private val _detection = MutableStateFlow<DetectionResult?>(null)

    /** Latest detection outcome. Null until the first watched surface is evaluated. */
    val detection: StateFlow<DetectionResult?> = _detection.asStateFlow()

    private val _blockingDecision = MutableStateFlow<BlockingDecision>(BlockingDecision.None)

    /** Latest decision. [BlockingDecision.None] until the first blockable surface is seen. */
    val blockingDecision: StateFlow<BlockingDecision> = _blockingDecision.asStateFlow()

    /** Last handled event time per package — backs the debounce below. */
    private val lastHandledMs = HashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "connected; watching ${screenDetector.watchedPackages.size} packages")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Cheap early-outs first: this fires on the main thread for every event.
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in screenDetector.watchedPackages) return

        val fromWindowStateChange = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> true
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> false
            else -> return
        }

        val now = SystemClock.uptimeMillis()
        if (now - (lastHandledMs[pkg] ?: 0L) < DEBOUNCE_MS) return
        lastHandledMs[pkg] = now

        val root: AccessibilityNodeInfo? = rootInActiveWindow
        val snapshot = snapshotExtractor.extract(pkg, root, fromWindowStateChange)
        @Suppress("DEPRECATION")
        root?.recycle()

        val result = screenDetector.detect(snapshot)
        _detection.value = result
        if (result.isMatch) {
            Log.d(TAG, "detected ${result.surface} in $pkg conf=${result.confidence}")
        }

        val decision = blockingEngine.decide(result, now)
        _blockingDecision.value = decision
        if (decision is BlockingDecision.Intervene) {
            Log.d(TAG, "intervene ${decision.surface} in ${decision.packageName} conf=${decision.confidence}")
            if (interveneEnabled) performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {
        // Required override. No queued work to cancel yet.
    }

    override fun onDestroy() {
        super.onDestroy()
        lastHandledMs.clear()
        blockingEngine.reset()
        _detection.value = null
        _blockingDecision.value = BlockingDecision.None
    }

    private companion object {
        const val TAG = "ScrollKillA11y"
        const val DEBOUNCE_MS = 250L
    }
}
