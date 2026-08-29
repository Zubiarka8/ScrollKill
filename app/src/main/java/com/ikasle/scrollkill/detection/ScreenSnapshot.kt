package com.ikasle.scrollkill.detection

/**
 * Framework-free view of one screen state, consumed by [AppDetector]s.
 *
 * A flat bag of already-extracted node attributes, not an [android.view.accessibility.AccessibilityNodeInfo]
 * and not a node tree: keeping it flat lets detectors be plain unit tests and keeps
 * the future extraction step from having to walk the whole accessibility tree.
 *
 * Covers the PACKAGE_NAME, WINDOW_STATE, VIEW_ID, CLASS_NAME, TEXT and
 * CONTENT_DESCRIPTION signals in [DetectionResult.Signal]. NODE_HIERARCHY and ACTIONS
 * are not modelled yet; add fields when a detector actually needs them.
 */
data class ScreenSnapshot(
    /** Foreground package the snapshot was taken from. */
    val packageName: String,
    /** True when sourced from TYPE_WINDOW_STATE_CHANGED rather than a content change. */
    val fromWindowStateChange: Boolean = false,
    /** viewIdResourceName values seen in the (bounded) traversal. */
    val viewIds: Set<String> = emptySet(),
    /** className values seen. */
    val classNames: Set<String> = emptySet(),
    /** text values seen. */
    val texts: List<String> = emptyList(),
    /** contentDescription values seen. */
    val contentDescriptions: List<String> = emptyList(),
)
