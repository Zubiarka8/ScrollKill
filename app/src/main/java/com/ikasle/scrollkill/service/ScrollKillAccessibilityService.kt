package com.ikasle.scrollkill.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ikasle.scrollkill.BuildConfig
import com.ikasle.scrollkill.ScrollKillApp
import com.ikasle.scrollkill.blocking.BlockingDecision
import com.ikasle.scrollkill.blocking.BlockingEngine
import com.ikasle.scrollkill.blocking.DailyUsageMeter
import com.ikasle.scrollkill.data.session.SessionRepository
import com.ikasle.scrollkill.data.settings.SettingsRepository
import com.ikasle.scrollkill.data.settings.dailyLimitFor
import com.ikasle.scrollkill.data.settings.watchedPackagesFrom
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

// HAY QUE ELIMINAR (Session 10 battery profiling): payload for ui.settings.DebugDetectionPanel.
data class DebugUsage(val packageName: String, val usedMs: Long, val budgetMs: Long?)

// HAY QUE ELIMINAR (Session 13 detector token verify): raw signal tokens from the last
// snapshot, so detector token drift can be fixed off a real device. DEBUG builds only.
data class DebugTokens(
    val viewIds: List<String>,
    val classNames: List<String>,
    val contentDescriptions: List<String>,
    // HAY QUE ELIMINAR (Session 13 detector token verify): the snapshot's `texts` are
    // extracted but no detector reads them; several tokens the detectors check against
    // contentDescription ("For You", "Following", "Shorts", "What's on your mind") are far
    // likelier to surface as text. Without this line a human cannot tell real token drift
    // from a token checked against the wrong bucket.
    val texts: List<String>,
) {
    companion object {
        /**
         * Project the snapshot down to what the detectors actually match on, trimmed for a
         * screen readout. viewIds and classNames are pure structure (no user content).
         * text / contentDescription can carry user data, so only short, digit-free entries
         * are kept (structural labels like "For You" / "Following" survive; counts and
         * captions do not) and the list is capped.
         */
        fun from(snapshot: com.ikasle.scrollkill.detection.ScreenSnapshot): DebugTokens =
            DebugTokens(
                viewIds = snapshot.viewIds.sorted().take(MAX_LINES),
                classNames = snapshot.classNames.sorted().take(MAX_LINES),
                contentDescriptions = shortLabels(snapshot.contentDescriptions),
                // HAY QUE ELIMINAR (Session 13 detector token verify)
                texts = shortLabels(snapshot.texts),
            )

        /**
         * Keep only short, digit-free, distinct entries: structural labels survive, counts
         * and captions do not. Same filter for text and contentDescription so user content
         * stays out of the readout.
         */
        private fun shortLabels(values: List<String>): List<String> =
            values.asSequence()
                .map { it.trim() }
                .filter { it.length in 1..MAX_DESC_LEN }
                .filterNot { it.any(Char::isDigit) }
                .distinct()
                .take(MAX_DESC_LINES)
                .toList()

        private const val MAX_LINES = 80
        private const val MAX_DESC_LEN = 40
        private const val MAX_DESC_LINES = 40
    }
}

