package io.raylytics.justmyweather

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.raylytics.justmyweather.alerts.AlertCheck
import io.raylytics.justmyweather.alerts.AlertScheduling
import io.raylytics.justmyweather.alerts.BackgroundAlerts
import io.raylytics.justmyweather.billing.BillingEvent
import io.raylytics.justmyweather.billing.BillingGateway
import io.raylytics.justmyweather.billing.ProductOffer
import io.raylytics.justmyweather.billing.PurchaseHost
import io.raylytics.justmyweather.billing.PurchaseRecord
import io.raylytics.justmyweather.data.nws.UrlSessionTransport
import io.raylytics.justmyweather.data.places.BundlePlaceSource
import io.raylytics.justmyweather.region.IosRegionSignals
import io.raylytics.justmyweather.ui.AppRoot
import io.raylytics.justmyweather.ui.AppViewModels
import io.raylytics.justmyweather.ui.alerts.AlertsViewModel
import io.raylytics.justmyweather.ui.customize.CustomizeViewModel
import io.raylytics.justmyweather.ui.home.HomeViewModel
import io.raylytics.justmyweather.ui.places.PlacesViewModel
import io.raylytics.justmyweather.ui.settings.AppSettingsViewModel
import io.raylytics.justmyweather.ui.settings.RegionViewModel
import io.raylytics.justmyweather.ui.theme.ThemeViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController
import kotlin.coroutines.resume

/**
 * The iOS app's half of the shared code: what MainActivity and AppContainer
 * are on Android. The Swift host creates one at launch with its platform
 * twins ([IosPlatform]); this builds the same [AppGraph] and ViewModels and
 * serves the Compose UI and the background alert check.
 */
class IosApp(private val platform: IosPlatform) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val graph: AppGraph =
        AppGraph(
            dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { settingsFile().toPath() }),
            nwsTransport = UrlSessionTransport.uncached(),
            metNoTransport = UrlSessionTransport.cached(),
            loadPlaces = BundlePlaceSource::load,
            locationSource = platform.location,
            regionSignals = {
                    lookup ->
                IosRegionSignals(platform.store::storefrontCountryCode, platform.location, lookup)
            },
            billingGateway = StoreKitGateway(platform.store),
            alertNotifications = platform.notifications,
            scope = scope,
        )

    private val canShowAds = MutableStateFlow(false)
    private val privacyOptionsRequired = MutableStateFlow(false)
    private val backgroundAlerts = MutableStateFlow(BackgroundAlerts.BEST_EFFORT)

    // Built once for the app's life: iOS has no configuration changes to
    // survive, which is what Android's ViewModelStore is for.
    private val viewModels =
        AppViewModels(
            home =
                HomeViewModel(
                    graph.weatherRepository,
                    graph.locationResolver,
                    graph.viewConfigRepository,
                    // Gadgetbridge is Android-only (docs/PARITY.md, "Dropped").
                    {},
                    onPlaceResolved = { graph.regionRepository.refresh(graph.locationResolver.resolveKnown()) },
                ),
            customize = CustomizeViewModel(graph.viewConfigRepository),
            appSettings = AppSettingsViewModel(graph.gadgetbridgeSettingsRepository, graph.removeAdsManager),
            region = RegionViewModel(graph.regionRepository) {},
            alerts =
                AlertsViewModel(
                    graph.alertRulesRepository,
                    graph.alertSettingsRepository,
                    onWorkChanged = { hasWork, minutes -> platform.scheduleAlertRefresh(hasWork, minutes) },
                    // A rule switched on is checked at once, as Android's
                    // one-off worker does, rather than at the next refresh.
                    onRuleActivated = { scope.launch { graph.alertCheck.run() } },
                    officialAlertsHere = {
                        graph.weatherRepository.officialAlertsAvailable(graph.locationResolver.resolve())
                    },
                ),
            places = PlacesViewModel(graph.savedPlacesRepository, loadCatalog = BundlePlaceSource::load),
            theme = ThemeViewModel(graph.themeConfigRepository),
        )

    /** At launch: Remove Ads syncs with the store, the alert schedule is
     * re-applied, and — unless ads are already removed — consent is asked. */
    fun start() {
        scope.launch { graph.removeAdsManager.start() }
        scope.launch {
            val rules = graph.alertRulesRepository.rules.first()
            val settings = graph.alertSettingsRepository.current()
            val hasWork = AlertScheduling.hasWork(rules, settings)
            platform.scheduleAlertRefresh(hasWork, settings.pollMinutes)
            if (hasWork) graph.alertCheck.run()
        }
        refreshBackgroundAlerts()
    }

    /** The location twin has its first fix after the user granted access:
     * the glance re-asks, as Android's permission callback does. */
    fun locationGranted() {
        viewModels.home.refresh()
    }

    /** Called by the host when the app comes to the foreground. */
    fun refreshBackgroundAlerts() {
        backgroundAlerts.value =
            if (platform.backgroundRefreshAvailable()) BackgroundAlerts.BEST_EFFORT else BackgroundAlerts.REFRESH_OFF
    }

    /** Asks the consent SDKs once the first screen is up (they present over it). */
    fun gatherConsent(host: UIViewController) {
        scope.launch {
            if (graph.adsEntitlementRepository.adsRemoved.first()) return@launch
            platform.ads.gather(host) { updateAdsState() }
        }
    }

    private fun updateAdsState() {
        canShowAds.value = platform.ads.canRequestAds()
        privacyOptionsRequired.value = platform.ads.privacyOptionsRequired()
    }

    /** One alert check, for the BGAppRefreshTask. [completion] says whether it
     * finished (false: a transient failure worth retrying sooner). */
    fun runAlertCheck(completion: (Boolean) -> Unit) {
        scope.launch { completion(graph.alertCheck.run() == AlertCheck.Outcome.DONE) }
    }

    /** The whole UI, for the host's window. */
    fun mainViewController(): UIViewController =
        ComposeUIViewController {
            AppRoot(
                graph = graph,
                viewModels = viewModels,
                canShowAds = canShowAds,
                privacyOptionsRequired = privacyOptionsRequired,
                onEnterAlerts = platform::requestNotificationPermission,
                onPrivacyChoices = {
                    platform.presenter()?.let { platform.ads.showPrivacyOptions(it) { updateAdsState() } }
                },
                onBuyRemoveAds = { platform.presenter()?.let(viewModels.appSettings::buyRemoveAds) },
                onRateApp = platform::openStoreListing,
                version = platform.versionLabel,
                adBanner = { Banner(platform) },
                support = platform.support,
                backgroundAlerts = backgroundAlerts,
            )
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun settingsFile(): String {
        val documents: NSURL? =
            NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)
        return requireNotNull(documents?.path) { "no Documents directory" } + "/settings.preferences_pb"
    }
}

