package com.ikasle.scrollkill.data.session

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented migration tests for [ScrollKillDatabase]. Needs a device or emulator
 * (`connectedDebugAndroidTest`); not part of the JVM unit-test suite.
 *
 * The schema is at version 1, so there is no migration to run yet. These tests lock in the
 * two invariants that must already hold before the first real migration is written:
 *  - the exported `app/schemas/1.json` is bundled in the test APK and matches the compiled
 *    `@Database` (so a forgotten re-export is caught here, not in production);
 *  - the production builder path (`addMigrations(*ALL_MIGRATIONS)`, no destructive
 *    fallback) opens a v1 database and keeps its rows.
 *
 * When [androidx.room.Database.version] is bumped, add a `migrate1To2` test that calls
 * `helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)` and asserts the old
 * rows survived.
 */
@RunWith(AndroidJUnit4::class)
class ScrollKillDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ScrollKillDatabase::class.java,
    )

    @Test
    fun version1_liveSchemaMatchesExportedJson() {
        helper.createDatabase(TEST_DB, 1).close()
        // No migrations to apply; this re-validates the on-disk schema against 1.json.
        helper.runMigrationsAndValidate(TEST_DB, 1, true).close()
    }

    @Test
    fun version1_opensThroughProductionBuilderWithDataIntact() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO sessions " +
                    "(packageName, surface, startedAtEpochMs, endedAtEpochMs, durationMs, " +
                    "detectionCount, interventionCount) " +
                    "VALUES ('com.instagram.android', 'REELS', 1000, 4000, 3000, 5, 1)",
            )
        }

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ScrollKillDatabase::class.java,
            TEST_DB,
        ).addMigrations(*ALL_MIGRATIONS).build()

        try {
            db.query("SELECT packageName, durationMs FROM sessions", null).use { rows ->
                assertTrue(rows.moveToFirst())
                assertEquals("com.instagram.android", rows.getString(0))
                assertEquals(3000L, rows.getLong(1))
                assertEquals(1, rows.count)
            }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
