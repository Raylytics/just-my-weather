import AppTrackingTransparency
import Appodeal
import InMobiCMP
import OSLog
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
    /// Every `gather` caller, kept for the app's life: each is told when the
    /// request settles and again whenever a later choice changes the answer
    /// (IosApp re-reads `canRequestAds` and `privacyOptionsRequired` in it).
    private var listeners: [() -> Void] = []
    private var timeout: DispatchWorkItem?
    /// After the CMP loads, a short grace in which a form may still appear;
    /// if none does, there is nothing to wait for and the request settles.
    private var grace: DispatchWorkItem?
    /// A CMP form is on screen.
    private var formVisible = false
    /// A choice was made on the form; polling for the form to leave the
    /// screen, since the US notice sends no dismissed status of its own.
    private var closeWatch: DispatchWorkItem?

    /// The CMP has answered on this launch: loaded with no form to come, a
    /// form closed, or an error. Until then the stored IAB keys are an
    /// earlier launch's answer and may be stale (consent expired, a new
    /// vendor list, the user moved), so they may size the banner's slot
    /// (`bannerHeight`) but never start ATT or Appodeal — including when the
    /// request settled on the offline timeout (roborev 6989/6991, the Android
    /// twin's `answeredThisLaunch` in AdsRules.kt).
    private var answered = false

    /// `startAdsIfAllowed` is in the tracking prompt; a second trigger waits.
    private var adsStarting = false
    private var afterStart: [() -> Void] = []
    private var sdkStarted = false
    private var sdkInitialized = false
    /// The consent and ad sequence, for Stage C and for the field: every CMP
    /// event with its status, and the SDK's start. Unified log, "ads".
    private let log = Logger(subsystem: Bundle.main.bundleIdentifier ?? "JustMyWeather", category: "ads")
    /// Banners handed to Compose before the SDK finished initializing; they
    /// load from the init callback.
    private let banners = NSHashTable<APDBannerView>.weakObjects()
    /// The follow-up for a privacy-options form the user opened from Settings.
    private var privacyOptionsDone: (() -> Void)?

    override init() {
        let info = Bundle.main.infoDictionary ?? [:]
        appKey = AdsRules.id(info["JMWAppodealAppKey"])
        pcode = AdsRules.id(info["JMWInMobiPcode"])
        super.init()
    }

    private var configured: Bool { AdsRules.configured(appKey: appKey, pcode: pcode) }

    // MARK: AdsBridge

    /// Called from the shared code's background scope; everything here is
    /// UIKit and the CMP's web view, and the request state is read by the
    /// CMP's callbacks on the main queue, so the whole thing hops there.
    func gather(host: UIViewController, done: @escaping () -> Void) {
        DispatchQueue.main.async { self.gatherOnMain(host: host, done: done) }
    }

    private func gatherOnMain(host: UIViewController, done: @escaping () -> Void) {
        log.info("gather: phase=\(String(describing: self.phase)) configured=\(self.configured)")
        listeners.append(done)
        switch phase {
        case .settled:
            done()
        case .inFlight:
            break
        case .idle:
            guard configured else {
                phase = .settled
                done()
                return
            }
            phase = .inFlight
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
    /// to load this time). The decision is AdsRules.canRequestAds; this
    /// reads the stored keys and, when nothing is stored yet, the SDK's
    /// live answer. The ping is asked only when the stored key is absent:
    /// before the CMP has loaded it is nil, and the rule then says no ads.
    func canRequestAds() -> Bool {
        guard configured else { return false }
        let defaults = UserDefaults.standard
        let stored = defaults.object(forKey: "IABTCF_gdprApplies") as? Int
        return AdsRules.canRequestAds(
            configured: true,
            storedGdprApplies: stored,
            pingGdprApplies: stored == nil ? ChoiceCmp.shared.ping().gdprApplies : nil,
            tcString: defaults.string(forKey: "IABTCF_TCString"),
            purposeConsents: defaults.string(forKey: "IABTCF_PurposeConsents")
        )
    }

    /// A "Privacy choices" entry is owed wherever a form applies (AdsRules).
    func privacyOptionsRequired() -> Bool {
        guard configured else { return false }
        let ping = ChoiceCmp.shared.ping()
        return AdsRules.privacyOptionsRequired(
            configured: true, gdprApplies: ping.gdprApplies, usRegulationApplies: ping.usRegulationApplies
        )
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
        log.info("consent settled: canRequestAds=\(self.canRequestAds())")
        phase = .settled
        timeout?.cancel()
        timeout = nil
        grace?.cancel()
        grace = nil
        startAdsIfAllowed { [weak self] in self?.notifyListeners() }
    }

    private func notifyListeners() {
        listeners.forEach { $0() }
    }

    /// Whenever the answer may have changed (the request settling, a
    /// privacy-options form closing, a form that came in after the offline
    /// timeout): where ads may now run and the SDK is not up yet, the
    /// tracking prompt and then Appodeal, before [then]. Otherwise straight
    /// through. While the tracking prompt is up, a second trigger queues its
    /// follow-up behind the first rather than asking again.
    private func startAdsIfAllowed(then: @escaping () -> Void) {
        if adsStarting {
            afterStart.append(then)
            return
        }
        guard answered, canRequestAds(), !sdkStarted else { return then() }
        adsStarting = true
        afterStart.append(then)
        askToTrack { [weak self] in
            guard let self else { return }
            self.startAppodeal()
            self.adsStarting = false
            let queued = self.afterStart
            self.afterStart.removeAll()
            queued.forEach { $0() }
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
        log.info("appodeal: initializing (banner)")
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
        log.info("appodeal: initialized; loading \(self.banners.count) banner(s)")
        sdkInitialized = true
        banners.allObjects.forEach { $0.loadAd() }
    }
}

// MARK: - InMobi CMP

extension AdsService: ChoiceCmpDelegate, CCPADelegate {
    /// Loaded. Whether a form is coming is read from what the CMP has
    /// stored: under the TCF, no TC string yet means the form; under US
    /// state law, a notice not yet shown means the notice; elsewhere, or
    /// with a choice on file, nothing. With nothing coming the request
    /// settles after a short grace (a re-consent form would show by then);
    /// with a form coming it waits for the form, with a longer backstop than
    /// the offline timeout since the page is loading over the network.
    func cmpDidLoad(info: PingResponse) {
        log.info("cmp loaded: gdprApplies=\(String(describing: info.gdprApplies)) usRegulation=\(info.usRegulationApplies) display=\(info.displayStatus.rawValue) status=\(info.cmpStatus.rawValue)")
        let expected = formExpected(info)
        // With a form to come the CMP has not answered yet; formClosed does.
        if !expected { answered = true }
        if phase == .settled, !formVisible, !expected {
            // Loaded after the request settled on the offline timeout, with
            // no form to come: this is the launch's answer.
            startAdsIfAllowed { [weak self] in self?.notifyListeners() }
            return
        }
        guard phase == .inFlight, !formVisible else { return }
        if expected {
            timeout?.cancel()
            let item = DispatchWorkItem { [weak self] in self?.settle() }
            timeout = item
            DispatchQueue.main.asyncAfter(deadline: .now() + 60, execute: item)
            return
        }
        let item = DispatchWorkItem { [weak self] in
            guard let self, !self.formVisible else { return }
            self.settle()
        }
        grace = item
        DispatchQueue.main.asyncAfter(deadline: .now() + 2, execute: item)
    }

    /// The decision is AdsRules.formExpected; this reads what the CMP stored.
    private func formExpected(_ info: PingResponse) -> Bool {
        let defaults = UserDefaults.standard
        return AdsRules.formExpected(
            gdprApplies: info.gdprApplies,
            usRegulationApplies: info.usRegulationApplies,
            tcString: defaults.string(forKey: "IABTCF_TCString"),
            usNoticeShown: defaults.bool(forKey: "INMOBI_MSPA_Shown")
        )
    }

    func didReceiveIABVendorConsent(gdprData: GDPRData, updated: Bool) {
        log.info("cmp iab consent: gdprApplies=\(String(describing: gdprData.gdprApplies)) updated=\(updated) tc=\(gdprData.tcString.isEmpty ? "empty" : "set")")
        if updated { choiceMade() }
    }

    func didReceiveNonIABVendorConsent(nonIabData: NonIABData, updated: Bool) {}

    /// Google's Additional Consent string carries AppLovin (provider 1301),
    /// which Appodeal mediates; the AppLovin SDK reads it from storage, so
    /// nothing is forwarded here.
    func didReceiveAdditionalConsent(acData: ACData, updated: Bool) {
        log.info("cmp additional consent: updated=\(updated) ac=\(acData.acString.isEmpty ? "empty" : "set")")
    }

    func didReceiveUSRegulationsConsent(usRegData: USRegulationsData) {
        log.info("cmp us regulations consent: gpp=\(usRegData.gppString.isEmpty ? "empty" : "set")")
        choiceMade()
    }

    func didReceiveCCPAConsent(string: String) {
        choiceMade()
    }

    /// A choice landed while a form was up. The TCF form reports its own
    /// dismissal; the US notice does not (its confirmation alert closes
    /// quietly), so the SDK's ping is polled until the form has left the
    /// screen, and then the tracking prompt can follow without stacking.
    /// A minute after the choice the request settles regardless: the choice
    /// is made, so nothing can stack on a form still being answered, and a
    /// ping that never clears must not hold the launch sequence forever.
    private func choiceMade() {
        guard formVisible, closeWatch == nil else { return }
        watchForClose(since: Date())
    }

    private func watchForClose(since choice: Date) {
        closeWatch = nil
        guard formVisible else { return }
        let stillUp = ChoiceCmp.shared.ping().displayStatus == .visible
        if stillUp, Date().timeIntervalSince(choice) < 60 {
            let item = DispatchWorkItem { [weak self] in self?.watchForClose(since: choice) }
            closeWatch = item
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5, execute: item)
            return
        }
        log.info("cmp form closed after a choice\(stillUp ? " (ping still says visible a minute later; settling anyway)" : "")")
        formVisible = false
        formClosed()
    }

    func didReceiveActionButtonTap(action: ActionButtons) {}

    /// The request is settled the moment the CMP is not showing anything:
    /// a form dismissed, a form not needed here, a stored choice applied.
    /// While a form is up the timeout is withdrawn — the user takes as long
    /// as they take. A form closing after the request already settled (the
    /// privacy-options form, or a first-launch form that outlived the
    /// offline timeout) may have changed the answer, so ads start then too.
    func cmpUIStatusChanged(info: DisplayInfo) {
        log.info("cmp ui: display=\(info.displayStatus.rawValue) regulation=\(info.regulationShown.rawValue) message=\(info.displayMessage, privacy: .public)")
        switch info.displayStatus {
        case .visible:
            formVisible = true
            timeout?.cancel()
            timeout = nil
            grace?.cancel()
            grace = nil
        case .hidden, .disabled, .dismissed:
            formVisible = false
            closeWatch?.cancel()
            closeWatch = nil
            formClosed()
        @unknown default:
            formVisible = false
            formClosed()
        }
    }

    /// An error is an answer too: no form will come. Ads stay off unless a
    /// stored choice allows them (`canRequestAds` reads the IAB keys the CMP
    /// persisted on an earlier launch).
    func cmpDidError(error: Error) {
        log.error("cmp error: \(error.localizedDescription, privacy: .public)")
        formClosed()
    }

    /// A form closing after the request settled may have changed the
    /// answer: ads start where they now may, then every `gather` caller is
    /// told (the shared state re-reads), then the privacy-options follow-up.
    private func formClosed() {
        answered = true
        if phase == .inFlight {
            settle()
            return
        }
        let done = privacyOptionsDone
        privacyOptionsDone = nil
        startAdsIfAllowed { [weak self] in
            self?.notifyListeners()
            done?()
        }
    }

    /// The user travelled into another regulation's territory; the CMP has
    /// re-evaluated and will show its form on the next request. Nothing to
    /// do now: what applies is re-read whenever `canRequestAds` is asked.
    func userDidMoveToOtherState() {}
}
