package com.ikasle.scrollkill.detection

/**
 * Per-app detector contract (the "AppDetector" stage in the CLAUDE.md pipeline).
 *
 * One implementation per third-party app so each can be updated independently when
 * that app's UI changes. Implementations must be pure: given a [ScreenSnapshot] they
 * return a [DetectionResult] and nothing else. They never act on it and never touch
 * the Android framework, which keeps them unit-testable.
 */
interface AppDetector {

    /** Package this detector understands, e.g. "com.instagram.android". */
    val targetPackage: String

    /**
     * Every package this detector handles. Defaults to just [targetPackage]; a detector
     * that covers more than one build of the same app (e.g. Facebook plus Facebook Lite)
     * overrides this and treats [targetPackage] as the primary.
     */
    val targetPackages: Set<String> get() = setOf(targetPackage)

    /**
     * Evaluate one screen state. Must be side-effect free.
     *
     * Return [DetectionResult.none] when the snapshot is not for one of [targetPackages]
     * or no watched surface is recognised.
     */
    fun detect(snapshot: ScreenSnapshot): DetectionResult
}
