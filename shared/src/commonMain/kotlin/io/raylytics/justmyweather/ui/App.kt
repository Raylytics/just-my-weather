package io.raylytics.justmyweather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.raylytics.justmyweather.support.AppInfo
import io.raylytics.justmyweather.support.SupportKind
import io.raylytics.justmyweather.support.SupportMail
import io.raylytics.justmyweather.support.bugDiagnostics
import io.raylytics.justmyweather.support.ideaContext
import io.raylytics.justmyweather.support.supportMail
import io.raylytics.justmyweather.ui.alerts.AlertsScreen
import io.raylytics.justmyweather.ui.alerts.AlertsViewModel
import io.raylytics.justmyweather.ui.customize.CustomizeScreen
import io.raylytics.justmyweather.ui.customize.CustomizeViewModel
import io.raylytics.justmyweather.ui.home.HomeScreen
import io.raylytics.justmyweather.ui.home.HomeViewModel
import io.raylytics.justmyweather.ui.home.SUN_TICK
import io.raylytics.justmyweather.ui.places.PlacesScreen
import io.raylytics.justmyweather.ui.places.PlacesViewModel
import io.raylytics.justmyweather.ui.settings.AppSettingsScreen
import io.raylytics.justmyweather.ui.settings.AppSettingsViewModel
import io.raylytics.justmyweather.ui.settings.RegionScreen
import io.raylytics.justmyweather.ui.settings.RegionViewModel
import io.raylytics.justmyweather.ui.support.SupportScreen
import io.raylytics.justmyweather.ui.theme.LocalConventions
import io.raylytics.justmyweather.view.ThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/*
 * The whole app, as both platforms run it: which screen is up, how each one
 * opens and closes, and what it is wired to. The host (MainActivity on
 * Android, the Swift app on iOS) builds the ViewModels, owns permissions and
 * the window, and passes in the few things only it can do.
 */

/** The platform half of a support mail: facts about this install, its own
 * recent log, and handing the finished mail to the email app. */
interface SupportHost {
    fun appInfo(): AppInfo

    /** Best-effort: an empty list when the platform won't say. */
    fun recentLogLines(): List<String>

    /** False when there is no email app to hand the mail to. */
    fun send(mail: SupportMail): Boolean
}

/** The screens this app has. A plain enum + state switch is all the navigation
 * a handful of destinations need — no nav library to learn or wire. */
private enum class Screen { HOME, CUSTOMIZE, APP_SETTINGS, REGION, ALERTS, PLACES, REPORT_BUG, SUBMIT_IDEA }

