package com.ikasle.scrollkill.data.session

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The single Room database for the app. Currently only session history; extend
 * [Database.entities] and bump [Database.version] with a real migration when adding more.
 */
@Database(entities = [SessionEntity::class], version = 1, exportSchema = true)
abstract class ScrollKillDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
