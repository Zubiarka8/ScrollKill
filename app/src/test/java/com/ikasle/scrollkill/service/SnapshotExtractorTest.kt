package com.ikasle.scrollkill.service

import com.ikasle.scrollkill.detection.ScreenSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotExtractorTest {

    @Test
    fun `null root yields an empty snapshot with the passthrough fields`() {
        val snapshot = SnapshotExtractor()
            .extract("com.instagram.android", null, fromWindowStateChange = true)

        assertEquals(
            ScreenSnapshot("com.instagram.android", fromWindowStateChange = true),
            snapshot,
        )
    }

    @Test
    fun `snapshot is keyed on the root package, not the triggering event package`() {
        // The event fired from the app that is leaving; getRootInActiveWindow already points
        // at whatever replaced it. Detection must run against what is really on screen.
        val root = node(viewId = "list", packageName = "com.android.launcher")

        val snapshot = SnapshotExtractor()
            .extract("com.zhiliaoapp.musically", root, fromWindowStateChange = false)

        assertEquals("com.android.launcher", snapshot.packageName)
    }

    @Test
    fun `falls back to the event package when the root has no package`() {
        val root = node(viewId = "list", packageName = "  ")

        val snapshot = SnapshotExtractor().extract("com.instagram.android", root, false)

        assertEquals("com.instagram.android", snapshot.packageName)
    }

    @Test
    fun `collects ids, class names, text and content descriptions across the tree`() {
        val root = node(
            viewId = "root",
            className = "android.widget.FrameLayout",
            children = listOf(
                node(
                    viewId = "list",
                    className = "androidx.recyclerview.widget.RecyclerView",
                    text = "Reels",
                ),
                node(className = "android.widget.ImageView", contentDescription = "Reel by someone"),
            ),
        )

        val snapshot = SnapshotExtractor().extract("com.instagram.android", root, false)

        assertEquals(setOf("root", "list"), snapshot.viewIds)
        assertEquals(
            setOf(
                "android.widget.FrameLayout",
                "androidx.recyclerview.widget.RecyclerView",
                "android.widget.ImageView",
            ),
            snapshot.classNames,
        )
        assertEquals(listOf("Reels"), snapshot.texts)
        assertEquals(listOf("Reel by someone"), snapshot.contentDescriptions)
    }

    @Test
    fun `blank and null text or content description is skipped`() {
        val root = node(
            text = "   ",
            children = listOf(node(text = "", contentDescription = "  "), node(text = "kept")),
        )

        val snapshot = SnapshotExtractor().extract("p", root, false)

        assertEquals(listOf("kept"), snapshot.texts)
        assertTrue(snapshot.contentDescriptions.isEmpty())
    }

    @Test
    fun `text is collected in breadth-first order`() {
        val root = node(
            text = "a",
            children = listOf(
                node(text = "b", children = listOf(node(text = "d"))),
                node(text = "c"),
            ),
        )

        val snapshot = SnapshotExtractor().extract("p", root, false)

        assertEquals(listOf("a", "b", "c", "d"), snapshot.texts)
    }

    @Test
    fun `traversal stops after maxNodes`() {
        val leaves = (0 until 10).map { node(text = "t$it") }
        val root = node(text = "root", children = leaves)

        // Budget of 3: root + the first two children.
        val snapshot = SnapshotExtractor(maxNodes = 3).extract("p", root, false)

        assertEquals(listOf("root", "t0", "t1"), snapshot.texts)
    }

    @Test
    fun `the default depth cap reaches feed containers that sit below level 12`() {
        // Real social-app feed containers / localized labels sit at depth 15-28
        // (docs/maintenance/detector-token-recheck.md bug B-4). A chain 20 deep with the
        // signal on the leaf must still be collected by the default extractor.
        var leaf: FakeNode = node(viewId = "clips_viewer_view_pager")
        repeat(20) { leaf = node(children = listOf(leaf)) }

        val snapshot = SnapshotExtractor().extract("com.instagram.android", leaf, false)

        assertTrue("clips_viewer_view_pager" in snapshot.viewIds)
    }

    @Test
    fun `nodes deeper than maxDepth are not visited`() {
        val root = node(
            text = "d0",
            children = listOf(node(text = "d1", children = listOf(node(text = "d2")))),
        )

        // maxDepth 1: visit depth 0 and 1, never enqueue depth 2.
        val snapshot = SnapshotExtractor(maxDepth = 1).extract("p", root, false)

        assertEquals(listOf("d0", "d1"), snapshot.texts)
    }

    @Test
    fun `every non-root node is recycled and the root is not`() {
        val a = node(text = "a")
        val b = node(text = "b")
        val root = node(text = "root", children = listOf(a, b))

        SnapshotExtractor().extract("p", root, false)

        assertFalse(root.recycled)
        assertTrue(a.recycled)
        assertTrue(b.recycled)
    }

    @Test
    fun `nodes queued but never visited are still recycled`() {
        val a = node(text = "a")
        val b = node(text = "b")
        val c = node(text = "c")
        val root = node(text = "root", children = listOf(a, b, c))

        // Budget 2: root + a are visited; b and c are enqueued then drained unvisited.
        SnapshotExtractor(maxNodes = 2).extract("p", root, false)

        assertFalse(root.recycled)
        assertTrue(a.recycled)
        assertTrue(b.recycled)
        assertTrue(c.recycled)
    }

    private fun node(
        viewId: String? = null,
        className: CharSequence? = null,
        text: CharSequence? = null,
        contentDescription: CharSequence? = null,
        packageName: CharSequence? = null,
        children: List<FakeNode> = emptyList(),
    ) = FakeNode(viewId, className, text, contentDescription, packageName, children)

    private class FakeNode(
        override val viewId: String?,
        override val className: CharSequence?,
        override val text: CharSequence?,
        override val contentDescription: CharSequence?,
        override val packageName: CharSequence?,
        private val children: List<FakeNode>,
    ) : NodeView {

        var recycled = false
            private set

        override val childCount get() = children.size

        override fun child(index: Int): NodeView? = children.getOrNull(index)

        override fun recycle() {
            recycled = true
        }
    }
}
