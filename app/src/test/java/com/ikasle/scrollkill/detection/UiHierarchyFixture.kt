package com.ikasle.scrollkill.detection

import com.ikasle.scrollkill.service.NodeView
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Parses an `adb shell uiautomator dump` XML into a [NodeView] tree, so a real captured
 * screen hierarchy can be run through the production
 * [com.ikasle.scrollkill.service.SnapshotExtractor] + [ScreenDetector] on the JVM - detector
 * work without a device.
 *
 * uiautomator's `<node>` attributes map straight onto what the accessibility pipeline reads:
 * `resource-id` -> `viewIdResourceName`, `class` -> `className`, `text`, `content-desc`,
 * `package`. Everything else (bounds, clickable, focusable, ...) is ignored.
 *
 * Capture with `scripts/detector-capture/`. Test-only helper; not shipped.
 */
object UiHierarchyFixture {

    /** Parse a uiautomator dump into a single root [NodeView] (see class docs). */
    fun parse(xml: String): NodeView {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Local dev files, but keep the XXE hardening the project asks for.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
            isNamespaceAware = false
        }
        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        document.documentElement.normalize()

        // <hierarchy> holds one <node> per window; the focused app is normally first.
        val windows = childElements(document.documentElement, "node").map(::toNodeView)
        require(windows.isNotEmpty()) { "no <node> elements under the document root" }
        if (windows.size == 1) return windows.single()

        // Multiple windows: wrap them so the extractor still gets one root, keyed on the
        // first window that names a package.
        val pkg = windows.firstNotNullOfOrNull { it.packageName?.toString()?.ifBlank { null } }
        return FixtureNode(pkg, null, null, null, null, windows)
    }

    private fun toNodeView(element: Element): FixtureNode = FixtureNode(
        packageName = element.attrOrNull("package"),
        viewId = element.attrOrNull("resource-id"),
        className = element.attrOrNull("class"),
        text = element.attrOrNull("text"),
        contentDescription = element.attrOrNull("content-desc"),
        children = childElements(element, "node").map(::toNodeView),
    )

    private fun Element.attrOrNull(name: String): String? =
        getAttribute(name).takeIf { it.isNotEmpty() }

    private fun childElements(parent: Element, tag: String): List<Element> {
        val kids = parent.childNodes
        val out = ArrayList<Element>(kids.length)
        for (i in 0 until kids.length) {
            val kid = kids.item(i)
            if (kid.nodeType == Node.ELEMENT_NODE && (kid as Element).tagName == tag) out.add(kid)
        }
        return out
    }

    /** [NodeView] over parsed XML. `recycle()` is a no-op; nothing is pooled here. */
    private class FixtureNode(
        override val packageName: CharSequence?,
        override val viewId: String?,
        override val className: CharSequence?,
        override val text: CharSequence?,
        override val contentDescription: CharSequence?,
        private val children: List<FixtureNode>,
    ) : NodeView {
        override val childCount: Int get() = children.size
        override fun child(index: Int): NodeView? = children.getOrNull(index)
        override fun recycle() = Unit
    }
}
