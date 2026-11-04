import AppTrackingTransparency
import Appodeal
import InMobiCMP
import Shared
import UIKit

/// iOS twin of Android's AdsConsent + ConsentGateway + AdBanner: the consent
/// platform decides whether ads may run, Apple's tracking prompt (ATT)
/// follows where it allows, and the ad SDK starts only after.
///
/// google-ads-exit Stage B: the InMobi CMP is the consent platform and
/// Appodeal mediation (on Appodeal's own network accounts) serves the
/// banner. The order on a launch that reaches here (IosApp only calls
/// `gather` once the store has said the user does not own Remove Ads):
///
///   1. the CMP loads, and shows its form where the law asks (EEA, UK,
///      Switzerland; the US opt-out notice under state law) — `gather` waits
///      for it to settle: form dismissed, no form needed, an error, or a
///      timeout for a launch with no network;
///   2. if the CMP's stored answer allows ads, Apple's ATT prompt
///      (`shouldDisplayIDFA: false` keeps the CMP from showing it itself,
///      so it is never stacked on the form);
///   3. only then Appodeal initializes, banner type only, and every banner
///      loads from its init callback, never before.
///
/// Consent reaches the networks through storage: the CMP writes the IAB TCF
/// and GPP strings to UserDefaults and Appodeal's SDK and its networks read
/// them there. Nothing here calls Appodeal's own consent manager (it needs
/// an AdMob account this project does not use) and nothing sets
/// `setNonPersonalized`, so the CMP governs.
///
/// Blank ids (a clone without Config/Ads.xcconfig) mean no CMP, no ATT, no
/// SDK and no banner: the app runs ad-free, and the release scripts refuse
/// to cut it that way (scripts/ios/common.sh, require_ads_configured).
final class AdsService: NSObject, AdsBridge {
    private let appKey: String
    private let pcode: String

    private enum Phase { case idle, inFlight, settled }
    private var phase: Phase = .idle
    private var waiting: [() -> Void] = []
    private var timeout: DispatchWorkItem?

    private var sdkStarted = false
    private var sdkInitialized = false
    /// Banners handed to Compose before the SDK finished initializing; they
    /// load from the init callback.
    private let banners = NSHashTable<APDBannerView>.weakObjects()
    /// The follow-up for a privacy-options form the user opened from Settings.
    private var privacyOptionsDone: (() -> Void)?

    override init() {
        let info = Bundle.main.infoDictionary ?? [:]
        appKey = Self.id(info["JMWAppodealAppKey"])
        pcode = Self.id(info["JMWInMobiPcode"])
        super.init()
    }

    /// An id from Info.plist (filled in from Config/Ads.xcconfig), or empty:
    /// a missing xcconfig leaves the `$(...)` reference unexpanded, and the
    /// committed example carries placeholders; neither is an id.
    private static func id(_ value: Any?) -> String {
        let s = (value as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if s.isEmpty || s.hasPrefix("$(") || s.hasPrefix("REPLACE_WITH") { return "" }
        return s
    }

    private var configured: Bool { !appKey.isEmpty && pcode.hasPrefix("p-") }

    // MARK: AdsBridge

    /// Called from the shared code's background scope; everything here is
    /// UIKit and the CMP's web view, and the request state is read by the
    /// CMP's callbacks on the main queue, so the whole thing hops there.
    func gather(host: UIViewController, done: @escaping () -> Void) {
        DispatchQueue.main.async { self.gatherOnMain(host: host, done: done) }
    }

    private func gatherOnMain(host: UIViewController, done: @escaping () -> Void) {
        switch phase {
        case .settled:
            done()
        case .inFlight:
            waiting.append(done)
        case .idle:
            guard configured else {
                phase = .settled
                done()
                return
            }
            phase = .inFlight
            waiting.append(done)
            // No network on a first launch: the CMP cannot answer, and the
            // rest of the launch sequence (the location prompt) must not wait
            // forever. Before an answer there are no ads, so settling early
            // is safe; the CMP keeps loading and its answer applies next time.
            let item = DispatchWorkItem { [weak self] in self?.settle() }
            timeout = item
            DispatchQueue.main.asyncAfter(deadline: .now() + 15, execute: item)
            ChoiceCmp.shared.startChoice(
                pcode: pcode,
                delegate: self,
                ccpaDelegate: self,
                gbcDelegate: nil,
                shouldDisplayIDFA: false,
                style: nil
            )
        }
    }

    /// What the CMP last said, persisted by it across launches in the IAB
    /// keys (so a stored choice holds offline, or while the CMP is failing
    /// to load this time): under the TCF (GDPR applies) a TC string with
    /// purpose 1 (store and/or access information on a device) consented is
    /// the floor every ad request needs; outside the TCF ads may run, with
    /// the US opt-out riding on the GPP string the networks read; before the
    /// CMP has said which applies, no ads.
    func canRequestAds() -> Bool {
        guard configured else { return false }
        let defaults = UserDefaults.standard
        let stored = defaults.object(forKey: "IABTCF_gdprApplies") as? Int
        guard let gdprApplies = stored.map({ $0 == 1 }) ?? ChoiceCmp.shared.ping().gdprApplies else {
            return false
        }
        if !gdprApplies { return true }
        let tcString = defaults.string(forKey: "IABTCF_TCString") ?? ""
        let purposes = defaults.string(forKey: "IABTCF_PurposeConsents") ?? ""
        return !tcString.isEmpty && purposes.hasPrefix("1")
    }

    /// A "Privacy choices" entry is owed wherever a form applies: the TCF
    /// regions and the US states with an opt-out notice.
    func privacyOptionsRequired() -> Bool {
        guard configured else { return false }
        let ping = ChoiceCmp.shared.ping()
        return ping.gdprApplies == true || ping.usRegulationApplies
    }

    func showPrivacyOptions(host: UIViewController, done: @escaping () -> Void) {
        guard configured else { return done() }
        let ping = ChoiceCmp.shared.ping()
        privacyOptionsDone = done
        if ping.gdprApplies == true {
            ChoiceCmp.shared.forceDisplayUI()
        } else if ping.usRegulationApplies {
            ChoiceCmp.shared.showUSRegulations(ccpaDelegate: self)
        } else {
            privacyOptionsDone = nil
            done()
        }
    }

    /// Appodeal's banner is 320x50 stretched to the width (smart sizing),
    /// so its height is 50 points wherever a banner may show at all.
    func bannerHeight(widthPoints: Double) -> Double { canRequestAds() ? 50 : 0 }

    func makeBanner(widthPoints: Double, host: UIViewController) -> UIView {
        let view = APDBannerView(size: kAPDAdSize320x50, rootViewController: host)
        view.usesSmartSizing = true
        view.frame = CGRect(x: 0, y: 0, width: widthPoints, height: 50)
        view.autoresizingMask = [.flexibleWidth]
        banners.add(view)
        if sdkInitialized { view.loadAd() }
        return view
    }

    // MARK: The consent request settling

    /// Once per request: the CMP has answered (or cannot). Where ads may run
    /// the tracking prompt follows, then the SDK starts; the callers waiting
    /// on `gather` run after that, so a first launch's other prompts queue
    /// behind the consent form and the ATT alert.
    private func settle() {
        guard phase == .inFlight else { return }
        phase = .settled
        timeout?.cancel()
        timeout = nil
        let callbacks = waiting
        waiting.removeAll()
        startAdsIfAllowed { callbacks.forEach { $0() } }
    }

    /// Whenever the answer may have changed (the request settling, a
    /// privacy-options form closing, a form that came in after the offline
    /// timeout): where ads may now run and the SDK is not up yet, the
    /// tracking prompt and then Appodeal, before [then]. Otherwise straight
    /// through. Idempotent, so a second settling costs nothing.
    private func startAdsIfAllowed(then: @escaping () -> Void) {
        guard canRequestAds(), !sdkStarted else { return then() }
        askToTrack { [weak self] in
            self?.startAppodeal()
            then()
        }
    }

    /// Apple's prompt, asked only once the consent platform allows ads at all
    /// — never on top of its form, and never where ads won't run.
    private func askToTrack(then: @escaping () -> Void) {
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return then() }
        ATTrackingManager.requestTrackingAuthorization { _ in DispatchQueue.main.async(execute: then) }
    }

