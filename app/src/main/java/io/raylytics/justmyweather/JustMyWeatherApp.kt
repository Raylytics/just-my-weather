package io.raylytics.justmyweather

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.ads.MobileAds
import io.raylytics.justmyweather.ads.AdsConsent
import io.raylytics.justmyweather.ads.UmpConsentGateway
import io.raylytics.justmyweather.alerts.AlertNotifier
import io.raylytics.justmyweather.alerts.AlertScheduling
import io.raylytics.justmyweather.alerts.AlertWorker
import io.raylytics.justmyweather.billing.PlayBillingGateway
import io.raylytics.justmyweather.data.WidgetConfigRepository
import io.raylytics.justmyweather.data.WidgetDataStore
import io.raylytics.justmyweather.data.gadgetbridge.GadgetbridgeBroadcaster
import io.raylytics.justmyweather.data.gadgetbridge.GadgetbridgeExporter
import io.raylytics.justmyweather.data.nws.OkHttpTransport
import io.raylytics.justmyweather.data.places.AssetPlaceSource
import io.raylytics.justmyweather.location.LocationProvider
import io.raylytics.justmyweather.region.AndroidRegionSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cache
import java.io.File

/** App-wide DataStore for user settings (view config, alert rules). */
private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * The Android app's dependencies: the shared [AppGraph], built from Android's
 * pieces (OkHttp, the settings DataStore, the bundled gazetteer asset,
 * LocationManager, the telephony/locale region signals, Play Billing, the
 * notification channels), plus what only Android has — home screen widgets,
 * the Gadgetbridge hand-off and Google's consent SDK.
 */
class AppContainer private constructor(
    private val appContext: Context,
    scope: CoroutineScope,
    /** The gazetteer is opened only when the places screen asks; nothing here
     * holds its rows for the life of the process. */
    val placeSource: AssetPlaceSource,
    val locationProvider: LocationProvider,
    val alertNotifier: AlertNotifier,
) : AppGraph(
        dataStore = appContext.dataStore,
        nwsTransport = OkHttpTransport(),
        // Its OWN OkHttp client, with a small disk cache: MET's terms ask that
        // a client honour Expires and revalidate with If-Modified-Since, which
        // OkHttp does by itself once it has somewhere to keep the response.
        metNoTransport =
            OkHttpTransport(
                OkHttpTransport.defaultClient
                    .newBuilder()
                    .cache(Cache(File(appContext.cacheDir, "metno-http"), MET_NO_CACHE_BYTES))
                    .build(),
            ),
        loadPlaces = placeSource::load,
        locationSource = locationProvider,
        regionSignals = { lookup -> AndroidRegionSignals(appContext, locationProvider, lookup) },
        billingGateway = PlayBillingGateway(appContext),
        alertNotifications = alertNotifier,
        scope = scope,
    ) {
    constructor(context: Context, scope: CoroutineScope) : this(
        context.applicationContext,
        scope,
        AssetPlaceSource(context.applicationContext),
        LocationProvider(context.applicationContext),
        AlertNotifier(context.applicationContext),
    )

    // Home screen widgets: each one's settings, and the one fetched dataset
    // they all draw from. Both are read by the launcher-driven draw and the
    // refresh worker, which is why they live here and not in an Activity.
    val widgetConfigRepository = WidgetConfigRepository(appContext.dataStore)
    val widgetDataStore = WidgetDataStore(appContext.dataStore)

    // Optional hand-off of each reading to Gadgetbridge, which relays it to a
    // paired watch. Constructed unconditionally but inert until switched on:
    // the exporter reads the setting before building anything.
    val gadgetbridgeExporter =
        GadgetbridgeExporter(
            settings = gadgetbridgeSettingsRepository,
            broadcaster = GadgetbridgeBroadcaster(appContext),
        )

    // Whether ads may run at all is Google's consent SDK's call (UMP): it asks
    // the user where the law requires it and says "not required" elsewhere.
    // The ads SDK starts once, only after that — and never for an owner of
    // Remove Ads, who has nothing to show.
    val adsConsent =
        AdsConsent(
            UmpConsentGateway(appContext, BuildConfig.UMP_DEBUG_GEOGRAPHY, BuildConfig.DEBUG),
        ) {
            scope.launch {
                if (!adsEntitlementRepository.adsRemoved.first()) MobileAds.initialize(appContext) {}
            }
        }
}

class JustMyWeatherApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        container.alertNotifier.ensureChannel()
        // Schedule the hourly check only when rules are live, so a quiet install
        // does no background work; a launch check gives any standing rule timely
        // feedback. Reading the rule list is suspending, hence the scope.
        appScope.launch {
            val rules = container.alertRulesRepository.rules.first()
            val settings = container.alertSettingsRepository.current()
            // Same predicate the ViewModel uses, not a second copy of the `||`.
            val hasWork = AlertScheduling.hasWork(rules, settings)
            AlertWorker.sync(this@JustMyWeatherApp, hasWork, settings.pollMinutes)
            if (hasWork) AlertWorker.runOnce(this@JustMyWeatherApp)
        }
        appScope.launch {
            // Play is asked what the account owns, which is what turns a
            // reinstall back into an owner. The ads SDK is NOT started here:
            // Google's consent SDK decides when it may be (AdsConsent, from
            // MainActivity), so it never starts before consent where the law
            // asks for it.
            container.removeAdsManager.start()
        }
    }
}

/** One megabyte: a MET response is ~40 KB and a user watches a handful of places. */
private const val MET_NO_CACHE_BYTES = 1L shl 20
