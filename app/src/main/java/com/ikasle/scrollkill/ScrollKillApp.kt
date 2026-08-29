package com.ikasle.scrollkill

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.ikasle.scrollkill.data.session.ScrollKillDatabase
import com.ikasle.scrollkill.data.session.SessionRepository
import com.ikasle.scrollkill.data.settings.SettingsRepository

/** Single DataStore for the app. Must be one top-level delegate (a second one for the
 * same file name throws at runtime). */
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Composition root. The project has no DI framework, so the [Application] owns the Room
 * database and the repositories as lazy singletons and the AccessibilityService / future
 * ViewModels read them from `context.applicationContext as ScrollKillApp`.
 */
class ScrollKillApp : Application() {

    private val database: ScrollKillDatabase by lazy {
        Room.databaseBuilder(this, ScrollKillDatabase::class.java, "scrollkill.db")
            // TODO(release): replace with real migrations before shipping; this wipes
            // stored stats on any schema change.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val sessionRepository: SessionRepository by lazy { SessionRepository(database.sessionDao()) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }
}
