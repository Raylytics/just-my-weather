package io.raylytics.justmyweather.view

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/*
 * The formatting seam. Dates, times and fixed-point numbers become text
 * through these few functions and nowhere else, because text is where the
 * platforms differ: Android formats with java.time and java.util (the code the
 * app shipped with, unchanged), iOS with Foundation. Everything above this
 * line — which pattern, which units, which order — is shared.
 */

/**
 * A date/time pattern in the letters java.time and Unicode TR35 (Apple's
 * formatter) agree on: "h:mm a", "EEE M/d", "HH:00". Names (Oct, Tuesday, PM)
 * always come out in English, because the UI is English — see [Conventions].
 * No zone letters: every value formatted is already local to some clock.
 */
class TimePattern(val pattern: String) {
    override fun toString(): String = pattern
}

expect fun LocalDate.format(pattern: TimePattern): String

expect fun LocalTime.format(pattern: TimePattern): String

expect fun LocalDateTime.format(pattern: TimePattern): String

/** [value] to exactly [decimals] places, with a '.' whatever the phone's
 * language: "1020.5", never "1020,5" beside an English unit label. */
expect fun fixedDecimals(value: Double, decimals: Int): String
