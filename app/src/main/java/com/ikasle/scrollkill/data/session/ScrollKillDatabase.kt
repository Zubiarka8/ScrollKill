package com.ikasle.scrollkill.data.session

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The single Room database for the app. Currently only session history. To change the
 * schema: extend [Database.entities], bump [Database.version], and add the matching
 * migration to [ALL_MIGRATIONS] (see `Migrations.kt`) plus a case in
 * `ScrollKillDatabaseMigrationTest`. There is no destructive fallback.
 */
@Database(entities = [SessionEntity::class], version = 1, exportSchema = true)
abstract class ScrollKillDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
