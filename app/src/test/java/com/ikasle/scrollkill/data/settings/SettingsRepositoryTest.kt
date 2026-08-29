package com.ikasle.scrollkill.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newRepo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.create { tmp.root.resolve("$name.preferences_pb") },
    )

    @Test
    fun `interveneEnabled defaults to true when unset`() = runTest {
        assertTrue(newRepo("default").settings.first().interveneEnabled)
    }

    @Test
    fun `setInterveneEnabled is reflected in settings`() = runTest {
        val repo = newRepo("toggle")

        repo.setInterveneEnabled(false)

        assertFalse(repo.settings.first().interveneEnabled)
    }
}
