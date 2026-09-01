package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.service.NodeView
import com.ikasle.scrollkill.service.SnapshotExtractor
import java.io.File
import java.util.Locale
import org.junit.Test

/**
 * Runs every XML capture under `app/src/test/resources/detector-fixtures/` through the
 * production [SnapshotExtractor] + [ScreenDetector] and writes a human-readable report to
 * `app/build/reports/detector-fixtures/report.txt` (also echoed to stdout).
 *
 * This is a diagnostic, not a gate: it always passes. Use it to fix detector token lists
 * against real captured hierarchies without a device - see `scripts/detector-capture/` for
 * how to record a fixture. Regression assertions on a specific fixture belong in their own
 * test (see [UiHierarchyFixtureTest]).
 */
class DetectorFixtureReportTest {

    @Test
    fun `report every detector fixture`() {
        val dirUrl = javaClass.classLoader?.getResource("detector-fixtures")
        val dir = dirUrl?.let { File(it.toURI()) }
        val fixtures = dir?.listFiles { f -> f.isFile && f.extension == "xml" }
            ?.sortedBy { it.name }
            .orEmpty()

        val report = buildString {
            appendLine("Detector fixture report - ${fixtures.size} capture(s)")
            appendLine("=".repeat(72))
            if (fixtures.isEmpty()) {
                appendLine()
                appendLine("No fixtures. Record one with scripts/detector-capture/capture.sh")
                appendLine("(e.g. `capture.sh tiktok-fyp`) then copy the XML into")
                appendLine("app/src/test/resources/detector-fixtures/ and re-run this test.")
            }
            for (fixture in fixtures) {
                appendLine()
                appendReport(fixture)
            }
        }

        val outFile = File("build/reports/detector-fixtures/report.txt").apply {
            parentFile?.mkdirs()
            writeText(report)
        }
        println(report)
        println("written to ${outFile.absolutePath}")
    }

    private fun StringBuilder.appendReport(fixture: File) {
        val synthetic = fixture.name.startsWith("synthetic-")
        appendLine("--- ${fixture.name}${if (synthetic) "  (SYNTHETIC)" else ""} ---")

        val root = UiHierarchyFixture.parse(fixture.readText())
        val tree = measureTree(root)
        val snapshot = SnapshotExtractor().extract("", root, fromWindowStateChange = false)
        val result = ScreenDetector.default().detect(snapshot)
        val hasDetector = snapshot.packageName in ScreenDetector.default().watchedPackages
        val truncated = tree.depth > EXTRACTOR_MAX_DEPTH || tree.nodes > EXTRACTOR_MAX_NODES

        appendLine("package : ${snapshot.packageName}")
        appendLine(
            "result  : surface=${result.surface} " +
                "confidence=${"%.2f".format(Locale.US, result.confidence)} " +
                "signals=${result.matchedSignals.map { it.name }}",
        )
        appendLine(
            "tree    : ${tree.nodes} nodes, ${tree.depth} deep; extractor caps at " +
                "${EXTRACTOR_MAX_NODES} nodes / depth ${EXTRACTOR_MAX_DEPTH}" +
                if (truncated) " -> ${tree.idsBelowCap} id-bearing nodes past the cap are never scored" else "",
        )
        appendLine(
            "verdict : " + when {
                !hasDetector -> "no detector for this package (launcher/other app, or not watched)"
                result.confidence >= MATCH_THRESHOLD -> "OK - crosses the $MATCH_THRESHOLD threshold"
                truncated -> "BELOW $MATCH_THRESHOLD - tree exceeds the extractor's depth/node cap; " +
                    "the distinctive container ids sit below it. Raise SnapshotExtractor caps or " +
                    "move signals onto shallower nodes before touching token lists."
                else -> "BELOW $MATCH_THRESHOLD - token drift on this surface; refresh the token lists"
            },
        )
        appendLine()
        appendTokens("viewIds", snapshot.viewIds.sorted())
        appendTokens("classNames", snapshot.classNames.sorted())
        appendTokens("texts", snapshot.texts.distinct())
        appendTokens("contentDescriptions", snapshot.contentDescriptions.distinct())
    }

    private fun StringBuilder.appendTokens(label: String, values: List<String>) {
        appendLine("$label (${values.size}):")
        values.take(MAX_TOKEN_LINES).forEach { appendLine("  $it") }
        if (values.size > MAX_TOKEN_LINES) appendLine("  ... ${values.size - MAX_TOKEN_LINES} more")
    }

    /** Raw shape of the captured hierarchy, before [SnapshotExtractor] applies its caps. */
    private data class TreeStats(val nodes: Int, val depth: Int, val idsBelowCap: Int)

    private fun measureTree(root: NodeView): TreeStats {
        var nodes = 0
        var maxDepth = 0
        var idsBelowCap = 0
        fun walk(node: NodeView, depth: Int) {
            nodes++
            if (depth > maxDepth) maxDepth = depth
            if (depth > EXTRACTOR_MAX_DEPTH && !node.viewId.isNullOrBlank()) idsBelowCap++
            for (i in 0 until node.childCount) node.child(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return TreeStats(nodes, maxDepth, idsBelowCap)
    }

    private companion object {
        const val MATCH_THRESHOLD = 0.60f
        const val MAX_TOKEN_LINES = 120

        // Mirror SnapshotExtractor.MAX_NODES / MAX_DEPTH (its companion is private).
        const val EXTRACTOR_MAX_NODES = 400
        const val EXTRACTOR_MAX_DEPTH = 12
    }
}
