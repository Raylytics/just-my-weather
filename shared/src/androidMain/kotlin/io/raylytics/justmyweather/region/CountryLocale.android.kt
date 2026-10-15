package io.raylytics.justmyweather.region

import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

actual fun isoCountryCodes(): List<String> = Locale.getISOCountries().toList()

actual fun englishCountryName(code: String): String = Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank {
    code
}

actual fun countryDateTimePatterns(countryCode: String): CountryPatterns {
    val locale = localeFor(countryCode)
    // MEDIUM, not SHORT: a year-first short pattern (South Africa's y/MM/dd)
    // hides whether the day or the month leads, where the medium one spells it
    // out (dd MMM y; Japan's stays y/MM/dd, month first, which is right there).
    return CountryPatterns(
        mediumDate = shortPattern(locale, FormatStyle.MEDIUM, null),
        shortTime = shortPattern(locale, null, FormatStyle.SHORT),
    )
}

/** The locale that best speaks for a country's conventions: its English
 * variant when the platform has one (en-GB, en-IN — how an English UI there
 * writes dates), else the first language the country uses. */
private fun localeFor(code: String): Locale {
    val candidates = Locale.getAvailableLocales().filter { it.country == code && it.variant.isEmpty() }
    return candidates.firstOrNull { it.language == "en" } ?: candidates.firstOrNull() ?: Locale("en", code)
}

private fun shortPattern(locale: Locale, date: FormatStyle?, time: FormatStyle?): String =
    runCatching {
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(date, time, IsoChronology.INSTANCE, locale)
    }.getOrDefault("")
