package io.raylytics.justmyweather.data.nws

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class ObservationFillTest {
    private val at = Instant.parse("2026-11-12T16:10:00Z")

    private fun obs(
        temperatureF: Double? = null,
        conditions: String? = null,
        visibilityMi: Double? = null,
        precipitationIn: Double? = null,
        feelsLikeF: Double? = null,
        observedAt: Instant? = at,
        reportsNoWeather: Boolean = false,
    ) = CurrentObservation(
        observedAt = observedAt,
        temperatureF = temperatureF,
        precipitationIn = precipitationIn,
        windMph = 0.0,
        pressureInHg = 29.9,
        conditions = conditions,
        relativeHumidityPercent = 90.0,
        dewpointF = 51.0,
        visibilityMi = visibilityMi,
        feelsLikeF = feelsLikeF,
        reportsNoWeather = reportsNoWeather,
    )

    /** Mountain View on 2026-11-12: a backyard station, then two airports. */
    private val backyard = obs(temperatureF = 54.0)
    private val moffett = obs(conditions = "Clear", visibilityMi = 9.0, reportsNoWeather = true)
    private val paloAlto = obs(temperatureF = 52.0, conditions = "Mostly Clear", visibilityMi = 10.0)

    @Test
    fun `the nearest station's readings stay, and its gaps come from the nearest station that has them`() {
        val filled = ObservationFill.fill(backyard, listOf(moffett, paloAlto))
        assertEquals(54.0, filled.temperatureF, "the nearest station's own reading, not Palo Alto's")
        assertEquals("Clear", filled.conditions)
        assertEquals(9.0, filled.visibilityMi)
    }

    @Test
    fun `a backup reading from another time is not used`() {
        val stale = moffett.copy(observedAt = Instant.parse("2026-11-12T12:00:00Z"))
        assertNull(ObservationFill.fill(backyard, listOf(stale)).conditions)
        assertNull(ObservationFill.fill(backyard, listOf(moffett.copy(observedAt = null))).conditions)
    }

    @Test
    fun `a dry hour at a full station is 0 of rain, and unknown otherwise`() {
        assertEquals(0.0, ObservationFill.fill(backyard, listOf(moffett)).precipitationIn)
        assertNull(ObservationFill.fill(backyard, listOf(paloAlto)).precipitationIn, "no station said it was dry")
        val raining = obs(precipitationIn = 0.04, conditions = "Light Rain")
        assertEquals(0.04, ObservationFill.fill(backyard, listOf(raining, moffett)).precipitationIn)
    }

    @Test
    fun `with no heat index or wind chill it feels like the air temperature`() {
        assertEquals(54.0, ObservationFill.fill(backyard, emptyList()).feelsLikeF)
        assertEquals(30.0, ObservationFill.fill(backyard.copy(feelsLikeF = 30.0), emptyList()).feelsLikeF)
        assertNull(ObservationFill.fill(obs(), emptyList()).feelsLikeF, "no temperature, nothing to feel")
    }

    @Test
    fun `another station's wind chill never sits beside this station's air`() {
        val windyAirport = obs(temperatureF = 48.0, feelsLikeF = 44.0, conditions = "Clear")
        assertEquals(54.0, ObservationFill.fill(backyard, listOf(windyAirport)).feelsLikeF)
        // With no temperature of its own, both come from the same station.
        val filled = ObservationFill.fill(obs(), listOf(windyAirport))
        assertEquals(48.0, filled.temperatureF)
        assertEquals(44.0, filled.feelsLikeF)
    }

    @Test
    fun `a dry hour is only believed from the station whose sky is shown`() {
        val showering = obs(conditions = "Light Rain")
        val cloudyDry = obs(conditions = "Cloudy", reportsNoWeather = true)
        val filled = ObservationFill.fill(backyard, listOf(showering, cloudyDry))
        assertEquals("Light Rain", filled.conditions)
        assertNull(filled.precipitationIn, "not 0 beside Light Rain")
    }

    @Test
    fun `a complete reading has no gaps to fill`() {
        assertTrue(ObservationFill.hasGaps(backyard))
        assertFalse(ObservationFill.hasGaps(paloAlto.copy(precipitationIn = 0.0)))
    }
}
