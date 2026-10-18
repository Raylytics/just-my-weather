package io.raylytics.justmyweather.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.whatsnew.AppVersion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class WhatsNewRepositoryTest {
    private fun store(file: File) = PreferenceDataStoreFactory.createWithPath(produceFile = { file.toOkioPath() })

    @Test
    fun `an update over a settings file the old app wrote shows the notes once`(
        @TempDir dir: File,
    ) = runTest {
        // A real settings file from the shipped app (PreferencesCompatibilityTest).
        val file = File(dir, "settings.preferences_pb")
        file.writeBytes(checkNotNull(javaClass.getResourceAsStream("/prefs/01-customize.preferences_pb")).readBytes())
        val repo = WhatsNewRepository(store(file))
        assertEquals(listOf(AppVersion(0, 4, 1)), repo.pending("0.4.1 (build 12)").map { it.version })
        // Not dismissed yet: a relaunch still shows it.
        assertEquals(1, repo.pending("0.4.1 (build 12)").size)
        repo.markSeen("0.4.1 (build 12)")
        assertTrue(repo.pending("0.4.1 (build 12)").isEmpty())
    }

    @Test
    fun `a fresh install is recorded on its first launch, so it never sees these notes`(
        @TempDir dir: File,
    ) = runTest {
        val dataStore = store(File(dir, "settings.preferences_pb"))
        val repo = WhatsNewRepository(dataStore)
        assertTrue(repo.pending("0.4.1 (build 12)").isEmpty())
        // The first session then writes what any user's would.
        dataStore.edit { it[stringPreferencesKey("view_config")] = "{}" }
        assertTrue(repo.pending("0.4.1 (build 12)").isEmpty())
        assertEquals("0.4.1", dataStore.data.first()[stringPreferencesKey("whats_new_seen")])
    }

    @Test
    fun `a fresh install's own first writes do not count as an earlier app`(
        @TempDir dir: File,
    ) = runTest {
        val dataStore = store(File(dir, "settings.preferences_pb"))
        dataStore.edit { it[stringPreferencesKey("region_clues")] = "{}" }
        assertTrue(WhatsNewRepository(dataStore).pending("0.4.1").isEmpty())
    }
}
