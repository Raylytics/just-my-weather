import CoreLocation
import Shared

/// iOS twin of Android's LocationProvider: the most recent coarse fix the
/// system already holds, never a fresh request. When-in-use only, and the
/// accuracy the user grants — a weather grid cell needs nothing finer.
final class LocationService: NSObject, LocationSource, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    /// True from a fresh grant until its first fix arrives.
    private var awaitingFirstFix = false
    /// Told about that first fix, so the glance leaves its default place.
    var onFirstFix: (() -> Void)?

    /// At launch, as Android asks after consent: one system prompt, and a
    /// declined one leaves the app on its default place.
    func start() {
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
        guard hasPermission() else { return }
        awaitingFirstFix = true
        manager.requestLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard awaitingFirstFix else { return }
        awaitingFirstFix = false
        onFirstFix?()
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}
}
