package com.ikasle.scrollkill.data.settings

/** How long persisted sessions are kept before [com.ikasle.scrollkill.data.session.SessionRepository] prunes them. */
enum class RetentionWindow(val durationMs: Long, val label: String) {
    DAYS_30(30L * 24 * 60 * 60 * 1000, "30 days"),
    DAYS_90(90L * 24 * 60 * 60 * 1000, "90 days"),
    DAYS_365(365L * 24 * 60 * 60 * 1000, "1 year"),
}