    private func startAppodeal() {
        guard !sdkStarted else { return }
        sdkStarted = true
        #if DEBUG
            // Test creatives, 100% fill, so a debug build never serves a real
            // ad; verbose logs name the networks that answered (Stage C).
            Appodeal.setTestingEnabled(true)
            Appodeal.setLogLevel(.verbose)
        #endif
        Appodeal.setSmartBannersEnabled(true)
        Appodeal.setInitializationDelegate(self)
        Appodeal.initialize(withApiKey: appKey, types: .banner)
    }
}

// MARK: - Appodeal

extension AdsService: AppodealInitializationDelegate {
    /// The one place a banner is asked to load.
    func appodealSDKDidInitialize() {
        sdkInitialized = true
        banners.allObjects.forEach { $0.loadAd() }
    }
}

// MARK: - InMobi CMP

extension AdsService: ChoiceCmpDelegate, CCPADelegate {
    func cmpDidLoad(info: PingResponse) {}

    func didReceiveIABVendorConsent(gdprData: GDPRData, updated: Bool) {}

    func didReceiveNonIABVendorConsent(nonIabData: NonIABData, updated: Bool) {}

    /// Google's Additional Consent string carries AppLovin (provider 1301),
    /// which Appodeal mediates; the AppLovin SDK reads it from storage, so
    /// nothing is forwarded here.
    func didReceiveAdditionalConsent(acData: ACData, updated: Bool) {}

    func didReceiveUSRegulationsConsent(usRegData: USRegulationsData) {}

    func didReceiveCCPAConsent(string: String) {}

    func didReceiveActionButtonTap(action: ActionButtons) {}

    /// The request is settled the moment the CMP is not showing anything:
    /// a form dismissed, a form not needed here, a stored choice applied.
    /// While a form is up the timeout is withdrawn — the user takes as long
    /// as they take. A form closing after the request already settled (the
    /// privacy-options form, or a first-launch form that outlived the
    /// offline timeout) may have changed the answer, so ads start then too.
    func cmpUIStatusChanged(info: DisplayInfo) {
        switch info.displayStatus {
        case .visible:
            timeout?.cancel()
            timeout = nil
        case .hidden, .disabled, .dismissed:
            formClosed()
        @unknown default:
            formClosed()
        }
    }

    /// An error is an answer too: no form will come. Ads stay off unless a
    /// stored choice allows them (`canRequestAds` reads the IAB keys the CMP
    /// persisted on an earlier launch).
    func cmpDidError(error: Error) {
        formClosed()
    }

    private func formClosed() {
        if phase == .inFlight {
            settle()
            return
        }
        let done = privacyOptionsDone
        privacyOptionsDone = nil
        startAdsIfAllowed { done?() }
    }

    /// The user travelled into another regulation's territory; the CMP has
    /// re-evaluated and will show its form on the next request. Nothing to
    /// do now: what applies is re-read whenever `canRequestAds` is asked.
    func userDidMoveToOtherState() {}
}
