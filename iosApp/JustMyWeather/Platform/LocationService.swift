import CoreLocation
import Shared

/// iOS twin of Android's LocationProvider: the most recent coarse fix the
/// system already holds, never a fresh request. When-in-use only, and the
/// accuracy the user grants — a weather grid cell needs nothing finer.
final class LocationService: NSObject, LocationSource, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    /// The status last seen, so a grant can be told from iOS re-reporting
    /// an existing one (it calls the delegate as soon as it is set). Read at
    /// construction, before any prompt, so a grant on this launch counts as
    /// new and the glance leaves its default place.
    private var lastStatus: CLAuthorizationStatus
    /// True from a fresh grant until its first fix arrives.
    private var awaitingFirstFix = false
    /// Told about that first fix, so the glance leaves its default place.
    var onFirstFix: (() -> Void)?

    /// Whether `start()` would put the permission prompt up right now.
    var wouldPrompt: Bool { manager.authorizationStatus == .notDetermined }
    private var started = false
    /// Waiting for the first launch's prompt to be answered (ComposeView
    /// holds the consent flow behind it).
    private var whenAnswered: [() -> Void] = []

    override init() {
        lastStatus = manager.authorizationStatus
        super.init()
    }

    /// Once per launch: one system prompt on a first launch, and a declined
    /// one leaves the app on its default place. A second call starts nothing,
    /// so the host may call it from both places. [then] runs once the
    /// permission question is answered: at once when it already was, or
    /// when location services are off (no prompt can come), else when the
    /// user answers the prompt. On iOS the prompt comes BEFORE the consent
    /// flow (unlike Android): the InMobi CMP's page asks WebKit for
    /// geolocation itself, which put this same system prompt over its
    /// consent form; answered first, nothing can land on the form.
    func start(then: (() -> Void)? = nil) {
        if let then {
            if manager.authorizationStatus == .notDetermined {
                whenAnswered.append(then)
                // Apple: don't ask this on the main thread (it can block).
                DispatchQueue.global(qos: .userInitiated).async {
                    guard !CLLocationManager.locationServicesEnabled() else { return }
                    DispatchQueue.main.async { self.answered() }
                }
            } else {
                DispatchQueue.main.async(execute: then)
            }
        }
        guard !started else { return }
        started = true
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyReduced
        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else if hasPermission() {
            manager.requestLocation()
        }
    }

    func hasPermission() -> Bool {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: return true
        default: return false
        }
    }

    func lastKnownLocation() -> WeatherLocation? {
        guard hasPermission(), let fix = manager.location else { return nil }
        return IosBridgesKt.locationFix(latitude: fix.coordinate.latitude, longitude: fix.coordinate.longitude)
    }

    private func answered() {
        let waiting = whenAnswered
        whenAnswered.removeAll()
        waiting.forEach { $0() }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        // iOS also calls this as soon as the delegate is set, still
        // undetermined; only a real answer releases the consent flow.
        if manager.authorizationStatus != .notDetermined { answered() }
        let wasGranted = lastStatus == .authorizedWhenInUse || lastStatus == .authorizedAlways
        lastStatus = manager.authorizationStatus
        guard hasPermission() else { return }
        // Only a NEW grant re-asks the glance: at launch HomeViewModel is
        // already loading, and a second fetch racing it could land last.
        if !wasGranted { awaitingFirstFix = true }
        manager.requestLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard awaitingFirstFix else { return }
        awaitingFirstFix = false
        onFirstFix?()
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}
}
