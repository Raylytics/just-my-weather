package io.raylytics.justmyweather.data

import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Everything the UI might show for one place at one moment, already in display
 * units. The home view reads only the fields the user has chosen to surface;
 * the rest are carried so the customization layer can promote them without a
 * re-fetch. This is the single domain object the UI layer depends on — the NWS
 * wire shapes never leak past the repository.
 */
data class WeatherSnapshot(
    val locationLabel: String,
    /** Whole-degree temperature is what people read at a glance. */
    val temperatureF: Double?,
    /** Plain-language conditions from the station ("Mostly Cloudy"). */
    val conditions: String?,
    val windMph: Double?,
    val precipitationIn: Double?,
    val pressureInHg: Double?,
    /** When the reading was taken; null when the station omitted it. */
    val observedAt: Instant?,
    /**
     * First carried for the Gadgetbridge export (WeatherSpec has a field for
     * it, and sending 0 for an unknown humidity would render as a confident
     * "0%" on the watch); a glance module since 0.4.1. Null when not reported.
     */
    val relativeHumidityPercent: Double? = null,
    /** Wind bearing in degrees clockwise from true north, 0–360. */
    val windDirectionDegrees: Double? = null,
    /** The station's apparent temperature — heat index or wind chill,
     * whichever it sent — or null when it feels like the temperature. */
    val feelsLikeF: Double? = null,
    val dewpointF: Double? = null,
    /** How far one can see, in statute miles. Null when not reported —
     * always, outside NWS territory: MET Norway's forecast has none. */
    val visibilityMi: Double? = null,
    /**
     * IANA id of the place this reading is FOR, from the NWS point lookup —
     * not the device's zone. Kept as a string so the data layer cannot fail on
     * an id it does not recognise; [zone] does the parsing, safely.
     *
     * Null when unknown (an older cached point, or a lookup that never
     * carried one), and the UI then falls back to the device's zone, which is
     * what the app always did.
     */
    val timeZone: String? = null,
    /**
     * True when no station reported this: outside NWS territory the reading
     * is MET Norway's forecast for the current hour, and [observedAt] is that
     * hour's start. The glance words its age differently so a forecast never
     * passes for a measurement.
     */
    val fromForecast: Boolean = false,
) {
    /**
     * The place's zone, or null if unknown or unparseable.
     *
     * Parsed here rather than at construction so a garbage id — a corrupted
     * cache, a zone this JVM has never heard of — costs a fallback to the
     * device's zone instead of taking down the reading it is attached to.
     */
    val zone: TimeZone?
        get() = timeZone?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() }
}
