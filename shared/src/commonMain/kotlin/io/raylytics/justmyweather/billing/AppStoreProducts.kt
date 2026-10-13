package io.raylytics.justmyweather.billing

/**
 * The App Store's names for the products the shared code knows by their Play
 * id. App Store product ids are unique across a developer account, not per
 * app, and `remove_ads` is already open-frame's there, so this app's is
 * longer. Like Play's, an App Store product id can never be changed or
 * reused (just-my-weather-k7j).
 *
 * The shared code ([RemoveAdsManager]) speaks Play ids only; the iOS gateway
 * translates at its edge, both ways.
 */
object AppStoreProducts {
    private val byShared = mapOf(REMOVE_ADS_PRODUCT_ID to "remove_ads_just_my_weather")
    private val byAppStore = byShared.entries.associate { (shared, appStore) -> appStore to shared }

    /** The App Store id for a shared id; an unmapped id passes through. */
    fun appStoreId(sharedId: String): String = byShared[sharedId] ?: sharedId

    /** The shared id for an App Store id, or null for a product this app
     * doesn't sell. Null rather than pass-through: a bare `remove_ads` (the
     * id open-frame owns) must never read as this app's Remove Ads. */
    fun sharedId(appStoreId: String): String? = byAppStore[appStoreId]
}