/** The one banner, drawn by the Google Mobile Ads SDK at the glance's foot. */
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun Banner(platform: IosPlatform) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth.value.toDouble()
        val height = remember(width) { platform.ads.bannerHeight(width) }
        val host = platform.presenter() ?: return@BoxWithConstraints
        UIKitView(
            factory = { platform.ads.makeBanner(width, host) },
            modifier = Modifier.fillMaxWidth().height(height.dp),
        )
    }
}

/**
 * [BillingGateway] over StoreKit 2. StoreKit has no acknowledge step and no
 * purchase tokens: a verified transaction IS the entitlement, so every owned
 * product reads as purchased and acknowledged, keyed by its product id.
 */
private class StoreKitGateway(private val store: StoreBridge) : BillingGateway {
    private val outcomes = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4)

    init {
        // Changes that happen outside the sheet. A late approval is a
        // purchase like any other; a revocation asks the manager to re-read
        // what the account owns (the AlreadyOwned path is that resync).
        store.onTransactionUpdate { productId, revoked ->
            outcomes.tryEmit(
                if (revoked) {
                    BillingEvent.AlreadyOwned
                } else {
                    BillingEvent.Purchases(listOf(PurchaseRecord(listOf(productId), true, true, productId)))
                },
            )
        }
    }

    override val events: Flow<BillingEvent> = outcomes

    override suspend fun queryOffer(productId: String): ProductOffer? =
        suspendCancellableCoroutine { c -> store.offer(productId) { c.resume(it) } }

    override suspend fun ownedPurchases(): List<PurchaseRecord>? =
        suspendCancellableCoroutine { c ->
            store.ownedProducts { owned ->
                c.resume(owned?.map { PurchaseRecord(listOf(it), purchased = true, acknowledged = true, token = it) })
            }
        }

    override suspend fun acknowledge(token: String): Boolean = true

    override fun launchPurchase(activity: PurchaseHost, productId: String) {
        store.purchase(productId) { result ->
            val event =
                when (result) {
                    StorePurchase.PURCHASED ->
                        BillingEvent.Purchases(listOf(PurchaseRecord(listOf(productId), true, true, productId)))
                    StorePurchase.PENDING ->
                        BillingEvent.Purchases(listOf(PurchaseRecord(listOf(productId), false, false, productId)))
                    StorePurchase.CANCELLED -> BillingEvent.Cancelled
                    StorePurchase.FAILED -> BillingEvent.Failed("The App Store couldn't complete the purchase.")
                }
            outcomes.tryEmit(event)
        }
    }
}
