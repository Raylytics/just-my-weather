package io.raylytics.justmyweather.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.raylytics.justmyweather.data.nws.DailyPeriod
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Instant

class DayRangeTest {
    private val louisville = TimeZone.of("America/Kentucky/Louisville")
    private val nov11 = LocalDate(2026, 11, 11)
    private val nov10 = LocalDate(2026, 11, 10)
    private val nov12 = LocalDate(2026, 11, 12)

    private fun period(start: String, day: Boolean, temperature: Double?) =
        DailyPeriod(
            name = "",
            startTime = Instant.parse(start),
            isDaytime = day,
            temperatureF = temperature,
            shortForecast = null,
        )

    @Test
    fun `a day period is its date's high and the night after it is its low`() {
        val days =
            DayRanges.fromPeriods(
                listOf(
                    // 6am and 6pm EST: Today and Tonight.
                    period("2026-11-11T11:00:00Z", day = true, 68.0),
                    period("2026-11-11T23:00:00Z", day = false, 41.0),
                    period("2026-11-12T11:00:00Z", day = true, 55.0),
                ),
                louisville,
            )
        assertEquals(listOf(DayRange(nov11, 68.0, 41.0), DayRange(nov12, highF = 55.0)), days)
    }

    @Test
    fun `an overnight period fetched after midnight is the night of the day before`() {
        val days = DayRanges.fromPeriods(listOf(period("2026-11-11T06:00:00Z", day = false, 39.0)), louisville)
        assertEquals(listOf(DayRange(nov10, lowF = 39.0)), days)
    }

    @Test
    fun `a period with no start or no temperature says nothing`() {
        val noStart = DailyPeriod(name = "", isDaytime = true, temperatureF = 70.0, shortForecast = null)
        assertEquals(
            emptyList<DayRange>(),
            DayRanges.fromPeriods(listOf(noStart, period("2026-11-11T11:00:00Z", true, null)), louisville),
        )
    }

    @Test
    fun `a newer forecast replaces values but never erases one with nothing`() {
        val morning = DayRange(nov11, 68.0, 41.0)
        // Evening: NWS no longer lists "Today", only "Tonight".
        assertEquals(DayRange(nov11, 68.0, 43.0), morning.updatedBy(DayRange(nov11, lowF = 43.0)))
    }

    @Test
    fun `the record keeps yesterday, a spare and the days ahead, and compares today with yesterday`() {
        val stored = listOf(DayRange(LocalDate(2026, 11, 8), 50.0, 30.0), DayRange(nov10, 63.0, 44.0))
        val merged = DayRangeHistory.merge(stored, listOf(DayRange(nov11, 68.0, 41.0), DayRange(nov12, 55.0)), nov11)
        assertEquals(listOf(nov10, nov11, nov12), merged.map { it.date })
        assertEquals(
            DayComparison(DayRange(nov11, 68.0, 41.0), DayRange(nov10, 63.0, 44.0)),
            DayRangeHistory.compare(merged, nov11),
        )
        assertNull(DayRangeHistory.compare(merged, LocalDate(2026, 11, 13)))
    }

    @Test
    fun `a fetch that no longer covers today still compares, from the day it was recorded ahead`(
        @TempDir dir: File,
    ) = runTest {
        // Abroad: MET's fold drops today after the morning, so a midday fetch
        // starts at tomorrow. Today was recorded yesterday, as a day ahead.
        val file = File(dir, "p.preferences_pb")
        val history = DayRangeHistory(PreferenceDataStoreFactory.createWithPath(produceFile = { file.toOkioPath() }))
        history.record("59.91,10.75", listOf(DayRange(nov10, 50.0, 40.0), DayRange(nov11, 47.0, 38.0)), nov10)
        val midday = history.record("59.91,10.75", listOf(DayRange(nov12, 45.0, 36.0)), nov11)
        assertEquals(DayComparison(DayRange(nov11, 47.0, 38.0), DayRange(nov10, 50.0, 40.0)), midday)
    }

    @Test
    fun `recorded days survive in the store and become tomorrow's yesterday`(
        @TempDir dir: File,
    ) = runTest {
        val file = File(dir, "p.preferences_pb")
        val store = PreferenceDataStoreFactory.createWithPath(produceFile = { file.toOkioPath() })
        val history = DayRangeHistory(store)
        val first = history.record("38.25,-85.76", listOf(DayRange(nov10, 63.0, 44.0)), nov10)
        assertEquals(DayComparison(DayRange(nov10, 63.0, 44.0), null), first)
        val next = history.record("38.25,-85.76", listOf(DayRange(nov11, 68.0, 41.0)), nov11)
        assertEquals(DayComparison(DayRange(nov11, 68.0, 41.0), DayRange(nov10, 63.0, 44.0)), next)
        // Another place has its own record.
        assertEquals(
            DayComparison(DayRange(nov11, 50.0), null),
            history.record("40.71,-74.01", listOf(DayRange(nov11, 50.0)), nov11),
        )
    }

    @Test
    fun `the codec round-trips and survives garbage`() {
        val history = mapOf("38.25,-85.76" to listOf(DayRange(nov10, 63.0, null), DayRange(nov11, 68.0, 41.0)))
        assertEquals(history, DayRangeHistoryCodec.decode(DayRangeHistoryCodec.encode(history)))
        assertEquals(emptyMap<String, List<DayRange>>(), DayRangeHistoryCodec.decode("{not json"))
        assertEquals(emptyMap<String, List<DayRange>>(), DayRangeHistoryCodec.decode(null))
        // A day with an unreadable date is dropped; its neighbours stay.
        assertEquals(
            mapOf("k" to listOf(DayRange(nov11, highF = 68.0))),
            DayRangeHistoryCodec.decode(
                """{"k":[{"date":"yesterday"},{"date":"2026-11-11","highF":68.0,"extra":1}]}""",
            ),
        )
    }
}
