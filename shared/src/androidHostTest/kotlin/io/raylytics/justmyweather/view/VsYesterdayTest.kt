package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.DayComparison
import io.raylytics.justmyweather.data.DayRange
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VsYesterdayTest {
    private val today = LocalDate(2026, 11, 11)
    private val yesterday = LocalDate(2026, 11, 10)

    @Test
    fun `each line is today's value and its change from yesterday`() {
        val c = DayComparison(DayRange(today, 68.0, 41.0), DayRange(yesterday, 63.0, 44.0))
        assertEquals("High 68° ↑5°\nLow 41° ↓3°", VsYesterday.format(c, Conventions.US))
    }

    @Test
    fun `the change is between the displayed values, so it adds up in Celsius too`() {
        // 68°F = 20°C and 63°F = 17.2 -> 17°C: the change reads 3, not 2.8.
        val c = DayComparison(DayRange(today, 68.0, 41.0), DayRange(yesterday, 63.0, 41.4))
        val celsius = Conventions.US.copy(units = Conventions.US.units.copy(temperature = TemperatureUnit.CELSIUS))
        assertEquals("High 20° ↑3°\nLow 5° 0°", VsYesterday.format(c, celsius))
    }

    @Test
    fun `what is not known reads as a dash`() {
        assertEquals("—", VsYesterday.format(null, Conventions.US))
        assertEquals(
            "High 68° —\nLow —",
            VsYesterday.format(DayComparison(DayRange(today, highF = 68.0), null), Conventions.US),
        )
    }
}
