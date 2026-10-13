package io.raylytics.justmyweather.region

import platform.Foundation.ISOCountryCodes
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.availableLocaleIdentifiers
import platform.Foundation.countryCode
import platform.Foundation.localeIdentifier

actual fun isoCountryCodes(): List<String> = NSLocale.ISOCountryCodes.filterIsInstance<String>()

actual fun englishCountryName(code: String): String =
    NSLocale(localeIdentifier = "en").displayNameForKey(NSLocaleCountryCode, code)?.takeIf { it.isNotBlank() } ?: code

actual fun countryDateTimePatterns(countryCode: String): CountryPatterns {
    val locale = localeFor(countryCode)

    fun pattern(date: ULong, time: ULong): String =
        NSDateFormatter().apply {
            this.locale = locale
            dateStyle = date
            timeStyle = time
        }.dateFormat
    return CountryPatterns(
        mediumDate = pattern(NSDateFormatterMediumStyle, NSDateFormatterNoStyle),
        shortTime = pattern(NSDateFormatterNoStyle, NSDateFormatterShortStyle),
    )
}

/** As on Android: the country's English locale when there is one (en_GB,
 * en_IN), else the first locale for that country, else plain en_<code>. */
private fun localeFor(code: String): NSLocale {
    val candidates =
        NSLocale.availableLocaleIdentifiers.filterIsInstance<String>()
            .map { NSLocale(localeIdentifier = it) }
            .filter { it.countryCode == code && it.localeIdentifier.count { c -> c == '_' } == 1 }
    return candidates.firstOrNull { it.localeIdentifier.startsWith("en_") }
        ?: candidates.firstOrNull()
        ?: NSLocale(localeIdentifier = "en_$code")
}
