import Shared
import StoreKit
import UIKit
import UserNotifications

/// Everything the shared code asks of iOS, in one object. Each service is its
/// own file in this folder, named after the Android twin it mirrors.
final class IosPlatformImpl: NSObject, IosPlatform {
    // Concrete twins, for the host to start; the shared code sees only the
    // contracts below.
    let locationService = LocationService()
    let storeService = StoreService()

    var location: LocationSource { locationService }
    var store: StoreBridge { storeService }
    let notifications: AlertNotifications = Notifier()
    let support: SupportHost = SupportService()
    let ads: AdsBridge = AdsService()

    var versionLabel: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (build \(build))"
    }

    func requestNotificationPermission() {
        // Asked when the user opens Alerts — the moment they opt in. Time
        // Sensitive needs no extra prompt; quiet hours use .passive.
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    /// The listing's review page. requestReview is quota-limited and silent
    /// in TestFlight, so it can't answer a tap; it is only the fallback while
    /// the App Store id isn't set yet (Info.plist JMWAppStoreID, from the
    /// App Store Connect record).
    func openStoreListing() {
        let id = Bundle.main.object(forInfoDictionaryKey: "JMWAppStoreID") as? String ?? ""
        if !id.isEmpty, let url = URL(string: "https://apps.apple.com/app/id\(id)?action=write-review") {
            UIApplication.shared.open(url)
        } else if let scene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene {
            SKStoreReviewController.requestReview(in: scene)
        }
    }

    func scheduleAlertRefresh(enabled: Bool, everyMinutes: Int32) {
        AlertRefresh.schedule(enabled: enabled, everyMinutes: Int(everyMinutes))
    }

    func backgroundRefreshAvailable() -> Bool {
        UIApplication.shared.backgroundRefreshStatus == .available
    }

    func presenter() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
