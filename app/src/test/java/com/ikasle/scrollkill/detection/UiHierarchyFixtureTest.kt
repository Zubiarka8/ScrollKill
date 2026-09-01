package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.detection.DetectionResult.Surface
import com.ikasle.scrollkill.service.SnapshotExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiHierarchyFixtureTest {

    @Test
    fun `parses nested nodes and their attributes into a NodeView tree`() {
        val root = UiHierarchyFixture.parse(
            """
            <hierarchy rotation="0">
              <node class="android.widget.FrameLayout" package="com.example"
                    resource-id="com.example:id/root">
                <node class="android.widget.TextView" text="hi" package="com.example" />
                <node class="android.widget.ImageView" content-desc="pic" package="com.example" />
              </node>
            </hierarchy>
            """.trimIndent(),
        )

        assertEquals("com.example", root.packageName)
        assertEquals("com.example:id/root", root.viewId)
        assertEquals(2, root.childCount)
        assertEquals("hi", root.child(0)?.text)
        assertEquals("pic", root.child(1)?.contentDescription)
    }

    @Test
    fun `merges multiple top-level windows under one root keyed on the first package`() {
        val root = UiHierarchyFixture.parse(
            """
            <hierarchy rotation="0">
              <node class="android.widget.FrameLayout" package="com.zhiliaoapp.musically" />
              <node class="android.widget.FrameLayout" package="com.android.systemui" />
            </hierarchy>
            """.trimIndent(),
        )

        assertEquals("com.zhiliaoapp.musically", root.packageName)
        assertEquals(2, root.childCount)
    }

    @Test
    fun `synthetic TikTok FYP fixture is detected as SHORT_VIDEO through the real pipeline`() {
        val root = UiHierarchyFixture.parse(readFixture("synthetic-tiktok-fyp.xml"))

        // Same extraction + routing the AccessibilityService runs on a live tree.
        val snapshot = SnapshotExtractor().extract("", root, fromWindowStateChange = false)
        val result = ScreenDetector.default().detect(snapshot)

        assertEquals("com.zhiliaoapp.musically", snapshot.packageName)
        assertEquals(Surface.SHORT_VIDEO, result.surface)
        assertTrue("confidence was ${result.confidence}", result.confidence >= 0.60f)
    }

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("detector-fixtures/$name")) {
            "missing test resource detector-fixtures/$name"
        }.bufferedReader().use { it.readText() }
}