@Composable
fun App(
    homeViewModel: HomeViewModel,
    customizeViewModel: CustomizeViewModel,
    appSettingsViewModel: AppSettingsViewModel,
    regionViewModel: RegionViewModel,
    alertsViewModel: AlertsViewModel,
    placesViewModel: PlacesViewModel,
    themeConfig: ThemeConfig,
    onThemeChange: (ThemeConfig) -> Unit,
    onEnterAlerts: () -> Unit,
    adsRemoved: Boolean,
    /** Whether Google's consent SDK says ads may run (ads/AdsConsent). */
    canShowAds: Boolean,
    /** Whether a "Privacy choices" entry must be offered. */
    privacyOptionsRequired: Boolean,
    onPrivacyChoices: () -> Unit,
    /** Opens Play's purchase sheet; needs the Activity, so the host supplies it. */
    onBuyRemoveAds: () -> Unit,
    /** Opens the store listing; the host owns the intent. */
    onRateApp: () -> Unit,
    /** "0.3.1 (build 8)": the version line App Settings ends with. */
    version: String,
    /** The one banner, drawn by the host's ads SDK — until Remove Ads is bought. */
    adBanner: @Composable () -> Unit,
    /** The support mail's platform half: app facts, logs, and the hand-off. */
    support: SupportHost,
    /** The app state a bug report attaches, read fresh from the stores. */
    loadBugReportState: suspend () -> List<Pair<String, String>>,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    // The idea form opens from Customize and from App Settings, and returns
    // to whichever one opened it.
    var ideaFrom by rememberSaveable { mutableStateOf(Screen.CUSTOMIZE) }
    // Places opens from the glance's place name and from App Settings, and
    // goes back to whichever one opened it.
    var placesFrom by rememberSaveable { mutableStateOf(Screen.HOME) }

    // Keep "next sunrise / next sunset" actually next. The value decays with
    // the clock rather than with the data, so nothing about a fetch would
    // catch it: a glance left open — or backgrounded at 5am and reopened at
    // 9am — would otherwise name a sunrise that has already happened.
    //
    // Gated on RESUMED so it re-works the moment the user comes back and
    // costs nothing while they are elsewhere. Driven from here rather than
    // from inside HomeScreen because this is the edge that already knows
    // about lifecycle, and it leaves the screen a pure function of its state.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, homeViewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                homeViewModel.refreshSunTimes()
                delay(SUN_TICK)
            }
        }
    }

    when (screen) {
        Screen.HOME -> {
            val state by homeViewModel.state.collectAsStateWithLifecycle()
            // The glance, and under it — only here, and only until Remove
            // Ads is bought — the one banner. The glance keeps the height;
            // the banner takes the few dp AdMob picks for this width and
            // sits above the gesture inset the outer padding leaves.
            Column(Modifier.fillMaxSize()) {
                HomeScreen(
                    state = state,
                    onRefresh = homeViewModel::refresh,
                    onSetMode = homeViewModel::setForecastMode,
                    onResizeModule = homeViewModel::resizeModule,
                    onPlaces = {
                        placesFrom = Screen.HOME
                        screen = Screen.PLACES
                    },
                    onMoveModule = homeViewModel::moveModule,
                    onAppSettings = { screen = Screen.APP_SETTINGS },
                    onCustomize = { screen = Screen.CUSTOMIZE },
                    onAlerts = {
                        onEnterAlerts()
                        alertsViewModel.refreshCoverage()
                        screen = Screen.ALERTS
                    },
                    modifier = Modifier.weight(1f),
                )
                if (!adsRemoved && canShowAds) {
                    adBanner()
                }
            }
        }

        Screen.CUSTOMIZE -> {
            // System back returns to the glance rather than exiting the app.
            BackHandler { screen = Screen.HOME }
            val config by customizeViewModel.config.collectAsStateWithLifecycle()
            CustomizeScreen(
                config = config,
                onToggle = customizeViewModel::toggle,
                onRelabel = customizeViewModel::relabel,
                onResize = customizeViewModel::resize,
                onMoveUp = customizeViewModel::moveUp,
                onMoveDown = customizeViewModel::moveDown,
                onSetDensity = customizeViewModel::setDensity,
                onSetTapForDetails = customizeViewModel::setTapForDetails,
                onSetTimesIn = customizeViewModel::setTimesIn,
                onSetDefaultForecastMode = customizeViewModel::setDefaultForecastMode,
                onSetDailyStyle = customizeViewModel::setDailyStyle,
                onSetHourlyHours = customizeViewModel::setHourlyHours,
                onSetDailyDays = customizeViewModel::setDailyDays,
                onSetSunDays = customizeViewModel::setSunDays,
                onToggleForecastElement = customizeViewModel::toggleForecastElement,
                onSetForecastTileLayout = customizeViewModel::setForecastTileLayout,
                onSetAlertBannerPosition = customizeViewModel::setAlertBannerPosition,
                theme = themeConfig,
                onThemeChange = onThemeChange,
                onSubmitIdea = {
                    ideaFrom = Screen.CUSTOMIZE
                    screen = Screen.SUBMIT_IDEA
                },
                onDone = { screen = Screen.HOME },
            )
        }

        Screen.APP_SETTINGS -> {
            BackHandler { screen = Screen.HOME }
            val gadgetbridgeEnabled by appSettingsViewModel.gadgetbridgeEnabled.collectAsStateWithLifecycle()
            val settingsAdsRemoved by appSettingsViewModel.adsRemoved.collectAsStateWithLifecycle()
            val removeAdsOffer by appSettingsViewModel.removeAdsOffer.collectAsStateWithLifecycle()
            val removeAdsStatus by appSettingsViewModel.removeAdsStatus.collectAsStateWithLifecycle()
            val inForce by regionViewModel.resolved.collectAsStateWithLifecycle()
            val reading = LocalConventions.current.units
            val regionSummary =
                listOfNotNull(inForce?.region?.name, "${reading.temperature.label}, ${reading.wind.label}")
                    .joinToString(" · ")
            val savedPlaces by placesViewModel.saved.collectAsStateWithLifecycle()
            val placesSummary =
                listOfNotNull(
                    "Showing " + (savedPlaces.current?.label ?: "my location"),
                    savedPlaces.places.size.takeIf { it > 0 }?.let { "$it saved" },
                ).joinToString(" · ")
            AppSettingsScreen(
                gadgetbridgeEnabled = gadgetbridgeEnabled,
                onSetGadgetbridgeEnabled = appSettingsViewModel::setGadgetbridgeEnabled,
                adsRemoved = settingsAdsRemoved,
                canShowAds = canShowAds,
                privacyOptionsRequired = privacyOptionsRequired,
                onPrivacyChoices = onPrivacyChoices,
                placesSummary = placesSummary,
                onPlaces = {
                    placesFrom = Screen.APP_SETTINGS
                    screen = Screen.PLACES
                },
                regionSummary = regionSummary,
                onRegion = { screen = Screen.REGION },
                removeAdsPrice = removeAdsOffer?.formattedPrice,
                removeAdsStatus = removeAdsStatus,
                onBuyRemoveAds = onBuyRemoveAds,
                onRestorePurchases = appSettingsViewModel::restorePurchases,
                onReportBug = { screen = Screen.REPORT_BUG },
                onSubmitIdea = {
                    ideaFrom = Screen.APP_SETTINGS
                    screen = Screen.SUBMIT_IDEA
                },
                onRateApp = onRateApp,
                version = version,
                onDone = { screen = Screen.HOME },
            )
        }

        // Each form returns to the page it was opened from.
        Screen.REPORT_BUG -> {
            BackHandler { screen = Screen.APP_SETTINGS }
            val app = remember { support.appInfo() }
            // Built once per visit and off the main thread: the state is read
            // from the stores rather than from screen-scoped flows (which hold
            // their defaults until something subscribes — Customize's config
            // never has, on the way here), and logcat is a process spawn. The
            // form shows exactly what will be sent, so it waits for this
            // rather than showing a guess that changes under the user.
            val attached by produceState<String?>(null) {
                value =
                    withContext(Dispatchers.IO) {
                        bugDiagnostics(
                            app = app,
                            sourceScreen = "App Settings",
                            state = loadBugReportState(),
                            logs = support.recentLogLines(),
                            timestamp = Clock.System.now().toString(),
                        )
                    }
            }
            SupportScreen(
                kind = SupportKind.BUG,
                attached = attached,
                onSend = { message ->
                    val diagnostics = attached ?: return@SupportScreen false
                    support.send(supportMail(SupportKind.BUG, app, message, diagnostics))
                },
                onDone = { screen = Screen.APP_SETTINGS },
            )
        }

        Screen.SUBMIT_IDEA -> {
            BackHandler { screen = ideaFrom }
            val app = remember { support.appInfo() }
            val sourceScreen = if (ideaFrom == Screen.APP_SETTINGS) "App Settings" else "Customize"
            val attached = remember { ideaContext(app, sourceScreen = sourceScreen) }
            SupportScreen(
                kind = SupportKind.IDEA,
                attached = attached,
                onSend = { message ->
                    support.send(supportMail(SupportKind.IDEA, app, message, attached))
                },
                onDone = { screen = ideaFrom },
            )
        }

        Screen.PLACES -> {
            BackHandler {
                screen = placesFrom
                homeViewModel.refresh()
            }
            val saved by placesViewModel.saved.collectAsStateWithLifecycle()
            val results by placesViewModel.results.collectAsStateWithLifecycle()
            val loading by placesViewModel.loading.collectAsStateWithLifecycle()
            // The query is screen state, not app state: it does not outlive the
            // visit, and holding it in the ViewModel only to mirror it here
            // would be two copies of one string.
            var query by rememberSaveable { mutableStateOf("") }
            PlacesScreen(
                saved = saved,
                results = results,
                query = query,
                loading = loading,
                onQueryChange = {
                    query = it
                    placesViewModel.setQuery(it)
                },
                onSave = placesViewModel::save,
                onSaveCoordinates = placesViewModel::saveCoordinates,
                onSelect = placesViewModel::select,
                onRemove = placesViewModel::remove,
                onDone = {
                    screen = placesFrom
                    // The place decides which coordinates every fetch uses, so
                    // coming back has to re-ask rather than keep showing the
                    // last place's weather under the new place's name.
                    homeViewModel.refresh()
                },
            )
        }

        Screen.REGION -> {
            BackHandler { screen = Screen.APP_SETTINGS }
            val settings by regionViewModel.settings.collectAsStateWithLifecycle()
            val resolved by regionViewModel.resolved.collectAsStateWithLifecycle()
            val automatic by regionViewModel.automatic.collectAsStateWithLifecycle()
            RegionScreen(
                settings = settings,
                resolved = resolved,
                automatic = automatic,
                conventions = LocalConventions.current,
                onUpdate = regionViewModel::update,
                onDone = { screen = Screen.APP_SETTINGS },
            )
        }

        Screen.ALERTS -> {
            BackHandler { screen = Screen.HOME }
            val rules by alertsViewModel.rules.collectAsStateWithLifecycle()
            val alertSettings by alertsViewModel.settings.collectAsStateWithLifecycle()
            val officialAlertsHere by alertsViewModel.officialAlerts.collectAsStateWithLifecycle()
            AlertsScreen(
                rules = rules,
                settings = alertSettings,
                onAdd = alertsViewModel::add,
                onToggle = alertsViewModel::toggle,
                onDelete = alertsViewModel::delete,
                onSetQuietHours = alertsViewModel::setQuietHours,
                onSetQuietWindow = alertsViewModel::setQuietWindow,
                onSetSafetyNotifications = alertsViewModel::setSafetyNotifications,
                onSetPollCadence = alertsViewModel::setPollCadence,
                officialAlertsHere = officialAlertsHere,
                onDone = { screen = Screen.HOME },
                events = alertsViewModel.events,
            )
        }
    }
}
