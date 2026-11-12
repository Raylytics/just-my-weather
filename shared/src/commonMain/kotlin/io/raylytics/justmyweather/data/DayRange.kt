package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.metno.ExtendedDay
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ObservedTemperature
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * One day's forecast high and low at a place, in °F (canonical, like every
 * stored temperature). Either may be unknown.
 *
 * The "vs. yesterday" module compares two of these. No free source gives
 * yesterday's forecast after the fact, so the app keeps its own: each day's
 * range is recorded while that day is still in the forecast
 * ([DayRangeHistory]), and tomorrow it is yesterday's.
 */
data class DayRange(
    val date: LocalDate,
    val highF: Double? = null,
    val lowF: Double? = null,
    /** What the station actually measured, not a forecast. Past days only. */
    val observed: Boolean = false,
) {
    /** This range, with anything [newer] knows taking over. The latest
     * forecast for a day is the best one, but a forecast that no longer
     * carries a value (NWS drops "Today" once the evening comes) must not
     * erase the one recorded earlier. What was measured beats any forecast:
     * an "Overnight" period fetched after midnight is still yesterday's, and
     * must not reopen a day the station has already closed. */
    fun updatedBy(newer: DayRange): DayRange =
        when {
            newer.observed -> newer
            observed -> this
            else -> copy(highF = newer.highF ?: highF, lowF = newer.lowF ?: lowF)
        }
}

/** Today's range beside yesterday's, as the module draws them. */
data class DayComparison(val today: DayRange, val yesterday: DayRange?)

object DayRanges {
    /**
     * Before this hour, a night period belongs to the day before: NWS's
     * "Overnight" period, fetched after midnight, is the tail of the night
     * that "Tonight" named the evening before.
     */
    private const val NIGHT_ROLLOVER_HOUR = 6

    /**
     * NWS's half-day periods as whole days, in the place's [zone]: a daytime
     * period's temperature is its day's high, and a night period's is the
     * low of the day whose evening it starts (the way NWS words them: "Today,
     * high 68 / Tonight, low 41"). A period with no start time is skipped,
     * since its day cannot be known.
     */
    fun fromPeriods(periods: List<DailyPeriod>, zone: TimeZone): List<DayRange> {
        val days = linkedMapOf<LocalDate, DayRange>()
        for (period in periods) {
            val start = period.startTime?.toLocalDateTime(zone) ?: continue
            val temperature = period.temperatureF ?: continue
            val date =
                if (!period.isDaytime && start.hour < NIGHT_ROLLOVER_HOUR) {
                    start.date.minus(DatePeriod(days = 1))
                } else {
                    start.date
                }
            val day = days[date] ?: DayRange(date)
            // First wins within one fetch: NWS lists one day period and one
            // night period per date, in order.
            days[date] =
                if (period.isDaytime) {
                    day.copy(highF = day.highF ?: temperature)
                } else {
                    day.copy(lowF = day.lowF ?: temperature)
                }
        }
        return days.values.toList()
    }

    /**
     * A station must have reported in at least this many of a day's hours
     * for its readings to stand as that day's high and low. A station that
     * was down half the day would otherwise give half a day's extremes as
     * the whole day's, which is worse than falling back to the forecast.
     */
    private const val MIN_OBSERVED_HOURS = 18

    /**
     * [date]'s actual high and low from what a station reported, in the
     * place's [zone]: the warmest and coolest reading of that calendar day.
     * Null when the station covered too little of the day to say.
     */
    fun fromObservations(readings: List<ObservedTemperature>, date: LocalDate, zone: TimeZone): DayRange? {
        val ofDay = readings.filter { it.at.toLocalDateTime(zone).date == date }
        val hours = ofDay.map { it.at.toLocalDateTime(zone).hour }.toSet()
        if (hours.size < MIN_OBSERVED_HOURS) return null
        return DayRange(
            date,
            highF = ofDay.maxOf { it.temperatureF },
            lowF = ofDay.minOf { it.temperatureF },
            observed = true,
        )
    }

    /** MET Norway's whole days, already cut in the place's calendar. */
    fun fromExtended(days: List<ExtendedDay>): List<DayRange> =
        days.map { DayRange(it.date, highF = it.highF, lowF = it.lowF) }
}
