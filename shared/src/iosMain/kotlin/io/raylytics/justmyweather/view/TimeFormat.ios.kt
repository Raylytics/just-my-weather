package io.raylytics.justmyweather.view

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneForSecondsFromGMT

// iOS twin of TimeFormat.android.kt. The value is already local to some clock,
// so it is pinned to UTC on both sides of the formatter and no zone shifts it.

actual fun LocalDate.format(pattern: TimePattern): String =
    formatter(pattern).stringFromDate(atStartOfDayIn(TimeZone.UTC).toNSDate())

actual fun LocalTime.format(pattern: TimePattern): String = LocalDateTime(LocalDate(2000, 1, 1), this).format(pattern)

actual fun LocalDateTime.format(pattern: TimePattern): String =
    formatter(pattern).stringFromDate(toInstant(TimeZone.UTC).toNSDate())

private fun formatter(pattern: TimePattern): NSDateFormatter =
    NSDateFormatter().apply {
        // en_US_POSIX: English names, and immune to the user's 12/24-hour
        // override, which would otherwise rewrite "h:mm a" behind our back.
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
        dateFormat = pattern.pattern
    }

/** Half-up like Java's %f, with a '.' and no grouping in every locale. */
actual fun fixedDecimals(value: Double, decimals: Int): String =
    NSNumberFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        usesGroupingSeparator = false
        minimumFractionDigits = decimals.toULong()
        maximumFractionDigits = decimals.toULong()
        minimumIntegerDigits = 1u
        roundingMode = NSNumberFormatterRoundHalfUp
    }.stringFromNumber(NSNumber(double = value)) ?: value.toString()
