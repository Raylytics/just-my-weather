package io.raylytics.justmyweather.alerts

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.data.AlertSettingsRepository
import io.raylytics.justmyweather.data.DataStoreLastLocationStore
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.nws.ActiveAlert
import io.raylytics.justmyweather.data.nws.HttpResult
import io.raylytics.justmyweather.data.nws.HttpTransport
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.location.LocationResolver
import io.raylytics.justmyweather.location.LocationSource
import io.raylytics.justmyweather.region.RegionRepository
import io.raylytics.justmyweather.region.RegionSignals
import io.raylytics.justmyweather.view.WeatherField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import okio.IOException
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Instant

/**
 * The background alert check, which both apps run on their own schedule
 * (WorkManager / BGAppRefreshTask). Real stores on a temp file; NWS faked at
 * the transport, so the whole path from rule to notification is exercised.
 */
class AlertCheckTest {
    @TempDir
    lateinit var dir: File

    private class Posted : AlertNotifications {
        val rules = mutableListOf<Pair<String, Boolean>>()
        val safety = mutableListOf<String>()

        override fun notify(rule: AlertRule, decision: FireDecision, silent: Boolean) {
            rules += rule.id to silent
        }

        override fun notifySafety(alert: ActiveAlert) {
            safety += alert.id
        }
    }

    /** NWS for one Brooklyn point: 20 °C (68 °F) observed, one tornado warning. */
    private class Nws(private val failing: Boolean = false) : HttpTransport {
        var calls = 0

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            calls++
            if (failing) throw IOException("offline")
            val body =
                when {
                    "/observations/latest" in url -> OBSERVATION
                    "/alerts/active" in url -> ALERTS
                    url.endsWith("/stations") -> STATIONS
                    "/points/" in url -> POINTS
                    else -> error("unexpected url $url")
                }
            return HttpResult(200, body, null)
        }
    }

    private object Us : RegionSignals {
        override fun phoneNetwork(): String = "US"

        override suspend fun phoneLocation(): String? = null

        override suspend fun countryOf(place: WeatherLocation): String? = null

        override fun deviceSettings(): String? = null
    }

    private object NoFix : LocationSource {
        override fun hasPermission(): Boolean = false

        override fun lastKnownLocation(): WeatherLocation? = null
    }

    private val brooklyn = WeatherLocation(40.69, -73.99, "Brooklyn, NY")

    private fun check(
        transport: Nws,
        posted: Posted,
        hour: Int = 15,
    ): Triple<AlertCheck, AlertRulesRepository, AlertSettingsRepository> {
        val store =
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { File(dir, "settings.preferences_pb").toOkioPath() },
            )
        val rules = AlertRulesRepository(store)
        val settings = AlertSettingsRepository(store)
        val now = Instant.parse("2026-06-24T%02d:00:00Z".format(hour))
        val check =
            AlertCheck(
                rules,
                settings,
                LocationResolver(
                    NoFix,
                    DataStoreLastLocationStore(store),
                    chosenPlace = { null },
                    fallback = { brooklyn },
                ),
                WeatherRepository(NwsClient(transport = transport), clock = { now }),
                RegionRepository(store, Us),
                posted,
                clock = { now },
                zone = { TimeZone.UTC },
            )
        return Triple(check, rules, settings)
    }

    private val warm =
        AlertRule("warm", AlertSubject.Field(WeatherField.TEMPERATURE), Comparison.ABOVE, threshold = 60.0)

    @Test
    fun `a quiet install fetches nothing and posts nothing`() =
        runTest {
            val nws = Nws()
            val posted = Posted()
            val (check, _, _) = check(nws, posted)
            assertEquals(AlertCheck.Outcome.DONE, check.run())
            assertEquals(0, nws.calls, "no rules and no safety alerts: no network at all")
            assertTrue(posted.rules.isEmpty() && posted.safety.isEmpty())
        }

    @Test
    fun `a rule that holds notifies once, then stays quiet while it keeps holding`() =
        runTest {
            val posted = Posted()
            val (check, rules, _) = check(Nws(), posted)
            rules.save(listOf(warm))
            assertEquals(AlertCheck.Outcome.DONE, check.run())
            assertEquals(listOf("warm" to false), posted.rules)
            assertEquals(setOf("warm"), rules.firingIds())
            check.run()
            assertEquals(1, posted.rules.size, "transition dedup: still firing is not news")
        }

    @Test
    fun `quiet hours deliver silently rather than dropping the alert`() =
        runTest {
            val posted = Posted()
            val (check, rules, settings) = check(Nws(), posted, hour = 23)
            rules.save(listOf(warm))
            settings.save(settings.current().copy(quietHoursEnabled = true))
            check.run()
            assertEquals(listOf("warm" to true), posted.rules)
        }

    @Test
    fun `a new safety alert is posted once, even with no rules`() =
        runTest {
            val posted = Posted()
            val (check, _, settings) = check(Nws(), posted)
            settings.save(settings.current().copy(safetyNotifications = true))
            check.run()
            check.run()
            assertEquals(listOf("urn:1"), posted.safety)
        }

    @Test
    fun `a network failure asks the scheduler to retry and changes nothing`() =
        runTest {
            val posted = Posted()
            val (check, rules, _) = check(Nws(failing = true), posted)
            rules.save(listOf(warm))
            assertEquals(AlertCheck.Outcome.RETRY, check.run())
            assertTrue(posted.rules.isEmpty())
            assertEquals(listOf(warm), rules.rules.first())
        }

    private companion object {
        const val POINTS =
            """{"properties":{"gridId":"OKX","gridX":33,"gridY":35,
            "forecastZone":"https://api.weather.gov/zones/forecast/NYZ072",
            "relativeLocation":{"properties":{"city":"Brooklyn","state":"NY"}}}}"""
        const val STATIONS = """{"features":[{"properties":{"stationIdentifier":"KNYC"}}]}"""
        const val OBSERVATION =
            """{"properties":{"timestamp":"2026-06-24T14:55:00Z",
            "temperature":{"value":20.0,"unitCode":"wmoUnit:degC"},"textDescription":"Clear"}}"""
        const val ALERTS =
            """{"features":[{"properties":{"id":"urn:1","event":"Tornado Warning",
              "severity":"Extreme","headline":"Tornado Warning until 5 PM"}}]}"""
    }
}
