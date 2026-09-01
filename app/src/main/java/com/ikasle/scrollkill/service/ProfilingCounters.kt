package com.ikasle.scrollkill.service

import android.os.SystemClock

// HAY QUE ELIMINAR (D3 profiling harness): the whole file.
// Cheap primitive counters for the on-device battery profiling run (checklist section D /
// Session 10 block 10.2, steps A-D). It measures the accessibility callback cost without an
// attached profiler so the numbers can be read straight from `adb logcat -s ScrollKillA11y`.
//
// Wired into ScrollKillAccessibilityService only inside `if (BuildConfig.DEBUG)` branches;
// in release every call site is dead and this class is unreachable (R8 is off, so it still
// ships in the APK until deleted - same situation as the Session 10 debug card, see 10.4).
//
// Deliberately allocation-free on the hot path: every hook is a single `long` increment.
// The only allocation is one log String at most once per second, in [maybeFlush].
// Not thread-safe by design: every method is called from the single accessibility callback
// thread, except construction.

/**
 * Rolling ~1 Hz counters for one accessibility-callback measurement window.
 *
 * @param log sink for the periodic line (the service passes its `debugLog`, so the line is
 *   tagged `ScrollKillA11y` and already gated on `BuildConfig.DEBUG`).
 * @param clockMs monotonic clock for the flush cadence; defaults to uptime millis.
 */
class ProfilingCounters(
    private val log: (String) -> Unit,
    private val clockMs: () -> Long = SystemClock::uptimeMillis,
) {

    private var windowStartMs = 0L

    /** Events entering onAccessibilityEvent, before EventFilter (pre-debounce volume). */
    private var rawEvents = 0L

    /** Events that survived EventFilter (post-debounce volume). */
    private var passedEvents = 0L

    /** `rootInActiveWindow` reads. */
    private var rootReads = 0L

    /** `NodeView.child(i)` calls = getChild IPC attempts, summed over the window. */
    private var childCalls = 0L

    /** SnapshotExtractor.extract invocations in the window. */
    private var extractCount = 0L
    private var extractTotalNanos = 0L
    private var extractMaxNanos = 0L

    fun onRawEvent() {
        rawEvents++
    }

    fun onPassedFilter() {
        passedEvents++
    }

    fun onRootRead() {
        rootReads++
    }

    fun onChildCall() {
        childCalls++
    }

    fun onExtract(wallNanos: Long) {
        extractCount++
        extractTotalNanos += wallNanos
        if (wallNanos > extractMaxNanos) extractMaxNanos = wallNanos
    }

    /**
     * Call once at the end of every handled event. Emits one line and resets the window when
     * at least [FLUSH_INTERVAL_MS] has elapsed since the last emission; otherwise does nothing.
     *
     * The line is raw counts plus the window length in millis - no float formatting, no
     * locale. Derive the rates in the README's step A/B notes:
     *   raw/s      = raw / (windowMs / 1000)
     *   passed/s   = passed / (windowMs / 1000)
     *   drop ratio = 1 - passed / raw
     *   getChild/event  = getChild / extractN
     *   extract mean ms = extractTotalUs / extractN / 1000
     *
     * @param nowMs monotonic now; defaults to [clockMs]. The service passes the same
     *   `SystemClock.uptimeMillis()` it already read for EventFilter.
     */
    fun maybeFlush(nowMs: Long = clockMs()) {
        if (windowStartMs == 0L) {
            windowStartMs = nowMs
            return
        }
        val windowMs = nowMs - windowStartMs
        if (windowMs < FLUSH_INTERVAL_MS) return

        log(
            "profile window=${windowMs}ms raw=$rawEvents passed=$passedEvents " +
                "rootReads=$rootReads getChild=$childCalls extractN=$extractCount " +
                "extractTotalUs=${extractTotalNanos / NANOS_PER_MICRO} " +
                "extractMaxUs=${extractMaxNanos / NANOS_PER_MICRO}",
        )

        windowStartMs = nowMs
        rawEvents = 0
        passedEvents = 0
        rootReads = 0
        childCalls = 0
        extractCount = 0
        extractTotalNanos = 0
        extractMaxNanos = 0
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 1_000L
        const val NANOS_PER_MICRO = 1_000L
    }
}

// HAY QUE ELIMINAR (D3 profiling harness): [NodeView] decorator that ticks [onChildCall] once
// per `child(i)` call so [ProfilingCounters] can count getChild IPC attempts per event
// without touching [SnapshotExtractor]. The service wraps the root in this only under
// `BuildConfig.DEBUG`; release keeps the bare [AccessibilityNodeView]. Adds one wrapper alloc
// per visited node on top of [AccessibilityNodeView] - so subtract that when reading the
// allocation numbers in checklist step C, or take step C from a build with the harness out.
class CountingNodeView(
    private val delegate: NodeView,
    private val onChildCall: () -> Unit,
) : NodeView {
    override val packageName: CharSequence? get() = delegate.packageName
    override val viewId: String? get() = delegate.viewId
    override val className: CharSequence? get() = delegate.className
    override val text: CharSequence? get() = delegate.text
    override val contentDescription: CharSequence? get() = delegate.contentDescription
    override val childCount: Int get() = delegate.childCount

    override fun child(index: Int): NodeView? {
        onChildCall()
        return delegate.child(index)?.let { CountingNodeView(it, onChildCall) }
    }

    override fun recycle() = delegate.recycle()
}
