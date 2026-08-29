package com.ikasle.scrollkill.data.settings

/**
 * User preferences, read as an immutable snapshot. A data class so fields can be added
 * without breaking callers; every field must have a default matching "unset".
 */
data class ScrollKillSettings(
    /** Master switch for the BACK-press intervention. */
    val interveneEnabled: Boolean = true,
)
