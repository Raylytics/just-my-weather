import BackgroundTasks
import Shared
import SwiftUI
import UserNotifications

/// The iOS host. The whole UI and every decision are the shared Kotlin code
/// (`IosApp`); this file starts it, gives it the platform twins in
/// `Platform/`, and owns what only an iOS app owns: the scene, background
/// refresh registration, and how notifications present in the foreground.
@main
struct JustMyWeatherApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ComposeView()
                // The shared UI pads itself clear of the notch and the home
                // indicator (safeDrawingPadding), and paints behind them.
                .ignoresSafeArea()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { AppDelegate.app.refreshBackgroundAlerts() }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    /// Created once, before any scene, so background launches have it too.
    static let platform = IosPlatformImpl()
    static let app = IosApp(platform: platform)

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Must be registered before launch finishes (BGTaskScheduler's rule).
        AlertRefresh.register(app: Self.app)
        UNUserNotificationCenter.current().delegate = self
        Self.platform.storeService.start()
        Self.platform.locationService.onFirstFix = { Self.app.locationGranted() }
        // A first launch's location prompt is asked from ComposeView, once
        // the first screen is up, ahead of the consent flow. Once answered,
        // location starts here, background launches included (the alert
        // check wants a fix and never shows a scene).
        if !Self.platform.locationService.wouldPrompt { Self.platform.locationService.start() }
        Self.app.start()
        return true
    }

    /// An alert that fires while the app is open still shows, as on Android.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }
}

/// The shared Compose UI as a SwiftUI view.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = AppDelegate.app.mainViewController()
        // Once the first screen is up: the location prompt first, then the
        // consent form, then Apple's tracking prompt (AdsService). The
        // reverse of Android, on purpose: the InMobi CMP's page asks WebKit
        // for geolocation itself, so a location question still open put the
        // system prompt over its consent form (seen 2026-11-15 over an EEA
        // VPN). Answered first, nothing lands on the form. Off a first
        // launch the question is already answered and consent starts at once.
        DispatchQueue.main.async {
            AppDelegate.platform.locationService.start {
                AppDelegate.app.gatherConsent(host: controller) {}
            }
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
