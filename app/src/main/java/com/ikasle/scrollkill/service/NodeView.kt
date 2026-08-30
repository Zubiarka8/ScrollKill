package com.ikasle.scrollkill.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Minimal read-only view of one accessibility node, so [SnapshotExtractor]'s tree walk can
 * be exercised in plain unit tests instead of against a live [AccessibilityNodeInfo].
 *
 * Mirrors only the attributes the extractor reads. One wrapper is allocated per visited
 * node during traversal; the walk is already bounded (see [SnapshotExtractor]), so the cost
 * is capped and short-lived.
 */
interface NodeView {
    val viewId: String?
    val className: CharSequence?
    val text: CharSequence?
    val contentDescription: CharSequence?
    val childCount: Int

    /** The i-th child, or null if unavailable. The caller owns the result and recycles it. */
    fun child(index: Int): NodeView?

    /** Frees the underlying pooled instance where that still applies (see the adapter). */
    fun recycle()
}

/** [NodeView] backed by a real [AccessibilityNodeInfo]. */
class AccessibilityNodeView(private val node: AccessibilityNodeInfo) : NodeView {

    override val viewId: String? get() = node.viewIdResourceName
    override val className: CharSequence? get() = node.className
    override val text: CharSequence? get() = node.text
    override val contentDescription: CharSequence? get() = node.contentDescription
    override val childCount: Int get() = node.childCount

    override fun child(index: Int): NodeView? =
        node.getChild(index)?.let(::AccessibilityNodeView)

    /** No-op and deprecated from API 33; still frees pooled instances on API 24-32. */
    @Suppress("DEPRECATION")
    override fun recycle() = node.recycle()
}
