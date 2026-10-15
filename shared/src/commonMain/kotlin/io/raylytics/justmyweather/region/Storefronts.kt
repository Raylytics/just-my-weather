package io.raylytics.justmyweather.region

/**
 * App Store storefronts name their country in ISO 3166-1 alpha-3 ("GBR");
 * the region registry keys by alpha-2 ("GB"), as Google Play does. Only the
 * storefronts the app is offered in need an answer — an unknown one returns
 * null and the region falls to its next clue. Add a row when a region opens
 * on the App Store (docs/REGIONS.md).
 */
object Storefronts {
    private val ALPHA2 =
        mapOf(
            "USA" to "US",
            "CAN" to "CA",
            "AUS" to "AU",
            "NZL" to "NZ",
            "GBR" to "GB",
            "IRL" to "IE",
            "IND" to "IN",
            "PHL" to "PH",
            "NGA" to "NG",
            "ZAF" to "ZA",
            "PAK" to "PK",
            "KEN" to "KE",
            "GHA" to "GH",
            "SGP" to "SG",
            "MYS" to "MY",
        )

    /** The alpha-2 country of storefront [alpha3], or null if unknown. */
    fun country(alpha3: String?): String? = alpha3?.uppercase()?.let(ALPHA2::get)

    /** Every storefront with an answer, for a test against the registry. */
    val known: Collection<String> get() = ALPHA2.values
}
