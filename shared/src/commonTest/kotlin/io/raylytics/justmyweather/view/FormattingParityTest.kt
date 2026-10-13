package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.places.PlaceCatalog
import io.raylytics.justmyweather.region.Regions
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The platform seams, held to one set of answers. These run on the JVM (where
 * the Android actuals are the code the app always shipped, so the strings
 * below ARE Android's output) and on the iOS simulator (where they prove the
 * Foundation twins say the same thing). A failure on one platform only is a
 * parity bug in that platform's actual — see docs/PARITY.md.
 */
class FormattingParityTest {
    private val us = Conventions.US
    private val uk = Regions.of("GB").conventions
    private val day = LocalDate(2026, 10, 6)
    private val afternoon = LocalDateTime(day, LocalTime(15, 5))

    @Test
    fun clockAndHours() {
        assertEquals("3:05 PM", afternoon.format(us.clock))
        assertEquals("15:05", afternoon.format(uk.clock))
        assertEquals("3 PM", afternoon.format(us.hour))
        assertEquals("15:00", afternoon.format(uk.hour))
        assertEquals("3PM", afternoon.format(us.tileHour))
        assertEquals("9:30 AM", LocalTime(9, 30).format(us.clock))
        assertEquals("12:00 AM", LocalTime(0, 0).format(us.clock))
        assertEquals("3:05 PM", us.clock(Instant.parse("2026-10-06T15:05:00Z"), TimeZone.UTC))
    }

    @Test
    fun dates() {
        assertEquals("10/6", day.format(us.shortDate))
        assertEquals("6/10", day.format(uk.shortDate))
        assertEquals("Oct 6", day.format(us.monthDay))
        assertEquals("6 Oct", day.format(uk.monthDay))
        assertEquals("Tuesday, October 6", day.format(us.longDate))
        assertEquals("Tuesday 6 October", day.format(uk.longDate))
        assertEquals("Tue 10/6", day.format(us.dayAndDate))
        assertEquals("Tue 6/10", day.format(uk.dayAndDate))
        assertEquals("TUE", day.format(TimePattern("EEE")).uppercase())
    }

    @Test
    fun fixedPointNumbers() {
        assertEquals("30.12", fixedDecimals(30.12, 2))
        assertEquals("102.0", fixedDecimals(101.99, 1))
        assertEquals("-0.50", fixedDecimals(-0.5, 2))
        assertEquals("38.2500", fixedDecimals(38.25, 4))
        assertEquals("1020 hPa", uk.pressure(30.12))
        assertEquals("6.4 mm", uk.precipitation(0.25))
    }

    @Test
    fun halfwayCasesRoundAway() {
        // Values whose shortest decimal form ends in 5 — where a half-even
        // formatter would disagree with Java's half-up %f.
        assertEquals("0.13", fixedDecimals(0.125, 2))
        assertEquals("2.5", fixedDecimals(2.45, 1))
    }

    @Test
    fun countriesFromThePlatform() {
        assertEquals("United Kingdom", Regions.displayName("GB"))
        assertEquals("Japan", Regions.of("JP").name)
        assertEquals(true, Regions.isKnown("no"))
        // Prepared regions pin their date order and clock in the registry, so
        // they read the same everywhere. An unprepared country's are derived
        // from the platform's own locale data (CLDR), which differs between
        // Android releases as well as between Android and iOS — deliberately
        // not asserted here.
        assertEquals(DateOrder.DAY_FIRST, Regions.of("GB").conventions.dateOrder)
        assertEquals(true, Regions.of("GB").conventions.clock24)
        assertEquals(DateOrder.MONTH_FIRST, Regions.of("US").conventions.dateOrder)
        assertEquals(false, Regions.of("CA").conventions.clock24)
    }

    @Test
    fun accentsFold() {
        assertEquals("bayamon", PlaceCatalog.normalize(" Bayamón "))
        assertEquals("sao paulo", PlaceCatalog.normalize("São Paulo"))
    }
}
