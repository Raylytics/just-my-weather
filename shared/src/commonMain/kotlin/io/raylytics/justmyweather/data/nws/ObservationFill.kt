package io.raylytics.justmyweather.data.nws

import kotlin.math.abs
import kotlin.time.Duration.Companion.hours

/**
 * The nearest station's reading, with what it does not report taken from the
 * stations behind it.
 *
 * The nearest station is often a backyard one (a CWOP station like
 * Mountain View's AW020): temperature, wind and humidity, but no sky
 * description, visibility or rain gauge, so those modules would read "—"
 * for good. The readings it has stay its own; only its gaps are filled, each
 * from the nearest [backups] station (nearest first) that reports it, and
 * only from a reading taken near the same time.
 *
 * Two values are then completed by NWS's own conventions rather than left
 * blank: a full station that describes the sky and reports nothing falling
 * leaves the hour's precipitation out when none fell, so that is 0; and a
 * station sends a heat index or wind chill only outside the range where
 * they apply, so with neither, it feels like the air temperature.
 */
object ObservationFill {
    /** A backup reading further than this from the nearest one's is not
     * the same moment's weather. */
    private val MAX_SKEW = 2.hours

    /** Whether [observation] has a gap the stations behind it could fill. */
    fun hasGaps(observation: CurrentObservation): Boolean =
        observation.conditions == null ||
            observation.visibilityMi == null ||
            observation.pressureInHg == null ||
            observation.relativeHumidityPercent == null ||
            observation.dewpointF == null ||
            observation.windMph == null ||
            observation.temperatureF == null ||
            (observation.precipitationIn == null && !observation.reportsNoWeather)

    fun fill(primary: CurrentObservation, backups: List<CurrentObservation>): CurrentObservation {
        val usable =
            backups.filter { b ->
                val at = b.observedAt ?: return@filter false
                val ref = primary.observedAt ?: return@filter true
                abs((at - ref).inWholeSeconds) <= MAX_SKEW.inWholeSeconds
            }

        fun <T> first(pick: (CurrentObservation) -> T?): T? = usable.firstNotNullOfOrNull(pick)
        // Temperature and feels-like travel together: NWS works a heat index
        // or wind chill out from a station's own temperature and wind, so
        // another station's would sit wrong beside this one's air. Only when
        // the nearest station has no temperature are both borrowed, from the
        // same station.
        val warmth = if (primary.temperatureF != null) primary else usable.firstOrNull { it.temperatureF != null }
        // Likewise precipitation, and "dry", are only believed from the
        // station whose sky is shown (or the nearest one): another station's
        // gauge or clear hour says nothing about rain this one's description
        // reports.
        val sky = if (primary.conditions != null) primary else usable.firstOrNull { it.conditions != null }
        val filled =
            primary.copy(
                temperatureF = warmth?.temperatureF,
                feelsLikeF = warmth?.let { it.feelsLikeF ?: it.temperatureF },
                conditions = sky?.conditions,
                visibilityMi = primary.visibilityMi ?: first { it.visibilityMi },
                pressureInHg = primary.pressureInHg ?: first { it.pressureInHg },
                relativeHumidityPercent = primary.relativeHumidityPercent ?: first { it.relativeHumidityPercent },
                dewpointF = primary.dewpointF ?: first { it.dewpointF },
                windMph = primary.windMph ?: first { it.windMph },
                windDirectionDegrees = primary.windDirectionDegrees ?: first { it.windDirectionDegrees },
                // Rain, measured or inferred, comes from the same station as
                // the sky it sits beside, never a third one's gauge.
                precipitationIn = primary.precipitationIn ?: sky?.precipitationIn,
            )
        val dry = primary.reportsNoWeather || sky?.reportsNoWeather == true
        return filled.copy(precipitationIn = filled.precipitationIn ?: if (dry) 0.0 else null)
    }
}
