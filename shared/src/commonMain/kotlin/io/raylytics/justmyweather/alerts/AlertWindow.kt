package io.raylytics.justmyweather.alerts

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * When a rule looks. [NOW] tests the current observation — the everyday case.
 * The rest test the hourly forecast over a window *ahead*, so you can be warned
 * before it happens: "overnight low below 35°", "wind above 30 within 12 hours".
 *
 * For a forecast window the comparison direction picks the aggregate that
 * matters: BELOW watches the window's *low* (does it ever dip under?), ABOVE
 * watches its *high* (does it ever climb over?). So "Temperature below 35°
 * overnight" fires when the coldest forecast hour overnight is under 35°.
 *
 * [key] is the stable persistence token — never rename it. [label] is the chip
 * text; [phrase] is the fragment the rule summary and notification read with
 * ("…overnight", "…within 12 hours"); NOW has none since it reads as the bare
 * present tense.
 */
enum class AlertWindow(
    val key: String,
    val label: String,
    val phrase: String,
) {
    NOW("now", "Right now", ""),
    NEXT_6H("next_6h", "Next 6h", "within 6 hours"),
    NEXT_12H("next_12h", "Next 12h", "within 12 hours"),
    NEXT_24H("next_24h", "Next 24h", "within 24 hours"),
    OVERNIGHT("overnight", "Overnight", "overnight"),
    ;

    val isForecast: Boolean get() = this != NOW

    /**
     * Whether a forecast hour at [time] falls in this window, measured from
     * [now] in the user's [zone]. NOW matches nothing on the forecast path
     * (it reads the observation instead).
     */
    fun contains(time: Instant, now: Instant, zone: TimeZone): Boolean =
        when (this) {
            NOW -> false
            NEXT_6H -> withinHours(time, now, 6)
            NEXT_12H -> withinHours(time, now, 12)
            NEXT_24H -> withinHours(time, now, 24)
            // A single imminent night, anchored to local 20:00–08:00 (not a
            // rolling 24h × night-hour intersection), so a late-night check
            // stays on the coming night rather than also catching tomorrow eve.
            OVERNIGHT -> {
                val (start, end) = overnightBand(now, zone)
                time >= now && time >= start && time < end
            }
        }

    private fun withinHours(time: Instant, now: Instant, hours: Long): Boolean =
        time >= now && time < now + hours.hours

    /**
     * The one overnight band [start, end) that is currently active or next to
     * come, as local 20:00 → next-day 08:00 in [zone]. Before 08:00 we're in the
     * night that began the previous evening; otherwise it's tonight's band.
     */
    private fun overnightBand(now: Instant, zone: TimeZone): Pair<Instant, Instant> {
        val local = now.toLocalDateTime(zone)
        val today = local.date

        fun at(date: LocalDate, hour: Int) = LocalDateTime(date, LocalTime(hour, 0)).toInstant(zone)
        return if (local.hour < NIGHT_END_HOUR) {
            at(today.minus(1, DateTimeUnit.DAY), NIGHT_START_HOUR) to at(today, NIGHT_END_HOUR)
        } else {
            at(today, NIGHT_START_HOUR) to at(today.plus(1, DateTimeUnit.DAY), NIGHT_END_HOUR)
        }
    }

    companion object {
        private const val NIGHT_START_HOUR = 20
        private const val NIGHT_END_HOUR = 8

        fun byKey(key: String): AlertWindow? = entries.firstOrNull { it.key == key }
    }
}
