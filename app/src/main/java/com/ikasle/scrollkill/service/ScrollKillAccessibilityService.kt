package com.ikasle.scrollkill.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Entry point for cross-app detection.
 *
 * Kept deliberately thin (CLAUDE.md battery/perf rules): receive events, drop
 * anything not from a watched package immediately, debounce bursts, then hand
 * survivors to the detection pipeline. No full-tree traversal and no blocking
 * I/O in the callback.
 *
 * Event types, flags and feedback are configured in
 * res/xml/accessibility_service_config.xml, not here.
 */
class ScrollKillAccessibilityService : AccessibilityService() {

    // TODO(detection): move to user settings via setServiceInfo() once the
    // repository exists; for now this is the single source of truth.
    private val watchedPackages = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically", // TikTok (global build)
        "com.facebook.katana",
        "com.facebook.lite", // Facebook Lite
    )

    /** Last handled event time per package — backs the debounce below. */
    private val lastHandledMs = HashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "connected; watching ${watchedPackages.size} packages")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Cheap early-outs first: this fires on the main thread for every event.
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in watchedPackages) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> Unit
            else -> return
        }

        val now = SystemClock.uptimeMillis()
        if (now - (lastHandledMs[pkg] ?: 0L) < DEBOUNCE_MS) return
        lastHandledMs[pkg] = now

        // TODO(detection): EventFilter -> per-app detector -> DetectionResult
        // -> BlockingEngine. Keep everything off this method's hot path.
    }

    override fun onInterrupt() {
        // Required override. No queued work to cancel yet.
    }

    override fun onDestroy() {
        super.onDestroy()
        lastHandledMs.clear()
    }

    private companion object {
        const val TAG = "ScrollKillA11y"
        const val DEBOUNCE_MS = 250L
    }
}
