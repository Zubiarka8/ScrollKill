package com.ikasle.scrollkill.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ikasle.scrollkill.ScrollKillApp
import com.ikasle.scrollkill.blocking.BlockingDecision
import com.ikasle.scrollkill.blocking.BlockingEngine
import com.ikasle.scrollkill.blocking.DailyUsageMeter
import com.ikasle.scrollkill.data.session.SessionRepository
import com.ikasle.scrollkill.data.settings.SettingsRepository
import com.ikasle.scrollkill.detection.DetectionResult
import com.ikasle.scrollkill.detection.ScreenDetector
import com.ikasle.scrollkill.session.Session
import com.ikasle.scrollkill.session.SessionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Entry point for cross-app detection.
 *
 * Kept deliberately thin (CLAUDE.md battery/perf rules): receive events, drop
 * anything not from a watched package immediately, debounce bursts, then hand
 * survivors to the detection pipeline. The tree traversal in [SnapshotExtractor]
 * is bounded; no blocking I/O runs in the callback.
 *
 * Pipeline: this service (EventFilter) -> [SnapshotExtractor] -> [ScreenDetector]
 * -> per-app detector -> [DetectionResult] -> [BlockingEngine] -> [BlockingDecision],
 * with [SessionTracker] folding the same stream into [Session] records that
 * [SessionRepository] persists off-thread. Detection and blocking stay separate systems;
 * this service is the only place that acts on a decision (a single BACK press), gated by
 * [interveneEnabled] (from [SettingsRepository]).
 *
 * Event types, flags and feedback are configured in
 * res/xml/accessibility_service_config.xml, not here.
 */
class ScrollKillAccessibilityService : AccessibilityService() {

    // TODO(detection): make the watched set user-configurable via setServiceInfo()
    // once a settings UI exists. For now it is derived from the detectors.
    private val screenDetector = ScreenDetector.default()
    private val snapshotExtractor = SnapshotExtractor()
    private val blockingEngine = BlockingEngine()
    private val sessionTracker = SessionTracker()

    /** Off-callback work: persistence writes and the settings collector. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var sessionRepository: SessionRepository
    private lateinit var settingsRepository: SettingsRepository

    /** Master intervention switch, fed from [SettingsRepository]; read on the callback thread. */
    @Volatile
    private var interveneEnabled = true

    private val _detection = MutableStateFlow<DetectionResult?>(null)

    /** Latest detection outcome. Null until the first watched surface is evaluated. */
    val detection: StateFlow<DetectionResult?> = _detection.asStateFlow()

    private val _blockingDecision = MutableStateFlow<BlockingDecision>(BlockingDecision.None)

    /** Latest decision. [BlockingDecision.None] until the first blockable surface is seen. */
    val blockingDecision: StateFlow<BlockingDecision> = _blockingDecision.asStateFlow()

    private val _lastCompletedSession = MutableStateFlow<Session?>(null)

    /** Most recently completed engagement. Null until the first session closes. */
    val lastCompletedSession: StateFlow<Session?> = _lastCompletedSession.asStateFlow()

    /** Last handled event time per package — backs the debounce below. */
    private val lastHandledMs = HashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as ScrollKillApp
        sessionRepository = app.sessionRepository
        settingsRepository = app.settingsRepository
        settingsRepository.settings
            .onEach { settings ->
                interveneEnabled = settings.interveneEnabled
                blockingEngine.blockingDisabledPackages = settings.blockingDisabledPackages
                blockingEngine.dailyBudgetMsByPackage = screenDetector.watchedPackages
                    .mapNotNull { pkg ->
                        val limit = settings.dailyLimitOverrides[pkg] ?: settings.defaultDailyLimit
                        limit.budgetMs?.let { pkg to it }
                    }
                    .toMap()
                sessionRepository.retentionMs = settings.historyRetention.durationMs
            }
            .launchIn(serviceScope)

        // Seed the daily-usage meter from persisted history so a restart does not reset budgets.
        serviceScope.launch {
            val since = System.currentTimeMillis() - DailyUsageMeter.WINDOW_MS
            val used = sessionRepository.perAppUsageSince(since)
                .associate { it.packageName to it.totalDurationMs }
            blockingEngine.seedUsage(used, SystemClock.uptimeMillis())
        }

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

        sessionTracker.track(result, decision, now)?.let { session ->
            _lastCompletedSession.value = session
            Log.d(TAG, "session ended $session")
            serviceScope.launch { sessionRepository.record(session) }
        }
    }

    override fun onInterrupt() {
        // Required override. No queued work to cancel yet.
    }

    override fun onDestroy() {
        super.onDestroy()
        lastHandledMs.clear()
        blockingEngine.reset()
        // Persist the session in progress before we lose it. One row on teardown, so a
        // brief blocking write here is acceptable.
        if (::sessionRepository.isInitialized) {
            sessionTracker.flush(SystemClock.uptimeMillis())?.let { session ->
                runBlocking { sessionRepository.record(session) }
            }
        }
        sessionTracker.reset()
        serviceScope.cancel()
        _detection.value = null
        _blockingDecision.value = BlockingDecision.None
        _lastCompletedSession.value = null
    }

    private companion object {
        const val TAG = "ScrollKillA11y"
        const val DEBOUNCE_MS = 250L
    }
}
