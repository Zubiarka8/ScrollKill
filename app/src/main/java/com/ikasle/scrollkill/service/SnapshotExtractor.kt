package com.ikasle.scrollkill.service

import android.view.accessibility.AccessibilityNodeInfo
import com.ikasle.scrollkill.detection.ScreenSnapshot

/**
 * Turns a live [AccessibilityNodeInfo] tree into a framework-free [ScreenSnapshot] for the
 * detectors.
 *
 * The traversal is deliberately bounded (CLAUDE.md performance rules): it stops after
 * [maxNodes] nodes or beyond [maxDepth] levels, so a pathological tree can never stall the
 * accessibility callback. Kept in the service layer so the detectors stay framework-free.
 */
class SnapshotExtractor(
    private val maxNodes: Int = MAX_NODES,
    private val maxDepth: Int = MAX_DEPTH,
) {

    /**
     * @param root the active window root; may be null, which yields an empty snapshot.
     *   The caller owns [root] and is responsible for recycling it. Child nodes obtained
     *   during traversal are recycled here.
     */
    fun extract(
        packageName: String,
        root: AccessibilityNodeInfo?,
        fromWindowStateChange: Boolean,
    ): ScreenSnapshot {
        val viewIds = HashSet<String>()
        val classNames = HashSet<String>()
        val texts = ArrayList<String>()
        val contentDescriptions = ArrayList<String>()

        if (root != null) {
            val queue = ArrayDeque<NodeAtDepth>()
            queue.add(NodeAtDepth(root, 0))
            var visited = 0

            while (queue.isNotEmpty() && visited < maxNodes) {
                val (node, depth) = queue.removeFirst()
                visited++

                node.viewIdResourceName?.let { viewIds.add(it) }
                node.className?.let { classNames.add(it.toString()) }
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
                node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                    ?.let { contentDescriptions.add(it) }

                if (depth < maxDepth) {
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i) ?: continue
                        queue.add(NodeAtDepth(child, depth + 1))
                    }
                }

                if (node !== root) node.recycleCompat()
            }
            // Recycle anything queued but never visited (node/depth budget was hit).
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst().node
                if (node !== root) node.recycleCompat()
            }
        }

        return ScreenSnapshot(
            packageName = packageName,
            fromWindowStateChange = fromWindowStateChange,
            viewIds = viewIds,
            classNames = classNames,
            texts = texts,
            contentDescriptions = contentDescriptions,
        )
    }

    private data class NodeAtDepth(val node: AccessibilityNodeInfo, val depth: Int)

    private companion object {
        const val MAX_NODES = 400
        const val MAX_DEPTH = 12

        /** No-op and deprecated from API 33; still frees pooled instances on API 24-32. */
        @Suppress("DEPRECATION")
        fun AccessibilityNodeInfo.recycleCompat() = recycle()
    }
}