// HAY QUE ELIMINAR (Session 10 battery profiling): payload for ui.settings.DebugDetectionPanel.
data class DebugSnapshot(
    val foregroundPackage: String?,
    val matched: Boolean,
    val surface: String,
    val confidence: Float,
    val signals: String,
    val decision: String,
    val usage: List<DebugUsage>,
    // HAY QUE ELIMINAR (Session 13 detector token verify)
    val tokens: DebugTokens?,
)

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

    /** Every package the detectors can handle: the candidate set the user picks from. */
    private val screenDetector = ScreenDetector.default()
    private val eventFilter = EventFilter()
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

    /**
     * Packages currently observed: [screenDetector] candidates minus the user's unwatched set,
     * fed from [SettingsRepository]. Also pushed to the framework via [setServiceInfo] so most
     * events are filtered before they reach [onAccessibilityEvent]; this in-process check is
     * the backstop. Starts wide so no event is missed before the first settings emission.
     */
    @Volatile
    private var activeWatchedPackages: Set<String> = screenDetector.watchedPackages

    /** Whether [activeWatchedPackages] has been pushed to the framework via [setServiceInfo]. */
    private var watchedPushedToFramework = false

    // HAY QUE ELIMINAR (Session 13 detector token verify): raw tokens from the last snapshot.
    @Volatile
    private var lastDebugTokens: DebugTokens? = null

    private val _detection = MutableStateFlow<DetectionResult?>(null)

    /** Latest detection outcome. Null until the first watched surface is evaluated. */
    val detection: StateFlow<DetectionResult?> = _detection.asStateFlow()

    private val _blockingDecision = MutableStateFlow<BlockingDecision>(BlockingDecision.None)

    /** Latest decision. [BlockingDecision.None] until the first blockable surface is seen. */
    val blockingDecision: StateFlow<BlockingDecision> = _blockingDecision.asStateFlow()

    private val _lastCompletedSession = MutableStateFlow<Session?>(null)

    /** Most recently completed engagement. Null until the first session closes. */
    val lastCompletedSession: StateFlow<Session?> = _lastCompletedSession.asStateFlow()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as ScrollKillApp
        sessionRepository = app.sessionRepository
        settingsRepository = app.settingsRepository
        settingsRepository.settings
            .onEach { settings ->
                interveneEnabled = settings.interveneEnabled
                blockingEngine.blockingDisabledPackages = settings.blockingDisabledPackages

                // Detection/blocking policy: one confidence floor feeds both stages.
                val confidenceFloor = settings.detectionConfidenceFloor.value
                blockingEngine.minConfidence = confidenceFloor
                blockingEngine.cooldownMs = settings.blockingCooldown.durationMs
                sessionTracker.minConfidence = confidenceFloor
                sessionTracker.idleTimeoutMs = settings.sessionIdleTimeout.durationMs
                sessionTracker.minSessionDurationMs = settings.minSessionDuration.durationMs

                val watched = settings.watchedPackagesFrom(screenDetector.watchedPackages)
                if (watched != activeWatchedPackages || !watchedPushedToFramework) {
                    activeWatchedPackages = watched
                    watchedPushedToFramework = true
                    applyWatchedPackages(watched)
                }

                blockingEngine.dailyBudgetMsByPackage = watched
                    .mapNotNull { pkg ->
                        settings.dailyLimitFor(pkg).budgetMs?.let { pkg to it }
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

        // HAY QUE ELIMINAR (Session 10 battery profiling)
        if (BuildConfig.DEBUG) debugInstance = this

        debugLog("connected; ${screenDetector.watchedPackages.size} candidate packages")
    }

    /**
     * Narrow the framework-level event filter to [packages] via [setServiceInfo], starting
     * from the current config so the XML-declared event types and flags are preserved.
     * [AccessibilityServiceInfo.packageNames] is a dynamically configurable property; an
     * empty array means observe nothing. Called off the callback thread from the settings
     * collector; the framework method has no thread affinity.
     */
    private fun applyWatchedPackages(packages: Set<String>) {
        val info = serviceInfo ?: return
        info.packageNames = packages.toTypedArray()
        setServiceInfo(info)
        debugLog("observing ${packages.size}/${screenDetector.watchedPackages.size} packages")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Cheap early-outs first: this fires on the main thread for every event.
        val now = SystemClock.uptimeMillis()
        val processed = eventFilter.evaluate(
            packageName = event.packageName?.toString(),
            eventType = event.eventType,
            watchedPackages = activeWatchedPackages,
            nowMs = now,
        )
        if (processed !is EventFilter.Outcome.Process) return
        val pkg = processed.packageName

        val root: AccessibilityNodeInfo? = rootInActiveWindow
        val snapshot = snapshotExtractor.extract(
            pkg,
            root?.let(::AccessibilityNodeView),
            processed.fromWindowStateChange,
        )
        @Suppress("DEPRECATION")
        root?.recycle()

        val result = screenDetector.detect(snapshot)
        _detection.value = result
        // HAY QUE ELIMINAR (Session 13 detector token verify)
        if (BuildConfig.DEBUG) lastDebugTokens = DebugTokens.from(snapshot)
        if (result.isMatch) {
            debugLog("detected ${result.surface} in $pkg conf=${result.confidence}")
        }

        val decision = blockingEngine.decide(result, now)
        _blockingDecision.value = decision
        if (decision is BlockingDecision.Intervene) {
            debugLog("intervene ${decision.surface} in ${decision.packageName} conf=${decision.confidence}")
            if (interveneEnabled) performGlobalAction(GLOBAL_ACTION_BACK)
        }

        sessionTracker.track(result, decision, now)?.let { session ->
            _lastCompletedSession.value = session
            debugLog("session ended $session")
            serviceScope.launch { sessionRepository.record(session) }
        }
    }

    override fun onInterrupt() {
        // Required override. No queued work to cancel yet.
    }

    // HAY QUE ELIMINAR (Session 10 battery profiling): live state for ui.settings.DebugDetectionPanel.
    internal fun debugSnapshot(): DebugSnapshot {
        val now = SystemClock.uptimeMillis()
        val det = detection.value
        return DebugSnapshot(
            foregroundPackage = det?.packageName,
            matched = det?.isMatch == true,
            surface = (det?.surface ?: DetectionResult.Surface.UNKNOWN).name,
            confidence = det?.confidence ?: 0f,
            signals = det?.matchedSignals?.joinToString(",") { it.name }.orEmpty(),
            decision = when (val dec = blockingDecision.value) {
                is BlockingDecision.Intervene -> "Intervene(${dec.surface.name})"
                BlockingDecision.None -> "None"
            },
            usage = activeWatchedPackages.sorted().map { pkg ->
                DebugUsage(
                    packageName = pkg,
                    usedMs = blockingEngine.debugUsedMs(pkg, now),
                    budgetMs = blockingEngine.dailyBudgetMsByPackage[pkg],
                )
            },
            // HAY QUE ELIMINAR (Session 13 detector token verify)
            tokens = lastDebugTokens,
        )
    }

    /**
     * Debug-only logging. These lines name the foreground social app and its surface, plus
     * per-session engagement counts; that is user behavioural data and must never reach
     * logcat in a release build (CLAUDE.md: keep user data local, zero tracking). R8 is off,
     * so a compile-time [BuildConfig.DEBUG] guard is what strips them.
     */
    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    override fun onDestroy() {
        super.onDestroy()
        // HAY QUE ELIMINAR (Session 10 battery profiling)
        if (BuildConfig.DEBUG) debugInstance = null
        // HAY QUE ELIMINAR (Session 13 detector token verify)
        lastDebugTokens = null
        eventFilter.clear()
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

    // HAY QUE ELIMINAR (Session 10 battery profiling): restore `private companion object` when
    // the `debugInstance` field below is removed.
    companion object {
        // HAY QUE ELIMINAR (Session 10 battery profiling): running instance for the debug readout.
        @Volatile
        internal var debugInstance: ScrollKillAccessibilityService? = null

        private const val TAG = "ScrollKillA11y"
    }
}
