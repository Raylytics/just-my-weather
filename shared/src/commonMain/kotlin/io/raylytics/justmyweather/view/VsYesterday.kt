package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.DayComparison
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The vs. yesterday module's text: today's forecast high and low, each with
 * how far it is from yesterday's.
 *
 *     High 68° ↑5°
 *     Low 41° ↓3°
 *
 * The difference is taken between the two values as DISPLAYED — each
 * converted to the user's unit and rounded first — so the arithmetic on
 * screen always checks out (68 against 63 reads 5, never a rounded 4.6), and
 * a difference in °C is a °C difference without a separate delta conversion.
 * No change reads a plain 0°, with no arrow. A side with no value reads "—".
 * The tile draws [rows] in columns so the values and changes line up.
 */
object VsYesterday {
    fun format(comparison: DayComparison?, conventions: Conventions): String =
        rows(comparison, conventions)?.joinToString("\n") { row -> row.filter(String::isNotEmpty).joinToString(" ") }
            ?: "—"

    /**
     * The same, as a table the tile draws in columns: name, today's value,
     * its change ("" when today has no value). Null when there is no
     * comparison at all, which the tile draws as a single dash.
     */
    fun rows(comparison: DayComparison?, conventions: Conventions): List<List<String>>? {
        val today = comparison?.today ?: return null
        val yesterday = comparison.yesterday
        return listOf(
            row("High", today.highF, yesterday?.highF, conventions),
            row("Low", today.lowF, yesterday?.lowF, conventions),
        )
    }

    private fun row(name: String, todayF: Double?, yesterdayF: Double?, conventions: Conventions): List<String> {
        val today = todayF?.let { shown(it, conventions) } ?: return listOf(name, "—", "")
        val change = yesterdayF?.let { shown(it, conventions) }?.let { today - it }
        return listOf(name, "$today°", difference(change))
    }

    private fun difference(change: Int?): String =
        when {
            change == null -> "—"
            change == 0 -> "0°"
            change > 0 -> "↑$change°"
            else -> "↓${abs(change)}°"
        }

    private fun shown(fahrenheit: Double, conventions: Conventions): Int =
        conventions.units.temperature.fromFahrenheit(fahrenheit).roundToInt()
}
