package io.raylytics.justmyweather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.raylytics.justmyweather.alerts.AlertCheck
import io.raylytics.justmyweather.alerts.AlertNotifications
import io.raylytics.justmyweather.billing.BillingGateway
import io.raylytics.justmyweather.billing.RemoveAdsManager
import io.raylytics.justmyweather.data.AdsEntitlementRepository
import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.data.AlertSettingsRepository
import io.raylytics.justmyweather.data.DataStoreLastLocationStore
import io.raylytics.justmyweather.data.DataStorePointCache
import io.raylytics.justmyweather.data.DataStoreSnapshotCache
import io.raylytics.justmyweather.data.GadgetbridgeSettingsRepository
import io.raylytics.justmyweather.data.ThemeConfigRepository
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.metno.MetNoClient
import io.raylytics.justmyweather.data.nws.HttpTransport
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.places.PlaceCatalog
import io.raylytics.justmyweather.data.places.PlaceLookup
import io.raylytics.justmyweather.data.places.SavedPlacesRepository
import io.raylytics.justmyweather.location.LocationResolver
import io.raylytics.justmyweather.location.LocationSource
import io.raylytics.justmyweather.region.RegionRepository
import io.raylytics.justmyweather.region.RegionSignals
import kotlinx.coroutines.CoroutineScope

/**
 * Plain manual dependency wiring — no DI framework — for everything both apps
 * share. For an app this size a single readable graph beats annotations a
 * first-time contributor would have to learn: every dependency is constructed
 * in one place, in plain sight.
 *
 * The platform hands in what only it can make — HTTP transports, the settings
 * file, where the gazetteer lives, the location and region signals, the store
 * that sells Remove Ads, and how a notification is posted. Android's
 * AppContainer (app/…/JustMyWeatherApp.kt) extends this with its own extras
 * (widgets, Gadgetbridge, the consent SDK); the iOS host builds it from Swift.
 */
open class AppGraph(
    dataStore: DataStore<Preferences>,
    /** NWS: deliberately uncached — a tap on Refresh must reach the network,
     * and NWS's cache headers would otherwise swallow it. */
    nwsTransport: HttpTransport,
    /** MET Norway, for places NWS doesn't cover and days eight and nine. With
     * a small HTTP cache: MET's terms ask that a client honour Expires and
     * revalidate with If-Modified-Since. */
    metNoTransport: HttpTransport,
    /** The gazetteer, opened only when something asks; nothing here holds its
     * rows for the life of the process. */
    loadPlaces: suspend () -> PlaceCatalog,
    val locationSource: LocationSource,
    /** The region's clues; given the place lookup, since a fix becomes a
     * country through the gazetteer. */
    regionSignals: (PlaceLookup) -> RegionSignals,
    billingGateway: BillingGateway,
    alertNotifications: AlertNotifications,
    scope: CoroutineScope,
) {
    private val nwsClient = NwsClient(transport = nwsTransport)
    private val metNoClient = MetNoClient(transport = metNoTransport)

    /** Nearest-place answers for a bare coordinate (a fix abroad, the
     * region). Loads its own copy of the gazetteer on first use only. */
    val placeLookup = PlaceLookup(load = loadPlaces)

    // Both caches are persisted so a cold start has something to work with: the
    // point cache reuses the resolved grid instead of re-hitting /points +
    // /stations, and the snapshot cache gives the home screen a real reading to
    // paint while the live fetch is in flight.
    val weatherRepository =
        WeatherRepository(
            nws = nwsClient,
            metNo = metNoClient,
            pointCache = DataStorePointCache(dataStore),
            snapshotCache = DataStoreSnapshotCache(dataStore),
            nearest = placeLookup::nearest,
        )

    // Everything that needs a place asks the resolver, not the location source
    // directly: a moment without a fix must fall back to where we last knew the
    // user to be, not to a default city. The background check is the caller
    // that most depends on it — it never gets a fix at all.
    val savedPlacesRepository = SavedPlacesRepository(dataStore)

    // The region — units, dates, ads — and the user's choices about it. Its
    // clues are re-read on every refresh of the glance and the background
    // work; docs/REGIONS.md is the whole story.
    val regionRepository = RegionRepository(dataStore, regionSignals(placeLookup))

    // A chosen place outranks the device fix, for the background check as much
    // as for the glance — they share this one resolver, which is why the
    // choice reaches background polling without any extra wiring.
    val locationResolver =
        LocationResolver(
            locationSource,
            DataStoreLastLocationStore(dataStore),
            chosenPlace = savedPlacesRepository::current,
            fallback = {
                regionRepository.currentRegion().defaultPlace?.let { place ->
                    WeatherLocation(place.latitude, place.longitude, place.label, place.timeZone)
                } ?: WeatherLocation.DEFAULT
            },
        )
    val viewConfigRepository = ViewConfigRepository(dataStore)
    val themeConfigRepository = ThemeConfigRepository(dataStore)
    val alertRulesRepository = AlertRulesRepository(dataStore)
    val alertSettingsRepository = AlertSettingsRepository(dataStore)

    /** The background alert check; each platform decides when it runs. */
    val alertCheck =
        AlertCheck(
            alertRulesRepository,
            alertSettingsRepository,
            locationResolver,
            weatherRepository,
            regionRepository,
            alertNotifications,
        )

    // Gadgetbridge is Android-only, but its switch is a plain stored setting
    // that App Settings reads on both (iOS never shows it).
    val gadgetbridgeSettingsRepository = GadgetbridgeSettingsRepository(dataStore)

    // The banner and the $0.99 that removes it. The entitlement is the
    // device's copy of what the store says; the manager keeps the two in step
    // and is the only thing that talks to the store.
    val adsEntitlementRepository = AdsEntitlementRepository(dataStore)
    val removeAdsManager = RemoveAdsManager(billingGateway, adsEntitlementRepository, scope)
}
