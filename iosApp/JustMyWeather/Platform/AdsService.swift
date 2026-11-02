import AppTrackingTransparency
import Shared
import UIKit

/// iOS twin of Android's AdsConsent + ConsentGateway + AdBanner: the consent
/// platform decides whether ads may run, Apple's tracking prompt (ATT)
/// follows where it allows, and the ad SDK starts only after.
///
/// google-ads-exit Stage A: Google Mobile Ads and UMP are gone and AppLovin
/// MAX with a third-party CMP has not landed yet (Stage B). Until then no
/// consent is gathered, ads are never allowed, the banner has no height and
/// the ATT prompt is never reached (it only ever follows a consent that
/// allows ads). The bridge keeps its shape so the shared code is unchanged.
final class AdsService: NSObject, AdsBridge {
    private var gathering = false

    func gather(host: UIViewController, done: @escaping () -> Void) {
        guard !gathering else { return }
        gathering = true
        // Stage B: the CMP's request and form run here, then askToTrack, then
        // the MAX SDK starts — in that order, never before the purchase answer.
        askToTrack { done() }
    }

    func canRequestAds() -> Bool { false }

    func privacyOptionsRequired() -> Bool { false }

    func showPrivacyOptions(host: UIViewController, done: @escaping () -> Void) { done() }

    func bannerHeight(widthPoints: Double) -> Double { 0 }

    func makeBanner(widthPoints: Double, host: UIViewController) -> UIView { UIView() }

    /// Apple's prompt, asked only once the consent platform allows ads at all
    /// — never on top of its form, and never where ads won't run.
    private func askToTrack(then: @escaping () -> Void) {
        guard canRequestAds(),
              ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return then() }
        ATTrackingManager.requestTrackingAuthorization { _ in DispatchQueue.main.async(execute: then) }
    }
}
