package com.ikasle.scrollkill.data.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ikasle.scrollkill.detection.DetectionResult.Surface
import com.ikasle.scrollkill.session.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionRepositoryTest {

    private lateinit var db: ScrollKillDatabase
    private lateinit var dao: SessionDao

    /** Fixed wall clock; mutate between records to control ordering. */
    private var now = 1_700_000_000_000L

    private val ninetyDaysMs = 90L * 24 * 60 * 60 * 1000

    private fun repo(retentionMs: Long = ninetyDaysMs) =
        SessionRepository(dao, clock = { now }, retentionMs = retentionMs)

    private fun session(
        pkg: String = "com.instagram.android",
        surface: Surface = Surface.FEED,
        durationMs: Long = 5_000L,
        detectionCount: Int = 3,
        interventionCount: Int = 1,
    ) = Session(
        packageName = pkg,
        surface = surface,
        startedAtMs = 0L,
        endedAtMs = durationMs,
        detectionCount = detectionCount,
        interventionCount = interventionCount,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScrollKillDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.sessionDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `record stamps epoch from the clock and the session duration`() = runTest {
        repo().record(session(durationMs = 5_000, detectionCount = 4, interventionCount = 2))

        val row = dao.observeSince(Long.MIN_VALUE).first().single()
        assertEquals("com.instagram.android", row.packageName)
        assertEquals("FEED", row.surface)
        assertEquals(now, row.endedAtEpochMs)
        assertEquals(now - 5_000, row.startedAtEpochMs)
        assertEquals(5_000, row.durationMs)
        assertEquals(4, row.detectionCount)
        assertEquals(2, row.interventionCount)
    }

    @Test
    fun `record prunes sessions older than the retention window`() = runTest {
        val oldEnd = now - (100L * 24 * 60 * 60 * 1000)
        dao.insert(
            SessionEntity(
                packageName = "stale",
                surface = "FEED",
                startedAtEpochMs = oldEnd - 1_000,
                endedAtEpochMs = oldEnd,
                durationMs = 1_000,
                detectionCount = 1,
                interventionCount = 0,
            ),
        )

        repo().record(session())

        val remaining = dao.observeSince(Long.MIN_VALUE).first()
        assertEquals(1, remaining.size)
        assertNotEquals("stale", remaining.single().packageName)
    }

    @Test
    fun `observeSince returns newest first and respects the cutoff`() = runTest {
        val r = repo()
        now = 10_000; r.record(session(durationMs = 1_000))
        now = 20_000; r.record(session(pkg = "com.zhiliaoapp.musically", surface = Surface.SHORT_VIDEO, durationMs = 1_000))
        now = 30_000; r.record(session(pkg = "com.google.android.youtube", durationMs = 1_000))

        val since = r.observeSince(15_000).first()

        assertEquals(
            listOf("com.google.android.youtube", "com.zhiliaoapp.musically"),
            since.map { it.packageName },
        )
    }

    @Test
    fun `observePerAppUsageSince sums duration, interventions and count per package`() = runTest {
        val r = repo()
        now = 100_000; r.record(session(pkg = "A", durationMs = 2_000, interventionCount = 1))
        now = 101_000; r.record(session(pkg = "A", durationMs = 3_000, interventionCount = 2))
        now = 102_000; r.record(session(pkg = "B", durationMs = 1_000, interventionCount = 0))

        val usage = r.observePerAppUsageSince(0).first().associateBy { it.packageName }

        assertEquals(5_000, usage.getValue("A").totalDurationMs)
        assertEquals(3, usage.getValue("A").totalInterventions)
        assertEquals(2, usage.getValue("A").sessionCount)
        assertEquals(1_000, usage.getValue("B").totalDurationMs)
        assertEquals(1, usage.getValue("B").sessionCount)
    }

    @Test
    fun `surface round-trips and an unknown stored value maps to UNKNOWN`() = runTest {
        val r = repo()
        r.record(session(surface = Surface.EXPLORE))
        dao.insert(
            SessionEntity(
                packageName = "weird",
                surface = "NOT_A_SURFACE",
                startedAtEpochMs = now - 1,
                endedAtEpochMs = now,
                durationMs = 1,
                detectionCount = 1,
                interventionCount = 0,
            ),
        )

        val byPkg = r.observeSince(Long.MIN_VALUE).first().associateBy { it.packageName }

        assertEquals(Surface.EXPLORE, byPkg.getValue("com.instagram.android").surface)
        assertEquals(Surface.UNKNOWN, byPkg.getValue("weird").surface)
    }
}
