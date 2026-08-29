package com.ikasle.scrollkill.data.session

/**
 * Aggregated usage for one package over a time window, produced by
 * [SessionDao.observePerAppUsageSince]. Column names must match the query aliases.
 */
data class PerAppUsage(
    val packageName: String,
    val totalDurationMs: Long,
    val totalInterventions: Int,
    val sessionCount: Int,
)
