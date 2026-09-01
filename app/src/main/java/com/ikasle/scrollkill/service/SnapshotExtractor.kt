package com.ikasle.scrollkill.service

import com.ikasle.scrollkill.detection.ScreenSnapshot

/**
 * Turns an accessibility node tree into a framework-free [ScreenSnapshot] for the detectors.
 *
 * The traversal is deliberately bounded (CLAUDE.md performance rules): it stops after
 * [maxNodes] nodes or beyond [maxDepth] levels, so a pathological tree can never stall the
 * accessibility callback. Kept in the service layer so the detectors stay framework-free.
 *
 * It walks [NodeView], not [android.view.accessibility.AccessibilityNodeInfo] directly, so
 * the walk can be unit-tested with a fake tree; the service passes an [AccessibilityNodeView].
 */
class SnapshotExtractor(
    private val maxNodes: Int = MAX_NODES,
    private val maxDepth: Int = MAX_DEPTH,
) {

    /**
     * @param eventPackageName the package the triggering accessibility event named. Used only
     *   as a fallback: the snapshot's package is taken from [root] when it is available, since
     *   an event can arrive from an app that is already leaving the foreground while
     *   [android.accessibilityservice.AccessibilityService.getRootInActiveWindow] already
     *   points at whatever replaced it (the launcher, another app). Keying detection off the
     *   node tree that was actually walked keeps the detector from scoring one app's tokens
     *   against another's UI.
     * @param root the active window root; may be null, which yields an empty snapshot keyed on
     *   [eventPackageName]. The caller owns [root] and is responsible for recycling it. Child
     *   nodes obtained during traversal are recycled here.
     */
    fun extract(
        eventPackageName: String,
        root: NodeView?,
        fromWindowStateChange: Boolean,
    ): ScreenSnapshot {
        val onScreenPackage = root?.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: eventPackageName

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

                node.viewId?.let { viewIds.add(it) }
                node.className?.let { classNames.add(it.toString()) }
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
                node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                    ?.let { contentDescriptions.add(it) }

                if (depth < maxDepth) {
                    for (i in 0 until node.childCount) {
                        val child = node.child(i) ?: continue
                        queue.add(NodeAtDepth(child, depth + 1))
                    }
                }

                if (node !== root) node.recycle()
            }
            // Recycle anything queued but never visited (node/depth budget was hit).
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst().node
                if (node !== root) node.recycle()
            }
        }

        return ScreenSnapshot(
            packageName = onScreenPackage,
            fromWindowStateChange = fromWindowStateChange,
            viewIds = viewIds,
            classNames = classNames,
            texts = texts,
            contentDescriptions = contentDescriptions,
        )
    }

    private data class NodeAtDepth(val node: NodeView, val depth: Int)

    private companion object {
        const val MAX_NODES = 400
        const val MAX_DEPTH = 12
    }
}
