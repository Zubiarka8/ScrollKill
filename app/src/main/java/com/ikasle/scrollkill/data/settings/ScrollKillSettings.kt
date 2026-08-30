package com.ikasle.scrollkill.data.settings

/**
 * User preferences, read as an immutable snapshot. A data class so fields can be added
 * without breaking callers; every field must have a default matching "unset".
 */
data class ScrollKillSettings(
    /** Master switch for the BACK-press intervention. */
    val interveneEnabled: Boolean = true,
    /** Packages the user has turned intervention OFF for; sessions are still tracked. */
    val blockingDisabledPackages: Set<String> = emptySet(),
    /**
     * Detector-supported packages the user has turned observation OFF for. Absent = watched.
     * The AccessibilityService ignores these entirely: no detection, session tracking or
     * daily-limit enforcement, and they are dropped from its [packageNames] filter.
     */
    val watchingDisabledPackages: Set<String> = emptySet(),
    /** Daily budget applied to every watched app unless [dailyLimitOverrides] says otherwise. */
    val defaultDailyLimit: DailyLimit = DailyLimit.OFF,
    /** Per-app daily budget that wins over [defaultDailyLimit]. Absent = use the default. */
    val dailyLimitOverrides: Map<String, DailyLimit> = emptyMap(),
    /**
     * Minimum detector confidence for a surface to count, shared by the BlockingEngine and the
     * SessionTracker (both shipped at 0.60). See [DetectionPolicy][ConfidenceFloor].
     */
    val detectionConfidenceFloor: ConfidenceFloor = ConfidenceFloor.BALANCED,
    /** Quiet period after a BlockingEngine intervention before it may fire again (shipped 45s). */
    val blockingCooldown: BlockingCooldown = BlockingCooldown.SEC_45,
    /** Idle gap that ends an open SessionTracker engagement (shipped 15s). */
    val sessionIdleTimeout: IdleTimeout = IdleTimeout.SEC_15,
    /** Shortest engagement the SessionTracker will emit; briefer ones are dropped (shipped 1s). */
    val minSessionDuration: MinSessionDuration = MinSessionDuration.SEC_1,
    /** Rolling window the home screen aggregates over. */
    val statsWindow: StatsWindow = StatsWindow.LAST_7_DAYS,
    /** How long session history is kept. */
    val historyRetention: RetentionWindow = RetentionWindow.DAYS_90,
    /**
     * The first-run rationale screen has been shown and the user has made an affirmative
     * choice on it. Gates the pre-permission disclosure required for the AccessibilityService
     * (Google Play "Use of the AccessibilityService API" policy). False = show onboarding.
     */
    val onboardingComplete: Boolean = false,
)

/**
 * The packages the AccessibilityService should observe: the detector [candidates] minus the
 * ones the user has unwatched. An empty result means observe nothing. Pure so the service
 * wiring stays unit-testable.
 */
fun ScrollKillSettings.watchedPackagesFrom(candidates: Set<String>): Set<String> =
    candidates - watchingDisabledPackages
