package com.ikasle.scrollkill.blocking

import com.ikasle.scrollkill.detection.DetectionResult
import com.ikasle.scrollkill.detection.DetectionResult.Surface

/**
 * The "BlockingEngine" stage of the CLAUDE.md pipeline: turns a [DetectionResult] into a
 * [BlockingDecision].
 *
 * Pure and framework-free (unit-testable). It never performs the action itself; the
 * AccessibilityService does. Its state is a small per-package memory used to avoid nagging
 * (after a [BlockingDecision.Intervene] for a package it stays quiet until either [cooldownMs]
 * elapses or that package's watched surface goes away and later returns) plus a
 * [DailyUsageMeter] for the daily budget.
 *
 * Daily budget: while a package's metered time on a watched surface over the last 24h is
 * under [dailyBudgetMsByPackage]'s value for it, [decide] returns [BlockingDecision.None] even
 * on a blockable surface. A package absent from that map has no budget and is blocked on
 * sight, the pre-daily-limits behavior.
 *
 * Not thread-safe: [decide] is meant to be called from the single accessibility callback
 * thread. [DailyUsageMeter] is separately synchronized because [seedUsage] runs off that
 * thread.
 */
class BlockingEngine(
    private val blockableSurfaces: Set<Surface> = DEFAULT_BLOCKABLE_SURFACES,
    minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val usageMeter: DailyUsageMeter = DailyUsageMeter(),
) {

    /**
     * Confidence floor and anti-nag cooldown, seeded from the constructor defaults and then
     * kept in sync with the settings repository by the AccessibilityService collector (a
     * different thread). Plain-value swaps behind [Volatile], exactly like
     * [blockingDisabledPackages] / [dailyBudgetMsByPackage]; a read on the callback thread
     * that is one settings emission stale is harmless.
     */
    @Volatile
    var minConfidence: Float = minConfidence

    @Volatile
    var cooldownMs: Long = cooldownMs

    private data class PackageState(
        val lastInterveneMs: Long,
        val onSurface: Boolean,
    )

    private val stateByPackage = HashMap<String, PackageState>()

    /**
     * Packages the user has turned intervention off for. Written from the settings
     * collector (a different thread) and read on the callback thread; an immutable-set swap
     * plus [Volatile] is enough. A disabled package is treated exactly like a
     * non-blockable surface.
     */
    @Volatile
    var blockingDisabledPackages: Set<String> = emptySet()

    /**
     * Effective daily budget (milliseconds) per package. Only packages with a real limit are
     * present; an absent package has no budget. Written from the settings collector, read on
     * the callback thread; immutable-map swap plus [Volatile], same as [blockingDisabledPackages].
     */
    @Volatile
    var dailyBudgetMsByPackage: Map<String, Long> = emptyMap()

    /**
     * @param nowMs a monotonic clock reading (e.g. SystemClock.uptimeMillis()).
     */
    fun decide(result: DetectionResult, nowMs: Long): BlockingDecision {
        markOtherPackagesLeft(result.packageName)

        val pkg = result.packageName
        val onWatchedSurface = result.isMatch &&
            result.surface in blockableSurfaces &&
            result.confidence >= minConfidence

        // Meter every watched-surface view - even with blocking off or no budget set - so a
        // budget added later starts from real history.
        if (onWatchedSurface) usageMeter.record(pkg, nowMs)

        val blockable = onWatchedSurface && pkg !in blockingDisabledPackages
        if (!blockable) {
            stateByPackage[pkg]?.let { stateByPackage[pkg] = it.copy(onSurface = false) }
            return BlockingDecision.None
        }

        // Within the daily allowance: stay out of the way (the surface is still metered above).
        val budgetMs = dailyBudgetMsByPackage[pkg]
        if (budgetMs != null && usageMeter.usedMs(pkg, nowMs) < budgetMs) {
            stateByPackage[pkg]?.let { stateByPackage[pkg] = it.copy(onSurface = false) }
            return BlockingDecision.None
        }

        val previous = stateByPackage[pkg]
        val stillWithinCooldown = previous != null &&
            previous.onSurface &&
            nowMs - previous.lastInterveneMs < cooldownMs
        if (stillWithinCooldown) return BlockingDecision.None

        stateByPackage[pkg] = PackageState(lastInterveneMs = nowMs, onSurface = true)
        return BlockingDecision.Intervene(pkg, result.surface, result.confidence)
    }

    /**
     * Preload per-package usage from session history (per-package totals over the last
     * [DailyUsageMeter.WINDOW_MS]) so a restarted service does not hand out a fresh budget.
     *
     * @param nowMs a monotonic clock reading, the same clock [decide] is called with.
     */
    fun seedUsage(usedByPackage: Map<String, Long>, nowMs: Long) =
        usageMeter.seed(usedByPackage, nowMs)

    /** Forget all per-package memory (e.g. on service teardown). */
    fun reset() {
        stateByPackage.clear()
        usageMeter.reset()
    }

    /**
     * Any watched-package event means every other watched package is no longer in the
     * foreground, so a later return to one of them re-arms the intervention.
     */
    private fun markOtherPackagesLeft(currentPackage: String) {
        val stale = stateByPackage.filter { (pkg, state) -> pkg != currentPackage && state.onSurface }
        for (pkg in stale.keys) {
            stateByPackage[pkg]?.let { stateByPackage[pkg] = it.copy(onSurface = false) }
        }
    }

    private companion object {
        // Confidence floor and cooldown are settings-driven (see [minConfidence] / [cooldownMs]
        // and data.settings.DetectionPolicy); these constants are the shipped defaults.
        // TODO(settings): blockableSurfaces is still fixed - needs a picker UI before it moves.
        val DEFAULT_BLOCKABLE_SURFACES = setOf(Surface.FEED, Surface.SHORT_VIDEO, Surface.EXPLORE)
        const val DEFAULT_MIN_CONFIDENCE = 0.60f
        const val DEFAULT_COOLDOWN_MS = 45_000L
    }
}
