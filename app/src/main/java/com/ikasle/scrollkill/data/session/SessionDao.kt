package com.ikasle.scrollkill.data.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room access for [SessionEntity]. One insert per completed browsing session (never per
 * accessibility event, per CLAUDE.md), plus retention pruning and read-only observers for
 * the future stats UI.
 */
@Dao
interface SessionDao {

    @Insert
    suspend fun insert(entity: SessionEntity): Long

    /** Drop sessions that ended before [cutoffEpochMs]. Returns the row count removed. */
    @Query("DELETE FROM sessions WHERE endedAtEpochMs < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long): Int

    @Query("SELECT * FROM sessions WHERE endedAtEpochMs >= :sinceEpochMs ORDER BY endedAtEpochMs DESC")
    fun observeSince(sinceEpochMs: Long): Flow<List<SessionEntity>>

    @Query(
        """
        SELECT packageName,
               SUM(durationMs) AS totalDurationMs,
               SUM(interventionCount) AS totalInterventions,
               COUNT(*) AS sessionCount
        FROM sessions
        WHERE endedAtEpochMs >= :sinceEpochMs
        GROUP BY packageName
        """,
    )
    fun observePerAppUsageSince(sinceEpochMs: Long): Flow<List<PerAppUsage>>

    /** One-shot counterpart of [observePerAppUsageSince], to seed the daily-usage meter at startup. */
    @Query(
        """
        SELECT packageName,
               SUM(durationMs) AS totalDurationMs,
               SUM(interventionCount) AS totalInterventions,
               COUNT(*) AS sessionCount
        FROM sessions
        WHERE endedAtEpochMs >= :sinceEpochMs
        GROUP BY packageName
        """,
    )
    suspend fun perAppUsageSince(sinceEpochMs: Long): List<PerAppUsage>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int
}
