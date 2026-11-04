import CoreLocation
import Shared

/// iOS twin of Android's LocationProvider: the most recent coarse fix the
/// system already holds, never a fresh request. When-in-use only, and the
/// accuracy the user grants — a weather grid cell needs nothing finer.
final class LocationService: NSObject, LocationSource, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    /// The status last seen, so a grant can be told from iOS re-reporting
    /// an existing one (it calls the delegate as soon as it is set). Read at
    /// construction, before any prompt: a grant made between then and
    /// `start()` (the CMP's own geolocation request on a first launch) must
    /// count as new, so the glance leaves its default place on that launch.
    private var lastStatus: CLAuthorizationStatus
    /// True from a fresh grant until its first fix arrives.
    private var awaitingFirstFix = false
    /// Told about that first fix, so the glance leaves its default place.
    var onFirstFix: (() -> Void)?

    /// Whether `start()` would put the permission prompt up right now.
    var wouldPrompt: Bool { manager.authorizationStatus == .notDetermined }
    private var started = false

    override init() {
        lastStatus = manager.authorizationStatus
        super.init()
    }

    /// Once per launch, after the consent flow on a first launch (as Android
    /// asks after consent) and straight away otherwise: one system prompt,
    /// and a declined one leaves the app on its default place. A second
    /// call is ignored, so the host may call it from both places.
    func start() {
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

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
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
