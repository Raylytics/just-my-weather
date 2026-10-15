package io.raylytics.justmyweather.view

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Android formats exactly as it did before the shared module existed: the same
// java.time patterns in Locale.ENGLISH, the same String.format in Locale.US.

actual fun LocalDate.format(pattern: TimePattern): String = toJavaLocalDate().format(pattern.formatter)

actual fun LocalTime.format(pattern: TimePattern): String = toJavaLocalTime().format(pattern.formatter)

actual fun LocalDateTime.format(pattern: TimePattern): String = toJavaLocalDateTime().format(pattern.formatter)

/** Names (Oct, Tuesday, PM) are English because the UI is: a month name in
 * another language inside an English sentence helps nobody. */
private val TimePattern.formatter: DateTimeFormatter get() = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)

/** Locale.US on purpose: a device in German would otherwise write "1020,5"
 * beside an English unit label. (Locale.ROOT, which the point-cache key once
 * used, writes %f identically.) */
actual fun fixedDecimals(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
