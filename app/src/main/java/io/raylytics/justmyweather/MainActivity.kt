package io.raylytics.justmyweather

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.raylytics.justmyweather.ads.AdBanner
import io.raylytics.justmyweather.ads.AdPolicy
import io.raylytics.justmyweather.alerts.AlertWorker
import io.raylytics.justmyweather.support.AppInfo
import io.raylytics.justmyweather.support.SupportMail
import io.raylytics.justmyweather.support.composeSupportMail
import io.raylytics.justmyweather.support.currentAppInfo
import io.raylytics.justmyweather.support.openStoreListing
import io.raylytics.justmyweather.ui.AppRoot
import io.raylytics.justmyweather.ui.AppViewModels
import io.raylytics.justmyweather.ui.SupportHost
import io.raylytics.justmyweather.ui.alerts.AlertsViewModel
import io.raylytics.justmyweather.ui.customize.CustomizeViewModel
import io.raylytics.justmyweather.ui.home.HomeViewModel
import io.raylytics.justmyweather.ui.places.PlacesViewModel
import io.raylytics.justmyweather.ui.settings.AppSettingsViewModel
import io.raylytics.justmyweather.ui.settings.RegionViewModel
import io.raylytics.justmyweather.ui.theme.ThemeViewModel
import io.raylytics.justmyweather.widget.WidgetRefreshWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as JustMyWeatherApp).container }

    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                HomeViewModel(
                    container.weatherRepository,
                    container.locationResolver,
                    container.viewConfigRepository,
                    // No-op unless the user switched the hand-off on.
                    container.gadgetbridgeExporter::export,
                    // The region refreshes from a place the user chose or a
                    // fix — never the fallback city (roborev 5424).
                    onPlaceResolved = { container.regionRepository.refresh(container.locationResolver.resolveKnown()) },
                )
            }
        }
    }

    private val customizeViewModel: CustomizeViewModel by viewModels {
        viewModelFactory {
            initializer {
                CustomizeViewModel(container.viewConfigRepository)
            }
        }
    }

    private val appSettingsViewModel: AppSettingsViewModel by viewModels {
        viewModelFactory {
            initializer { AppSettingsViewModel(container.gadgetbridgeSettingsRepository, container.removeAdsManager) }
        }
    }

    private val regionViewModel: RegionViewModel by viewModels {
        viewModelFactory {
            initializer {
                RegionViewModel(container.regionRepository) {
                    WidgetRefreshWorker.runOnce(applicationContext, afterCurrent = true)
                }
            }
        }
    }

    private val placesViewModel: PlacesViewModel by viewModels {
        viewModelFactory {
            initializer {
                PlacesViewModel(
                    container.savedPlacesRepository,
                    loadCatalog = container.placeSource::load,
                )
            }
        }
    }

    private val themeViewModel: ThemeViewModel by viewModels {
        viewModelFactory {
            initializer { ThemeViewModel(container.themeConfigRepository) }
        }
    }

    private val alertsViewModel: AlertsViewModel by viewModels {
        viewModelFactory {
            initializer {
                AlertsViewModel(
                    container.alertRulesRepository,
                    container.alertSettingsRepository,
                    // Pass-through: the ViewModel decides whether the worker
                    // should run (rules OR safety alerts) and at what cadence,
                    // so the predicate is testable instead of buried here.
                    onWorkChanged = { hasWork, minutes ->
                        AlertWorker.sync(applicationContext, hasWork, minutes)
                    },
                    onRuleActivated = { AlertWorker.runOnce(applicationContext) },
                    officialAlertsHere = {
                        container.weatherRepository.officialAlertsAvailable(container.locationResolver.resolve())
                    },
                )
            }
        }
    }

    // Coarse location is optional: granting it re-fetches for the real place,
    // declining leaves the default location in place. Either way the app works.
    private val requestLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) homeViewModel.refresh()
        }

    // Notification permission is requested when the user opens Alerts — that's
    // the moment they're opting in. Declining is fine; rules just won't post
    // until it's granted in system settings.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ forces edge-to-edge for targetSdk 35; opting in explicitly
        // makes every OS version render the same way, so one inset strategy
        // (the safeDrawingPadding below) covers them all.
        enableEdgeToEdge()

        // Once per launch: Google's consent SDK says whether ads may run here,
        // asking the user first where the law requires it (ads/AdsConsent).
        // The location prompt waits until that has settled, so a first launch
        // in Europe shows one dialog at a time rather than the permission
        // sheet stacked on top of the consent form (seen on the emulator).
        // An owner of Remove Ads never meets the consent flow: there are no
        // ads to consent to, so no request to Google and no form (roborev 5436).
        val askLocation = {
            val alive = !isFinishing && !isDestroyed
            if (alive && !container.locationProvider.hasPermission()) {
                requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        lifecycleScope.launch {
            val owner = runCatching { container.adsEntitlementRepository.adsRemoved.first() }.getOrDefault(false)
            if (owner) askLocation() else container.adsConsent.gather(this@MainActivity) { askLocation() }
        }

        setContent {
            AppRoot(
                graph = container,
                viewModels = viewModels,
                canShowAds = container.adsConsent.canShowAds,
                privacyOptionsRequired = container.adsConsent.privacyOptionsRequired,
                onEnterAlerts = ::requestNotificationsIfNeeded,
                onPrivacyChoices = { container.adsConsent.showPrivacyOptions(this@MainActivity) },
                onBuyRemoveAds = { appSettingsViewModel.buyRemoveAds(this@MainActivity) },
                onRateApp = { openStoreListing() },
                version = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                adBanner = { AdBanner(unitId = bannerUnitId) },
                support = supportHost,
                // The bars sit on the app-painted background, and the user can
                // force a mood against the system setting — so bar icon contrast
                // must follow the app's resolved mood, not the system default
                // that the argless enableEdgeToEdge() above assumes.
                onDarkChanged = { dark ->
                    val bars =
                        if (dark) {
                            SystemBarStyle.dark(Color.TRANSPARENT)
                        } else {
                            // The darkScrim only shows on API 24/25, which can't
                            // render dark nav-bar icons: a translucent dark bar
                            // keeps the white buttons visible over a light theme.
                            SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x80, 0x1B, 0x1B, 0x1B))
                        }
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                },
            )
        }
    }

    private val viewModels by lazy {
        AppViewModels(
            home = homeViewModel,
            customize = customizeViewModel,
            appSettings = appSettingsViewModel,
            region = regionViewModel,
            alerts = alertsViewModel,
            places = placesViewModel,
            theme = themeViewModel,
        )
    }

    private val bannerUnitId: String
        get() = AdPolicy.bannerUnitId(BuildConfig.DEBUG, BuildConfig.ADMOB_BANNER_UNIT_ID)

    private val supportHost =
        object : SupportHost {
            override fun appInfo(): AppInfo = currentAppInfo()

            override fun recentLogLines(): List<String> = io.raylytics.justmyweather.support.recentLogLines()

            override fun send(mail: SupportMail): Boolean = composeSupportMail(mail)
        }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
