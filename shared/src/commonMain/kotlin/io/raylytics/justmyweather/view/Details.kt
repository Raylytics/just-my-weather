package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.DayComparison
import io.raylytics.justmyweather.data.DayRange
import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.data.nws.Units
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/*
 * What a tap on a tile opens: the whole of what is tracked for that data
 * point, as label/value rows and, where NWS wrote one, a paragraph. A tile
 * shows one reading; the sheet shows the observation it came from, or the
 * hour or day it belongs to, in full.
 *
 * Pure — data in, a Detail out — so the content of every sheet is decided
 * and tested on the JVM, and the composable that draws it only walks rows.
 * The glance tiles do not build these themselves because a module's tile
 * only has its own value; the observation the value came from lives with
 * the screen, which is why [Details.ofModule] takes the snapshot.
 */

/** One sheet: a title, a quieter line under it, rows, and an optional
 * paragraph below the rows. */
data class Detail(
    val title: String,
    val subtitle: String? = null,
    val rows: List<DetailRow> = emptyList(),
    val body: String? = null,
)

data class DetailRow(val label: String, val value: String)

object Details {
    /**
     * The tapped module's detail, or null for one that opens nothing at the
     * module level (the forecast: its hours and days open their own). [zone]
     * is the clock the reading's timestamp reads in — the phone's, or the
     * reading's own; see TimesIn.observedZone for why never the screen's
     * current place.
     *
     * A reading opens the whole OBSERVATION, its own field first: the other
     * readings are the context the number was taken in, and "Temperature 93°"
     * alone is what the tile already said.
     */
    fun ofModule(
        module: ModuleValue,
        snapshot: WeatherSnapshot,
        zone: TimeZone,
        conventions: Conventions,
        /** The vs. yesterday module's comparison, which its tile only shows
         * the formatted face of. */
        comparison: DayComparison? = null,
    ): Detail? =
        // Vs. yesterday is not an observation reading: it opens its own
        // comparison, whatever it is drawn as.
        if (module.module == ModuleKey.VsYesterday) {
            ofVsYesterday(module.label, comparison, conventions)
        } else {
            when (val content = module.content) {
                is ModuleContent.Reading -> ofObservation(module, snapshot, zone, conventions)
                is ModuleContent.Sun -> ofSun(module.label, content.days, content.zone, conventions)
                is ModuleContent.Table -> null
                is ModuleContent.Forecast -> null
            }
        }

    /**
     * Each side of the comparison with where it came from, and the change.
     * The tile can only fit the numbers; the sheet says which are forecast
     * and which were measured, since that decides how far to trust them.
     */
    fun ofVsYesterday(label: String, comparison: DayComparison?, conventions: Conventions): Detail {
        val today = comparison?.today
        val yesterday = comparison?.yesterday
        val changes = VsYesterday.rows(comparison, conventions)

        fun source(range: DayRange?, soFar: Boolean = false): String =
            when {
                range == null -> ""
                range.observed -> " · measured"
                soFar -> " · measured so far"
                else -> " · forecast"
            }
        return Detail(
            title = label,
            subtitle =
                if (today == null) {
                    "Not available yet"
                } else {
                    "${today.date.format(conventions.monthDay)} against " +
                        (yesterday?.date?.format(conventions.monthDay) ?: "the day before")
                },
            rows =
                listOf(
                    DetailRow(
                        "High today${source(today, soFar = comparison?.todayHighSoFar == true)}",
                        conventions.degrees(today?.highF),
                    ),
                    DetailRow("High yesterday${source(yesterday)}", conventions.degrees(yesterday?.highF)),
                    DetailRow("High change", changes?.getOrNull(0)?.getOrNull(2)?.ifEmpty { "—" } ?: "—"),
                    DetailRow("Low today${source(today)}", conventions.degrees(today?.lowF)),
                    DetailRow("Low yesterday${source(yesterday)}", conventions.degrees(yesterday?.lowF)),
                    DetailRow("Low change", changes?.getOrNull(1)?.getOrNull(2)?.ifEmpty { "—" } ?: "—"),
                ),
            body =
                "Today is the National Weather Service forecast for this place; once the forecast no longer " +
                    "lists today, its high is the warmest the nearest station has measured so far. Yesterday " +
                    "is what that station measured, or, where it has no record, the forecast the app kept " +
                    "for it. Outside the US yesterday is always the kept forecast.",
        )
    }

