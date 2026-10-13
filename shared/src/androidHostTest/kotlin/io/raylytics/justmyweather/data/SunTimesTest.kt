package io.raylytics.justmyweather.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Sun times against the US Naval Observatory.
 *
 * The expectations below were taken from USNO's own service
 * (aa.usno.navy.mil/api/rstt/oneday, tz=0) on 2026-08-13, and cross-checked
 * against open-meteo. They are NOT self-generated from this implementation,
 * which would only prove it agrees with itself.
 *
 * Worth recording why the source matters: an early draft was validated against
 * sunrise-sunset.org, which disagreed by five minutes at 64°N and led to a
 * rewrite chasing an error that was the reference's, not ours. USNO and
 * open-meteo agree with each other and with this code.
 *
 * The tolerance below absorbs USNO's minute-rounding plus the difference
 * between their fuller model and this one. The worst deviation measured across
 * these cases was about 90 seconds (New York); two minutes leaves margin
 * without letting a real regression through — five minutes, the size of the
 * error a coarse solar model produces at high latitude, would still fail.
 */
class SunTimesTest {
    private val tolerance: Duration = 2.minutes

    private fun assertNear(expected: String, actual: Instant?, what: String) {
        assertNotNull(actual, "$what: expected about $expected, got null")
        val off = abs((actual!! - Instant.parse(expected)).inWholeSeconds)
        assertTrue(off <= tolerance.inWholeSeconds, "$what: expected about $expected, got $actual (${off}s off)")
    }

    @Test
    fun `mid-latitude northern, matching USNO`() {
        // Louisville, the developer's own sky. USNO: rise 10:56Z, set 00:40Z —
        // both of which are 13 August where he lives.
        val day =
            SunTimes.forDate(38.2527, -85.7585, LocalDate(2026, 8, 13), TimeZone.of("America/Kentucky/Louisville"))
        assertNear("2026-08-13T10:56:00Z", day.sunrise, "Louisville sunrise")
        assertNear("2026-08-14T00:40:00Z", day.sunset, "Louisville sunset")
    }

    @Test
    fun `the app's own default location`() {
        // WeatherLocation.DEFAULT is New York. USNO: rise 10:03Z, set 23:58Z.
        val day = SunTimes.forDate(40.7128, -74.0060, LocalDate(2026, 8, 13), TimeZone.of("America/New_York"))
        assertNear("2026-08-13T10:03:00Z", day.sunrise, "New York sunrise")
        assertNear("2026-08-13T23:58:00Z", day.sunset, "New York sunset")
    }

    @Test
    fun `high latitude in deep winter, where a coarse model goes wrong`() {
        // Reykjavik, 64°N, winter solstice: a four-hour day, and the case that
        // separates a good solar model from a passable one. USNO: 11:22, 15:29.
        val day = SunTimes.forDate(64.1466, -21.9426, LocalDate(2026, 12, 21), TimeZone.of("Atlantic/Reykjavik"))
        assertNear("2026-12-21T11:22:00Z", day.sunrise, "Reykjavik sunrise")
        assertNear("2026-12-21T15:29:00Z", day.sunset, "Reykjavik sunset")
    }

    @Test
    fun `southern hemisphere, where the local date runs ahead of UTC`() {
        // Sydney: the sunrise USNO reports at 20:36Z on the 12th is the MORNING
        // of the 13th there. Asking by local date has to find it across that
        // UTC boundary — the thing "the next of each" never had to do.
        val day = SunTimes.forDate(-33.8688, 151.2093, LocalDate(2026, 8, 13), TimeZone.of("Australia/Sydney"))
        assertNear("2026-08-12T20:36:00Z", day.sunrise, "Sydney sunrise")
        assertNear("2026-08-13T07:24:00Z", day.sunset, "Sydney sunset")
    }

    @Test
    fun `the equator at an equinox`() {
        // Quito. USNO: 11:18, 23:24.
        val day = SunTimes.forDate(-0.1807, -78.4678, LocalDate(2026, 3, 20), TimeZone.of("America/Guayaquil"))
        assertNear("2026-03-20T11:18:00Z", day.sunrise, "Quito sunrise")
        assertNear("2026-03-20T23:24:00Z", day.sunset, "Quito sunset")
    }

