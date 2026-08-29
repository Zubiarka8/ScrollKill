package com.ikasle.scrollkill.data.session

import com.ikasle.scrollkill.session.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The "Repository" stage for session history: takes the transient [Session] records the
 * SessionTracker emits, stamps them with wall-clock time and persists them, prunes old
 * rows, and exposes read-only observers for the stats layer.
 *
 * Framework-free apart from Room; the [SessionDao] is injected so it is unit-testable with
 * an in-memory database. One write per browsing session (CLAUDE.md battery rules), so
 * pruning on each write is cheap.
 *
 * @param clock wall-clock source, injectable for tests.
 * @param retentionMs sessions older than this (by end time) are dropped on the next write.
 *   Mutable: the accessibility service pushes the user's choice in from settings while it
 *   runs (same [Volatile] pattern as the intervention toggle).
 */
class SessionRepository(
    private val dao: SessionDao,
    private val clock: () -> Long = System::currentTimeMillis,
    @field:Volatile var retentionMs: Long = DEFAULT_RETENTION_MS,
) {

    /**
     * Persist one finished session. The tracker works in monotonic uptime, so epoch times
     * are reconstructed from [Session.durationMs] and "now"; this folds in sub-second
     * record lag, which does not matter for daily/weekly aggregates.
     */
    suspend fun record(session: Session) {
        val now = clock()
        dao.insert(
            SessionEntity(
                packageName = session.packageName,
                surface = session.surface.name,
                startedAtEpochMs = now - session.durationMs,
                endedAtEpochMs = now,
                durationMs = session.durationMs,
                detectionCount = session.detectionCount,
                interventionCount = session.interventionCount,
            ),
        )
        dao.deleteOlderThan(now - retentionMs)
    }

    /** Sessions that ended at or after [sinceEpochMs], newest first. */
    fun observeSince(sinceEpochMs: Long): Flow<List<SessionRecord>> =
        dao.observeSince(sinceEpochMs).map { rows -> rows.map { it.toRecord() } }

    /** Per-package totals over sessions that ended at or after [sinceEpochMs]. */
    fun observePerAppUsageSince(sinceEpochMs: Long): Flow<List<PerAppUsage>> =
        dao.observePerAppUsageSince(sinceEpochMs)

    private companion object {
        /** Fallback (90 days) until the service supplies the user's choice from settings. */
        const val DEFAULT_RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    }
}
