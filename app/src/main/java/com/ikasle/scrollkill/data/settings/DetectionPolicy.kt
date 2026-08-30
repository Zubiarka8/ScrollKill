package com.ikasle.scrollkill.data.settings

/**
 * User-tunable policy behind the detection/blocking pipeline. These were compile-time
 * constants in [com.ikasle.scrollkill.blocking.BlockingEngine] and
 * [com.ikasle.scrollkill.session.SessionTracker]; they now live in [ScrollKillSettings] so
 * they can be changed without a rebuild. Every default here equals the constant it replaced,
 * so an install with nothing stored behaves exactly as before.
 *
 * Enum-of-presets rather than free values keeps storage and any future picker UI trivial and
 * parsing safe, matching [DailyLimit] / [StatsWindow] / [RetentionWindow]. The engine and
 * tracker stay framework- and settings-free: the AccessibilityService maps these to the plain
 * Float/Long the pipeline reads, the same seam already used for the daily budget.
 */

/**
 * Minimum detector confidence a [com.ikasle.scrollkill.detection.DetectionResult] must carry
 * to count as "on a watched surface" for both blocking and session tracking. [BALANCED] is
 * the value both stages shipped with.
 */
enum class ConfidenceFloor(val value: Float, val label: String) {
    LENIENT(0.40f, "Lenient"),
    BALANCED(0.60f, "Balanced"),
    STRICT(0.80f, "Strict"),
}

/**
 * How long the BlockingEngine stays quiet for a package after intervening, before it will
 * intervene again on the same continuous surface visit. [SEC_45] is the shipped value.
 */
enum class BlockingCooldown(val durationMs: Long, val label: String) {
    SEC_15(15_000L, "15 seconds"),
    SEC_30(30_000L, "30 seconds"),
    SEC_45(45_000L, "45 seconds"),
    SEC_60(60_000L, "1 minute"),
    SEC_120(120_000L, "2 minutes"),
}

/**
 * Idle gap after which the SessionTracker considers an open engagement finished. [SEC_15] is
 * the shipped value.
 */
enum class IdleTimeout(val durationMs: Long, val label: String) {
    SEC_10(10_000L, "10 seconds"),
    SEC_15(15_000L, "15 seconds"),
    SEC_30(30_000L, "30 seconds"),
    SEC_60(60_000L, "1 minute"),
}

/**
 * Shortest engagement the SessionTracker will emit; anything briefer is dropped as noise.
 * [SEC_1] is the shipped value.
 */
enum class MinSessionDuration(val durationMs: Long, val label: String) {
    SEC_1(1_000L, "1 second"),
    SEC_3(3_000L, "3 seconds"),
    SEC_5(5_000L, "5 seconds"),
    SEC_10(10_000L, "10 seconds"),
}
