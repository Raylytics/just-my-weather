package io.raylytics.justmyweather.region

/*
 * What the platform knows about countries: their codes, their English names,
 * and how each writes a date and a time. The region registry builds on these;
 * Android answers from java.util.Locale and java.time (unchanged from before
 * the shared module), iOS from NSLocale and DateFormatter.
 */

/** Every ISO 3166 country code the platform knows. */
expect fun isoCountryCodes(): List<String>

/** "GB" → "United Kingdom"; the code itself when the platform has no name. */
expect fun englishCountryName(code: String): String

/**
 * How the locale that best speaks for [countryCode] writes a MEDIUM date and a
 * SHORT time, as patterns ("dd MMM y", "HH:mm"); "" for one it cannot say.
 */
expect fun countryDateTimePatterns(countryCode: String): CountryPatterns

data class CountryPatterns(val mediumDate: String, val shortTime: String)
