package io.raylytics.justmyweather.region

import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.places.PlaceLookup
import io.raylytics.justmyweather.location.LocationSource
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.localTimeZone

/**
 * iOS twin of AndroidRegionSignals. An iPhone has no mobile-network country to
 * read, so the region comes from (docs/REGIONS.md, "On iOS"): the App Store
 * storefront, the last fix and the place shown (via the gazetteer, as on
 * Android), the device's Region setting, and finally its time zone.
 */
class IosRegionSignals(
    private val storefrontCode: () -> String?,
    private val location: LocationSource,
    private val places: PlaceLookup,
) : RegionSignals {
    override fun phoneNetwork(): String? = null

    override fun storefront(): String? = Storefronts.country(storefrontCode())

    override suspend fun phoneLocation(): String? =
        runCatching {
            location.lastKnownLocation()?.let { fix -> places.nearest(fix.latitude, fix.longitude)?.country }
        }.getOrNull()

    override suspend fun countryOf(place: WeatherLocation): String? =
        runCatching { places.nearest(place.latitude, place.longitude)?.country }.getOrNull()

    /** Settings → General → Language & Region → Region: a deliberate choice. */
    override fun deviceSettings(): String? = NSLocale.currentLocale.countryCode?.ifBlank { null }

    override suspend fun timeZoneCountry(): String? =
        runCatching { places.countryOfZone(NSTimeZone.localTimeZone.name) }.getOrNull()
}
