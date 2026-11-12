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
 * "Same" is written out rather than as ↑0°. A side with no value reads "—".
 */
object VsYesterday {
    fun format(comparison: DayComparison?, conventions: Conventions): String {
        val today = comparison?.today ?: return "—"
        val yesterday = comparison.yesterday
        return listOf(
            line("High", today.highF, yesterday?.highF, conventions),
            line("Low", today.lowF, yesterday?.lowF, conventions),
        ).joinToString("\n")
    }

    private fun line(name: String, todayF: Double?, yesterdayF: Double?, conventions: Conventions): String {
        val today = todayF?.let { shown(it, conventions) } ?: return "$name —"
        val change = yesterdayF?.let { shown(it, conventions) }?.let { today - it }
        return "$name $today° ${difference(change)}"
    }

    private fun difference(change: Int?): String =
        when {
            change == null -> "—"
            change == 0 -> "same"
            change > 0 -> "↑$change°"
            else -> "↓${abs(change)}°"
        }

    private fun shown(fahrenheit: Double, conventions: Conventions): Int =
        conventions.units.temperature.fromFahrenheit(fahrenheit).roundToInt()
}
