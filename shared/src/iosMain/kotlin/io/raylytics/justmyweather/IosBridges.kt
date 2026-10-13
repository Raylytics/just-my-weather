package io.raylytics.justmyweather

import io.raylytics.justmyweather.alerts.AlertNotifications
import io.raylytics.justmyweather.billing.ProductOffer
import io.raylytics.justmyweather.location.LocationSource
import io.raylytics.justmyweather.ui.SupportHost
import platform.UIKit.UIView
import platform.UIKit.UIViewController

/*
 * What the Swift app hands the shared code: the iOS twins of Android's
 * platform services (the Swift files in iosApp/JustMyWeather/Platform). Callback-shaped,
 * not suspend, so the Swift side needs no Kotlin coroutines; IosApp adapts
 * them to the common contracts (BillingGateway, RegionSignals, ...).
 */

/** StoreKit 2 (Platform/StoreService.swift) — the App Store half of Remove Ads. */
interface StoreBridge {
    /** The product's offer, or null when the store can't say right now. */
    fun offer(productId: String, completion: (ProductOffer?) -> Unit)

    /** Product ids this Apple Account owns, verified; null when unknown. */
    fun ownedProducts(completion: (List<String>?) -> Unit)

    /** Shows the App Store's purchase sheet. */
    fun purchase(productId: String, completion: (StorePurchase) -> Unit)

    /** The storefront's ISO alpha-3 country ("GBR"), read at launch; null if unknown. */
    fun storefrontCountryCode(): String?
}

/** How a purchase sheet ended. */
enum class StorePurchase { PURCHASED, PENDING, CANCELLED, FAILED }

/** Google Mobile Ads + UMP + App Tracking Transparency (Platform/AdsService.swift). */
interface AdsBridge {
    /** Once per launch: UMP's consent request (and its form where the law
     * asks), then Apple's tracking prompt where allowed; [done] once settled. */
    fun gather(host: UIViewController, done: () -> Unit)

    fun canRequestAds(): Boolean

    fun privacyOptionsRequired(): Boolean

    fun showPrivacyOptions(host: UIViewController, done: () -> Unit)

    /** The anchored adaptive banner's height for [widthPoints]. */
    fun bannerHeight(widthPoints: Double): Double

    /** A loaded banner [widthPoints] wide, shown over [host]. */
    fun makeBanner(widthPoints: Double, host: UIViewController): UIView
}

/** Everything else the iOS host provides. */
interface IosPlatform {
    val location: LocationSource
    val notifications: AlertNotifications
    val support: SupportHost
    val store: StoreBridge
    val ads: AdsBridge

    /** "0.4.0 (build 3)": the version line App Settings ends with. */
    val versionLabel: String

    /** Asks for notification permission, once the user opens Alerts. */
    fun requestNotificationPermission()

    /** Opens the App Store page (Rate Just My Weather). */
    fun openStoreListing()

    /** Schedules (or cancels) the BGAppRefreshTask that runs the alert check. */
    fun scheduleAlertRefresh(enabled: Boolean, everyMinutes: Int)

    /** Whether Background App Refresh is available to the app right now. */
    fun backgroundRefreshAvailable(): Boolean

    /** The view controller sheets are presented over. */
    fun presenter(): UIViewController?
}

/*
 * Constructors for Swift. Kotlin's default arguments don't cross into Swift,
 * so the twins build these shared values through plain functions instead.
 */

/** A bare fix, as the location twin reports it: no label, zone or country. */
fun locationFix(latitude: Double, longitude: Double): io.raylytics.justmyweather.data.WeatherLocation =
    io.raylytics.justmyweather.data.WeatherLocation(latitude = latitude, longitude = longitude, label = "")

/** This build on this iPhone, for a support mail. */
fun iosAppInfo(
    version: String,
    build: Int,
    osRelease: String,
    model: String,
): io.raylytics.justmyweather.support.AppInfo =
    io.raylytics.justmyweather.support.AppInfo(
        versionName = version,
        versionCode = build,
        osName = "iOS",
        osRelease = osRelease,
        osDetail = null,
        manufacturer = "Apple",
        model = model,
    )
