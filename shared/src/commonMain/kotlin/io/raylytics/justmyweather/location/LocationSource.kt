package io.raylytics.justmyweather.location

import io.raylytics.justmyweather.data.WeatherLocation

/**
 * Where the phone is, coarsely, if the user allowed it. Android answers from
 * LocationManager (app/…/location/LocationProvider.kt), iOS from CoreLocation
 * (iosApp/JustMyWeather/Platform/LocationService.swift). Coarse on purpose on
 * both: a weather grid cell needs nothing finer.
 */
interface LocationSource {
    fun hasPermission(): Boolean

    /** The most recent fix the platform already holds — no new request is
     * made — or null when there is none or no permission. */
    fun lastKnownLocation(): WeatherLocation?
}
