package com.ikasle.scrollkill.data.session

import androidx.room.migration.Migration

/**
 * Hand-written Room migrations for [ScrollKillDatabase], oldest first.
 *
 * The schema is still at version 1, so this list is empty and there is nothing to
 * migrate. It exists as the single place the next schema change plugs into: bump
 * [androidx.room.Database.version], let KSP export the new `app/schemas/<n>.json`, add a
 * `MIGRATION_1_2` object here, append it to [ALL_MIGRATIONS], and add one
 * `helper.runMigrationsAndValidate` case to `ScrollKillDatabaseMigrationTest`.
 *
 * Never use `fallbackToDestructiveMigration` on this database: a missing migration must
 * fail loudly, not silently wipe the user's local session history (there is no cloud copy).
 *
 * Template for the next step:
 * ```
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE sessions ADD COLUMN note TEXT NOT NULL DEFAULT ''")
 *     }
 * }
 * ```
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
