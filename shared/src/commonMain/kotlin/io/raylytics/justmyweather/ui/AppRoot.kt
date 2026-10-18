package io.raylytics.justmyweather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.raylytics.justmyweather.AppGraph
import io.raylytics.justmyweather.Platform
import io.raylytics.justmyweather.alerts.BackgroundAlerts
import io.raylytics.justmyweather.currentPlatform
import io.raylytics.justmyweather.ui.alerts.AlertsViewModel
import io.raylytics.justmyweather.ui.customize.CustomizeViewModel
import io.raylytics.justmyweather.ui.home.HomeUiState
import io.raylytics.justmyweather.ui.home.HomeViewModel
import io.raylytics.justmyweather.ui.home.describe
import io.raylytics.justmyweather.ui.places.PlacesViewModel
import io.raylytics.justmyweather.ui.settings.AppSettingsViewModel
import io.raylytics.justmyweather.ui.settings.RegionViewModel
import io.raylytics.justmyweather.ui.theme.JustMyWeatherTheme
import io.raylytics.justmyweather.ui.theme.LocalConventions
import io.raylytics.justmyweather.ui.theme.ThemeViewModel
import io.raylytics.justmyweather.ui.theme.themeResolvesToDark
import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.whatsnew.ReleaseNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Every screen's ViewModel. Each host builds them its own way — Android
 * through the Activity's ViewModelStore, iOS once for the app's life. */
class AppViewModels(
    val home: HomeViewModel,
    val customize: CustomizeViewModel,
    val appSettings: AppSettingsViewModel,
    val region: RegionViewModel,
    val alerts: AlertsViewModel,
    val places: PlacesViewModel,
    val theme: ThemeViewModel,
)

/**
 * The window both apps draw: the user's theme, the app-painted background
 * edge to edge, content kept clear of system bars and cutouts, the region's
 * conventions, and [App] inside. The host passes what only it knows — whether
 * ads may run (each platform's consent SDK), and the platform actions.
 */
@Composable
fun AppRoot(
    graph: AppGraph,
    viewModels: AppViewModels,
    /** Whether the consent SDK says ads may run; false until it has answered. */
    canShowAds: StateFlow<Boolean>,
    /** Whether a "Privacy choices" entry must be offered. */
    privacyOptionsRequired: StateFlow<Boolean>,
    onEnterAlerts: () -> Unit,
    onPrivacyChoices: () -> Unit,
    onBuyRemoveAds: () -> Unit,
    onRateApp: () -> Unit,
    version: String,
    adBanner: @Composable () -> Unit,
    support: SupportHost,
    /** The app's resolved mood, for a host whose system bars must follow it. */
    onDarkChanged: (Boolean) -> Unit = {},
    /** How dependably background alert checks run (iOS can't promise). */
    backgroundAlerts: StateFlow<BackgroundAlerts> = AlwaysReliable,
) {
    val alertsReliability by backgroundAlerts.collectAsStateWithLifecycle()
    val themeConfig by viewModels.theme.config.collectAsStateWithLifecycle()
    // Starts true: the banner is withheld until the store's answer is read,
    // so an owner never sees it flash in.
    val adsRemoved by graph.adsEntitlementRepository.adsRemoved.collectAsStateWithLifecycle(true)
    // The region decides how everything reads.
    val conventions by graph.regionRepository.conventions.collectAsStateWithLifecycle(Conventions.US)
    val showAds by canShowAds.collectAsStateWithLifecycle()
    val privacyOptions by privacyOptionsRequired.collectAsStateWithLifecycle()
    val dark = themeResolvesToDark(themeConfig)
    LaunchedEffect(dark) { onDarkChanged(dark) }
    // What's new: decided once per launch, before this launch's own writes
    // can make a fresh install look like an update (WhatsNewRepository).
    var whatsNew by remember { mutableStateOf<List<ReleaseNote>>(emptyList()) }
    LaunchedEffect(version) { whatsNew = graph.whatsNewRepository.pending(version) }
    val scope = rememberCoroutineScope()
    JustMyWeatherTheme(themeConfig) {
        // The background paints the full edge-to-edge window (so the areas
        // behind the system bars match the app), then the padding keeps every
        // screen's content clear of bars and cutouts. Test tags are surfaced
        // for the UI tests (Maestro), which target controls with no text.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .exposeTestTags(),
        ) {
            CompositionLocalProvider(LocalConventions provides conventions) {
                App(
                    homeViewModel = viewModels.home,
                    customizeViewModel = viewModels.customize,
                    appSettingsViewModel = viewModels.appSettings,
                    regionViewModel = viewModels.region,
                    alertsViewModel = viewModels.alerts,
                    placesViewModel = viewModels.places,
                    themeConfig = themeConfig,
                    onThemeChange = viewModels.theme::save,
                    onEnterAlerts = onEnterAlerts,
                    loadBugReportState = { bugReportState(graph, viewModels.home) },
                    adsRemoved = adsRemoved,
                    canShowAds = showAds,
                    privacyOptionsRequired = privacyOptions,
                    onPrivacyChoices = onPrivacyChoices,
                    onBuyRemoveAds = onBuyRemoveAds,
                    onRateApp = onRateApp,
                    version = version,
                    adBanner = adBanner,
                    support = support,
                    backgroundAlerts = alertsReliability,
                    whatsNew = whatsNew,
                    onWhatsNewSeen = {
                        whatsNew = emptyList()
                        scope.launch { graph.whatsNewRepository.markSeen(version) }
                    },
                )
            }
        }
    }
}

/** How long a bug report waits for the glance to report its state. */
private const val GLANCE_WAIT_MS = 2_000L

/**
 * What a bug report says about the app, read from the persisted stores and
 * not from any screen's StateFlow: those are shared WhileSubscribed and
 * report their defaults to a reader that arrives before a subscriber.
 * The glance state is the exception — it lives only in the ViewModel.
 * Its StateFlow starts at Loading, and first() on a StateFlow returns the
 * current value at once, so the wait is for a SETTLED state: first {}
 * subscribes, which starts the upstream. A glance still loading when the
 * wait ends is reported as exactly that — a stuck fetch is the kind of
 * thing a bug report is for — rather than blocking the report.
 */
internal suspend fun bugReportState(graph: AppGraph, home: HomeViewModel): List<Pair<String, String>> {
    val config = graph.viewConfigRepository.config.first()
    val glance =
        withTimeoutOrNull(GLANCE_WAIT_MS) {
            home.state.first { it !is HomeUiState.Loading }
        }
    return listOfNotNull(
        "Glance" to (glance?.describe() ?: "Loading (still, after ${GLANCE_WAIT_MS}ms)"),
        "Location permission" to graph.locationSource.hasPermission().toString(),
        "Visible modules" to config.visible.size.toString(),
        "Density" to config.density.name,
        "Theme" to graph.themeConfigRepository.config.first().toString(),
        // An Android-only hand-off; on iPhone the line would only ever say false.
        if (currentPlatform == Platform.ANDROID) {
            "Gadgetbridge" to graph.gadgetbridgeSettingsRepository.enabled.first().toString()
        } else {
            null
        },
    )
}

/** Android's answer, made once: a default built in the parameter list would be
 * a new flow on every recomposition, restarting its collector each time. */
private val AlwaysReliable: StateFlow<BackgroundAlerts> = MutableStateFlow(BackgroundAlerts.RELIABLE)