    private fun ofObservation(
        module: ModuleValue,
        snapshot: WeatherSnapshot,
        zone: TimeZone,
        conventions: Conventions,
    ): Detail {
        val tapped = module.module.field
        val fields = WeatherField.entries.sortedBy { if (it == tapped) 0 else 1 }
        val observed =
            snapshot.observedAt?.let { ReadingHeading.of(snapshot.fromForecast, conventions.clock(it, zone)) }
        val source = ReadingHeading.FORECAST_SOURCE.takeIf { snapshot.fromForecast }
        return Detail(
            title = module.label,
            subtitle = listOfNotNull(observed, source, snapshot.locationLabel).joinToString(" · "),
            rows =
                buildList {
                    fields.forEach { field ->
                        val value =
                            when (field) {
                                // Wind gets its direction here, which the
                                // tile has no room for.
                                WeatherField.WIND ->
                                    conventions.wind(
                                        snapshot.windMph,
                                        Units.compassPoint(snapshot.windDirectionDegrees),
                                    )
                                else -> field.format(snapshot, conventions) ?: "—"
                            }
                        add(DetailRow(field.defaultLabel, value))
                    }
                },
        )
    }

    private fun ofSun(label: String, days: List<SunDay>, zone: TimeZone, conventions: Conventions): Detail =
        Detail(
            title = label,
            subtitle = "Computed for this place",
            rows =
                days.flatMap { day ->
                    val date = day.date.format(conventions.monthDay)
                    listOf(
                        DetailRow("Sunrise · $date", day.sunrise?.let { conventions.clock(it, zone) } ?: "—"),
                        DetailRow("Sunset · $date", day.sunset?.let { conventions.clock(it, zone) } ?: "—"),
                        DetailRow("Daylight · $date", daylight(day)),
                    )
                },
        )

    /** Every field an hour carries, in the place's clock. */
    fun ofHour(hour: ForecastPoint, zone: TimeZone, conventions: Conventions): Detail {
        val at = hour.startTime.toLocalDateTime(zone)
        return Detail(
            title = at.format(conventions.hour).lowercase(),
            subtitle = "${at.format(conventions.longDate)} · Forecast",
            rows =
                listOf(
                    DetailRow("Temperature", conventions.degrees(hour.temperatureF)),
                    DetailRow("Chance of precipitation", hour.precipProbabilityPercent.percent()),
                    DetailRow("Wind", conventions.wind(hour.windMph, hour.windDirection)),
                    DetailRow("Humidity", hour.relativeHumidityPercent.percent()),
                    DetailRow("Dew point", conventions.degrees(hour.dewpointF)),
                    DetailRow("Conditions", hour.shortForecast ?: "—"),
                ),
        )
    }

    /** Every field a half-day period carries, with NWS's prose beneath. */
    fun ofPeriod(period: DailyPeriod, conventions: Conventions): Detail =
        Detail(
            title = period.name,
            subtitle = if (period.isDaytime) "Forecast · day" else "Forecast · night",
            rows =
                listOf(
                    DetailRow(if (period.isDaytime) "High" else "Low", conventions.degrees(period.temperatureF)),
                    DetailRow("Chance of precipitation", period.precipProbabilityPercent.percent()),
                    DetailRow("Wind", conventions.wind(period.windMph, period.windDirection)),
                    DetailRow("Conditions", period.shortForecast ?: "—"),
                ),
            body = period.detailedForecast,
        )

    private fun daylight(day: SunDay): String {
        val rise = day.sunrise ?: return "—"
        val set = day.sunset ?: return "—"
        val minutes = (set - rise).inWholeMinutes
        if (minutes < 0) return "—"
        return "${minutes / 60} h ${minutes % 60} min"
    }
}

/** "28%", or an em-dash. */
fun Double?.percent(): String = this?.let { "${it.roundToInt()}%" } ?: "—"
