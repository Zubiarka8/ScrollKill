package com.ikasle.scrollkill.blocking

/**
 * Rolling per-package "time on a watched surface" accumulator for [BlockingEngine]'s daily
 * budget gate.
 *
 * Pure and framework-free (unit-testable). Time is whatever monotonic clock the caller passes
 * ([android.os.SystemClock.uptimeMillis]); the meter never reads a clock itself. Usage is kept
 * in coarse fixed-width buckets so the memory stays tiny (a 24h window at 10-minute buckets is
 * 144 longs per package) and expiry is a cheap key comparison.
 *
 * [record] is called from the single accessibility callback thread; [seed] runs once from an
 * IO coroutine when the service connects, so the mutating methods are [Synchronized]. Lock
 * contention is negligible: callback events are debounced to a few per second.
 */
class DailyUsageMeter(
    private val windowMs: Long = WINDOW_MS,
    private val maxGapMs: Long = DEFAULT_MAX_GAP_MS,
    private val bucketMs: Long = DEFAULT_BUCKET_MS,
) {

    private class PackageUsage {
        /** bucket index -> milliseconds credited to that bucket */
        val buckets = HashMap<Long, Long>()
        var lastEventMs: Long? = null
    }

    private val usageByPackage = HashMap<String, PackageUsage>()

    /**
     * Credit the gap since this package's previous on-surface event to the current bucket. The
     * gap is capped at [maxGapMs] so time spent with the app backgrounded (or the phone off)
     * does not count. The first event for a package credits nothing.
     */
    @Synchronized
    fun record(packageName: String, nowMs: Long) {
        val usage = usageByPackage.getOrPut(packageName) { PackageUsage() }
        val last = usage.lastEventMs
        if (last != null) {
            val delta = (nowMs - last).coerceIn(0L, maxGapMs)
            if (delta > 0L) {
                val key = nowMs / bucketMs
                usage.buckets[key] = (usage.buckets[key] ?: 0L) + delta
            }
        }
        usage.lastEventMs = nowMs
        prune(usage, nowMs)
    }

    /** Milliseconds credited to [packageName] within the last [windowMs]. 0 if never seen. */
    @Synchronized
    fun usedMs(packageName: String, nowMs: Long): Long {
        val usage = usageByPackage[packageName] ?: return 0L
        prune(usage, nowMs)
        return usage.buckets.values.sum()
    }

    /**
     * Preload historical usage (per-package totals over the last [windowMs], read from session
     * history) so a restarted service does not hand out a fresh budget. The whole seeded total
     * lands in the current bucket and ages out [windowMs] later; live buckets take over well
     * before then on a continuously running service.
     */
    @Synchronized
    fun seed(usedByPackage: Map<String, Long>, nowMs: Long) {
        val key = nowMs / bucketMs
        for ((pkg, seededMs) in usedByPackage) {
            if (seededMs <= 0L) continue
            val usage = usageByPackage.getOrPut(pkg) { PackageUsage() }
            usage.buckets[key] = (usage.buckets[key] ?: 0L) + seededMs
        }
    }

    /** Forget all usage (e.g. on service teardown). */
    @Synchronized
    fun reset() = usageByPackage.clear()

    private fun prune(usage: PackageUsage, nowMs: Long) {
        val oldestKept = (nowMs - windowMs) / bucketMs
        usage.buckets.keys.removeAll { it < oldestKept }
    }

    companion object {
        /** The daily-limit period. Also the history look-back the service seeds from. */
        const val WINDOW_MS = 24L * 60 * 60 * 1000

        private const val DEFAULT_MAX_GAP_MS = 30_000L
        private const val DEFAULT_BUCKET_MS = 10L * 60 * 1000
    }
}