    @Test
    fun `polar night and midnight sun have no answer, and say so`() {
        // Longyearbyen at 78°N. The sun does not rise in December nor set in
        // June, and null is the honest answer — not a fabricated time, and not
        // a crash. This is why the times are nullable at all.
        val zone = TimeZone.of("Arctic/Longyearbyen")
        val december = SunTimes.forDate(78.2232, 15.6267, LocalDate(2026, 12, 21), zone)
        assertNull(december.sunrise, "no sunrise during polar night")
        assertNull(december.sunset, "no sunset during polar night")

        val june = SunTimes.forDate(78.2232, 15.6267, LocalDate(2026, 6, 21), zone)
        assertNull(june.sunrise, "no sunrise during midnight sun")
        assertNull(june.sunset, "no sunset during midnight sun")
    }

    @Test
    fun `every day of the year has its times, in zones either side of UTC`() {
        // The search in eventOnLocalDate looks a day either side of the UTC
        // date, because a local day straddles two of them at every offset but
        // zero. Its one reachable failure is a FALSE NULL — a straddle the
        // window misses — so this asserts the events are THERE, day by day,
        // for a whole year.
        //
        // Asserting only "a returned time falls on its date" would be vacuous:
        // forDate returns a time only when it does. That check is kept below
        // as a guard on the filter itself, but it is the counts that have
        // teeth.
        val places =
            listOf(
                Triple(38.2527, -85.7585, TimeZone.of("America/Kentucky/Louisville")),
                Triple(-33.8688, 151.2093, TimeZone.of("Australia/Sydney")),
                Triple(35.6762, 139.6503, TimeZone.of("Asia/Tokyo")),
                // Kiritimati keeps UTC+14 at longitude 157 WEST — the widest
                // gap there is between a local date and the UTC date its
                // sunrise falls on, and the case the window exists for.
                Triple(1.9, -157.4, TimeZone.of("Pacific/Kiritimati")),
                Triple(64.1466, -21.9426, TimeZone.of("Atlantic/Reykjavik")),
            )
        for ((lat, lon, zone) in places) {
            var rises = 0
            var sets = 0
            var days = 0
            var date = LocalDate(2026, 1, 1)
            while (date.year == 2026) {
                val day = SunTimes.forDate(lat, lon, date, zone)
                day.sunrise?.let {
                    rises++
                    assertEquals(date, it.toLocalDateTime(zone).date, "sunrise off its date, $zone $date")
                }
                day.sunset?.let {
                    sets++
                    assertEquals(date, it.toLocalDateTime(zone).date, "sunset off its date, $zone $date")
                }
                days++
                date = date.plus(1, DateTimeUnit.DAY)
            }
            assertEquals(365, days, "2026 is not a leap year")
            assertEquals(365, rises, "every day at $zone should have a sunrise")

            if (zone == TimeZone.of("Atlantic/Reykjavik")) {
                // One genuine exception, and worth knowing rather than
                // rounding away: at 64°N around the solstice the sun sets
                // just after midnight, so 15 June's sunset lands on the 16th
                // and the 15th has none of its own. A real null, not a miss.
                assertEquals(364, sets, "Reykjavik loses exactly one sunset to the solstice straddle")
                assertNull(
                    SunTimes.forDate(lat, lon, LocalDate(2026, 6, 15), zone).sunset,
                    "the known straddle is 15 June",
                )
            } else {
                assertEquals(365, sets, "every day at $zone should have a sunset")
            }
        }
    }

    @Test
    fun `daysFrom returns consecutive days starting where asked`() {
        val start = LocalDate(2026, 8, 13)
        val days = SunTimes.daysFrom(38.2527, -85.7585, start, TimeZone.of("America/Kentucky/Louisville"), 3)
        assertEquals((0..2).map { start.plus(it, DateTimeUnit.DAY) }, days.map { it.date })
        // And the sun keeps setting a little earlier through August.
        assertTrue(days[1].sunset!! < days[0].sunset!! + 86400.seconds, "sunset drifts earlier")
    }
}
