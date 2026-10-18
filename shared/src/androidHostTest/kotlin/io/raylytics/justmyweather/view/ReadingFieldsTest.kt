package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.alerts.AlertSubject
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.region.Regions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * The three readings added in 0.4.1 — humidity, dew point, visibility —
 * which the store listing had promised. Each is a one-cell module, off by
 * default, alertable, and reads in the region's units.
 */
class ReadingFieldsTest {
    private val snapshot =
        WeatherSnapshot(
            "Test", 93.0, "Sunny", 5.0, 0.0, 29.9,
            observedAt = null,
            relativeHumidityPercent = 58.4,
            dewpointF = 70.3,
            visibilityMi = 10.0,
        )
    private val canada = Regions.of("CA").conventions

    @Test
    fun `humidity is a percentage, the same in every region`() {
        assertEquals("58%", WeatherField.HUMIDITY.format(snapshot, Conventions.US))
        assertEquals("58%", WeatherField.HUMIDITY.format(snapshot, canada))
        assertEquals(58.4, WeatherField.HUMIDITY.numericValue(snapshot))
        assertNull(WeatherField.HUMIDITY.format(snapshot.copy(relativeHumidityPercent = null), Conventions.US))
        assertTrue(WeatherField.HUMIDITY.isForecastable, "the hourly forecast carries it")
    }

    @Test
    fun `dew point is a temperature`() {
        assertEquals("70°", WeatherField.DEW_POINT.format(snapshot, Conventions.US))
        assertEquals("21°", WeatherField.DEW_POINT.format(snapshot, canada))
        assertTrue(WeatherField.DEW_POINT.isForecastable, "the hourly forecast carries it")
        val subject = AlertSubject.Field(WeatherField.DEW_POINT)
        // A rule typed as 21 °C is stored in Fahrenheit and reads back as typed.
        assertEquals(69.8, subject.toCanonical(21.0, canada), 1e-6)
        assertEquals("21°", subject.formatThreshold(69.8, canada))
    }

    @Test
    fun `visibility reads in miles or kilometres and is observed only`() {
        assertEquals("10 mi", WeatherField.VISIBILITY.format(snapshot, Conventions.US))
        assertEquals("16 km", WeatherField.VISIBILITY.format(snapshot, canada))
        // Outside NWS territory nothing reports it; the tile shows a dash.
        assertNull(WeatherField.VISIBILITY.format(snapshot.copy(visibilityMi = null), Conventions.US))
        assertFalse(WeatherField.VISIBILITY.isForecastable)
        val subject = AlertSubject.Field(WeatherField.VISIBILITY)
        assertEquals("km", subject.unitLabel(canada))
        assertEquals(1.0, subject.toCanonical(1.609344, canada), 1e-9)
        assertEquals("1 km", subject.format(1.0 / 1.609344, canada))
    }

    @Test
    fun `all three are one-cell modules, hidden by default, alertable, and persist by key`() {
        for (field in listOf(WeatherField.HUMIDITY, WeatherField.DEW_POINT, WeatherField.VISIBILITY)) {
            assertEquals(ModuleSize.CELL, field.defaultSize, field.name)
            assertEquals(ModuleSize.CELL, field.minSize, field.name)
            assertTrue(field.isNumeric, field.name)
            assertTrue(field in WeatherField.alertable, field.name)
            assertFalse(ViewConfig.DEFAULT.shows(ModuleKey.Reading(field)), "${field.name} ships off")
        }
        assertEquals(ModuleKey.Reading(WeatherField.HUMIDITY), ModuleKey.byKey("humidity"))
        assertEquals(ModuleKey.Reading(WeatherField.DEW_POINT), ModuleKey.byKey("dew_point"))
        assertEquals(ModuleKey.Reading(WeatherField.VISIBILITY), ModuleKey.byKey("visibility"))
    }

    @Test
    fun `forecastable is exactly what an hour carries`() {
        val hour =
            ForecastPoint(
                startTime = Instant.parse("2026-10-18T15:00:00Z"),
                temperatureF = 80.0,
                windMph = 5.0,
                relativeHumidityPercent = 60.0,
                dewpointF = 65.0,
            )
        for (field in WeatherField.entries) {
            assertEquals(field.isForecastable, field.forecastValue(hour) != null, field.name)
        }
        assertEquals(
            listOf(WeatherField.TEMPERATURE, WeatherField.WIND, WeatherField.HUMIDITY, WeatherField.DEW_POINT),
            WeatherField.entries.filter { it.isForecastable },
        )
    }
}
