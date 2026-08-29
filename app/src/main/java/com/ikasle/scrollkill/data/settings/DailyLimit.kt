package com.ikasle.scrollkill.data.settings

/**
 * Preset per-app daily time budgets for infinite-content surfaces, evaluated over a rolling
 * 24-hour window by [com.ikasle.scrollkill.blocking.DailyUsageMeter]. [OFF] means no budget:
 * the BlockingEngine blocks the surface on sight (the pre-daily-limits behavior).
 *
 * Enum-of-presets rather than a free duration keeps storage and the future picker UI trivial
 * and parsing safe, matching [StatsWindow] / [RetentionWindow].
 */
enum class DailyLimit(val budgetMs: Long?, val label: String) {
    OFF(null, "No limit"),
    MIN_5(5L * 60 * 1000, "5 min/day"),
    MIN_10(10L * 60 * 1000, "10 min/day"),
    MIN_15(15L * 60 * 1000, "15 min/day"),
    MIN_30(30L * 60 * 1000, "30 min/day"),
    MIN_60(60L * 60 * 1000, "1 hour/day"),
}

/**
 * The daily budget in effect for [packageName]: its per-app override if set, otherwise the
 * global default. Single source of truth for the BlockingEngine wiring and the home screen.
 */
fun ScrollKillSettings.dailyLimitFor(packageName: String): DailyLimit =
    dailyLimitOverrides[packageName] ?: defaultDailyLimit
