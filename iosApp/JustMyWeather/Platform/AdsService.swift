import AppTrackingTransparency
import GoogleMobileAds
import Shared
import UIKit
import UserMessagingPlatform

/// iOS twin of Android's AdsConsent + UmpConsentGateway + AdBanner: Google's
/// consent platform (UMP) decides whether ads may run, Apple's tracking prompt
/// (ATT) follows where UMP allows, and the Mobile Ads SDK starts only after.
/// Requests carry no personalization flag — the SDK applies the user's choices
/// (plan decision 4, as on Android since da74242 and as open-frame does).
final class AdsService: NSObject, AdsBridge {
    private var started = false
    private var gathering = false

    private var bannerUnitId: String {
        Bundle.main.object(forInfoDictionaryKey: "JMWBannerUnitID") as? String ?? ""
    }

    func gather(host: UIViewController, done: @escaping () -> Void) {
        guard !gathering else { return }
        gathering = true
        let parameters = RequestParameters()
        ConsentInformation.shared.requestConsentInfoUpdate(with: parameters) { [weak self] _ in
            ConsentForm.loadAndPresentIfRequired(from: host) { _ in
                self?.askToTrack {
                    self?.startAdsIfAllowed()
                    done()
                }
            }
        }
        // A returning user's earlier answer applies at once, before the network.
        startAdsIfAllowed()
    }

    func canRequestAds() -> Bool { ConsentInformation.shared.canRequestAds }

    func privacyOptionsRequired() -> Bool {
        ConsentInformation.shared.privacyOptionsRequirementStatus == .required
    }

    func showPrivacyOptions(host: UIViewController, done: @escaping () -> Void) {
        ConsentForm.presentPrivacyOptionsForm(from: host) { _ in done() }
    }

    func bannerHeight(widthPoints: Double) -> Double {
        Double(currentOrientationAnchoredAdaptiveBanner(width: CGFloat(widthPoints)).size.height)
    }

    func makeBanner(widthPoints: Double, host: UIViewController) -> UIView {
        let banner = BannerView(adSize: currentOrientationAnchoredAdaptiveBanner(width: CGFloat(widthPoints)))
        banner.adUnitID = bannerUnitId
        banner.rootViewController = host
        banner.load(Request())
        return banner
    }

    /// Apple's prompt, asked only once UMP allows ads at all — never on top of
    /// Google's form, and never where ads won't run.
    private func askToTrack(then: @escaping () -> Void) {
        guard ConsentInformation.shared.canRequestAds,
              ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return then() }
        ATTrackingManager.requestTrackingAuthorization { _ in DispatchQueue.main.async(execute: then) }
    }

    private func startAdsIfAllowed() {
        guard !started, ConsentInformation.shared.canRequestAds else { return }
        started = true
        MobileAds.shared.start(completionHandler: nil)
    }
}
