package com.ikasle.scrollkill.blocking

import com.ikasle.scrollkill.detection.DetectionResult
import com.ikasle.scrollkill.detection.DetectionResult.Surface

/**
 * The "BlockingEngine" stage of the CLAUDE.md pipeline: turns a [DetectionResult] into a
 * [BlockingDecision].
 *
 * Pure and framework-free (unit-testable). It never performs the action itself; the
 * AccessibilityService does. Its only state is a small per-package memory used to avoid
 * nagging: after a [BlockingDecision.Intervene] for a package it stays quiet until either
 * [cooldownMs] elapses or that package's watched surface goes away and later returns.
 *
 * Not thread-safe: [decide] is meant to be called from the single accessibility callback
 * thread.
 */
class BlockingEngine(
    private val blockableSurfaces: Set<Surface> = DEFAULT_BLOCKABLE_SURFACES,
    private val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {

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
     * @param nowMs a monotonic clock reading (e.g. SystemClock.uptimeMillis()).
     */
    fun decide(result: DetectionResult, nowMs: Long): BlockingDecision {
        markOtherPackagesLeft(result.packageName)

        val pkg = result.packageName
        val blockable = result.isMatch &&
            result.surface in blockableSurfaces &&
            result.confidence >= minConfidence &&
            pkg !in blockingDisabledPackages

        if (!blockable) {
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

    /** Forget all per-package memory (e.g. on service teardown). */
    fun reset() = stateByPackage.clear()

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
        val DEFAULT_BLOCKABLE_SURFACES = setOf(Surface.FEED, Surface.SHORT_VIDEO, Surface.EXPLORE)
        const val DEFAULT_MIN_CONFIDENCE = 0.60f

        // TODO(settings): move policy (cooldown, confidence, blockable surfaces) to the
        // settings repository once it exists.
        const val DEFAULT_COOLDOWN_MS = 45_000L
    }
}
