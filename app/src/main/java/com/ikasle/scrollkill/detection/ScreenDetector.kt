package com.ikasle.scrollkill.detection

/**
 * The "ScreenDetector" stage of the CLAUDE.md pipeline: given a [ScreenSnapshot] already
 * extracted from the current screen, run the [AppDetector] that owns the foreground
 * package and return its [DetectionResult].
 *
 * Pure and framework-free so it stays unit-testable. Snapshot extraction from the live
 * accessibility tree happens upstream (SnapshotExtractor); blocking happens downstream
 * (future BlockingEngine). This class does neither.
 */
class ScreenDetector(detectors: List<AppDetector>) {

    /** Every watched package mapped to the single detector responsible for it. */
    private val detectorByPackage: Map<String, AppDetector> = buildMap {
        for (detector in detectors) {
            for (pkg in detector.targetPackages) {
                val previous = put(pkg, detector)
                require(previous == null) {
                    "package $pkg is claimed by more than one detector"
                }
            }
        }
    }

    /** Packages any detector handles. The service filters events against this set. */
    val watchedPackages: Set<String> get() = detectorByPackage.keys

    /**
     * Route [snapshot] to its detector. Returns [DetectionResult.none] when no detector
     * handles [ScreenSnapshot.packageName].
     */
    fun detect(snapshot: ScreenSnapshot): DetectionResult =
        detectorByPackage[snapshot.packageName]?.detect(snapshot)
            ?: DetectionResult.none(snapshot.packageName)

    companion object {
        /** The production detector set. Packages must not overlap between detectors. */
        fun default(): ScreenDetector = ScreenDetector(
            listOf(
                InstagramDetector(),
                YouTubeShortsDetector(),
                TikTokDetector(),
                FacebookDetector(),
            ),
        )
    }
}
