package io.raylytics.justmyweather.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.alerts.AlertRulesCodec
import io.raylytics.justmyweather.alerts.AlertSettingsCodec
import io.raylytics.justmyweather.data.places.SavedPlacesCodec
import io.raylytics.justmyweather.data.places.SavedPlacesRepository
import io.raylytics.justmyweather.region.RegionCodec
import io.raylytics.justmyweather.view.ThemeConfigCodec
import io.raylytics.justmyweather.view.ViewConfigCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

/**
 * Settings written by the app as it shipped still read, and still write back
 * byte for byte, now that the stores and codecs live in the shared module.
 *
 * The fixtures under resources/prefs are real `settings.preferences_pb` files
 * pulled from an emulator running the pre-port build (main at 0.3.1), each
 * after one Maestro flow that leaves its kind of state behind: a shown field
 * and a theme (01), an alert rule (02), quiet hours (04), the safety opt-in
 * (05), a saved place (08) — plus the caches every launch writes.
 *
 * If this fails, an upgrade would lose or rewrite someone's settings. Don't
 * re-capture the fixtures to make it pass: they are the old app's output, and
 * the point is that the new code agrees with it.
 */
class PreferencesCompatibilityTest {
    private fun bytes(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/prefs/$name.preferences_pb")) { "missing fixture $name" }
            .use { it.readBytes() }

    private suspend fun read(raw: ByteArray): Preferences = PreferencesSerializer.readFrom(Buffer().write(raw))

    @ParameterizedTest
    @ValueSource(strings = ["01-customize", "02-alerts", "04-quiet-hours", "05-safety-alerts", "08-places"])
    fun `the file re-serialises to the same bytes`(name: String) =
        runTest {
            val raw = bytes(name)
            val out = Buffer()
            PreferencesSerializer.writeTo(read(raw), out)
            assertArrayEquals(raw, out.readByteArray())
        }

    @ParameterizedTest
    @ValueSource(strings = ["01-customize", "02-alerts", "04-quiet-hours", "05-safety-alerts", "08-places"])
    fun `every stored value decodes and re-encodes unchanged`(name: String) =
        runTest {
            val prefs = read(bytes(name))
            val codecs: Map<String, (String) -> String> =
                mapOf(
                    "view_config" to { ViewConfigCodec.encode(ViewConfigCodec.decode(it)) },
                    "theme_config" to { ThemeConfigCodec.encode(ThemeConfigCodec.decode(it)) },
                    "alert_rules" to { AlertRulesCodec.encode(AlertRulesCodec.decode(it)) },
                    "alert_settings" to { AlertSettingsCodec.encode(AlertSettingsCodec.decode(it)) },
                    "saved_places" to { SavedPlacesCodec.encode(SavedPlacesCodec.decode(it)) },
                    "point_cache" to { PointCacheCodec.encode(PointCacheCodec.decode(it)) },
                    "last_snapshot" to { SnapshotCodec.encode(checkNotNull(SnapshotCodec.decode(it))) },
                    "region_settings" to { RegionCodec.encodeSettings(RegionCodec.decodeSettings(it)) },
                    "region_clues" to { RegionCodec.encodeClues(checkNotNull(RegionCodec.decodeClues(it))) },
                )
            var checked = 0
            for ((key, roundTrip) in codecs) {
                val stored = prefs[stringPreferencesKey(key)] ?: continue
                assertEquals(stored, roundTrip(stored), "$key in $name")
                checked++
            }
            assertTrue(checked >= 3, "only $checked coded values found in $name — wrong fixture?")
        }

    @org.junit.jupiter.api.Test
    fun `a shipped saved place opens through the shared repository`(
        @TempDir dir: File,
    ) = runTest {
        val file = File(dir, "settings.preferences_pb").apply { writeBytes(bytes("08-places")) }
        val store = PreferenceDataStoreFactory.createWithPath(produceFile = { file.toOkioPath() })
        val saved = SavedPlacesRepository(store).saved.first()
        assertEquals(listOf("Louisville, KY"), saved.places.map { it.label })
    }
}
