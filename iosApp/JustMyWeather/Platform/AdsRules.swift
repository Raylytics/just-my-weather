import Foundation

/// The decisions AdsService makes from plain values: which ids count, whether
/// the consent platform's stored answer allows ads, whether its form is still
/// to come, and whether a "Privacy choices" entry is owed. Nothing here
/// touches the InMobi CMP or Appodeal, so AdsRulesTests runs them without
/// either SDK; AdsService reads the SDK and UserDefaults and hands the
/// values in.
enum AdsRules {
    /// An id from Info.plist (filled in from Config/Ads.xcconfig), or empty:
    /// a missing xcconfig leaves the `$(...)` reference unexpanded, and the
    /// committed example carries placeholders; neither is an id.
    static func id(_ value: Any?) -> String {
        let s = (value as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if s.isEmpty || s.hasPrefix("$(") || s.hasPrefix("REPLACE_WITH") { return "" }
        return s
    }

    /// Both ids present, and the p-code shaped like one (the release gate,
    /// scripts/ios/common.sh require_ads_configured, checks the same).
    static func configured(appKey: String, pcode: String) -> Bool {
        !appKey.isEmpty && pcode.hasPrefix("p-")
    }

    /// What the CMP last said, persisted by it across launches in the IAB
    /// keys (so a stored choice holds offline, or while the CMP is failing
    /// to load this time): under the TCF (GDPR applies) a TC string with
    /// purpose 1 (store and/or access information on a device) consented is
    /// the floor every ad request needs; outside the TCF ads may run, with
    /// the US opt-out riding on the GPP string the networks read; before the
    /// CMP has said which applies, no ads.
    ///
    /// - storedGdprApplies: `IABTCF_gdprApplies` (1 or 0), or nil when the
    ///   CMP has not written it; pingGdprApplies is then the SDK's live
    ///   answer, itself nil before the CMP has loaded.
    static func canRequestAds(
        configured: Bool,
        storedGdprApplies: Int?,
        pingGdprApplies: Bool?,
        tcString: String?,
        purposeConsents: String?
    ) -> Bool {
        guard configured else { return false }
        guard let gdprApplies = storedGdprApplies.map({ $0 == 1 }) ?? pingGdprApplies else { return false }
        if !gdprApplies { return true }
        return !(tcString ?? "").isEmpty && (purposeConsents ?? "").hasPrefix("1")
    }

    /// Once the CMP has loaded, whether a form is still coming: under the
    /// TCF, no TC string yet means the form; under US state law, a notice
    /// not yet shown means the notice; elsewhere, or with a choice on file,
    /// nothing.
    static func formExpected(
        gdprApplies: Bool?,
        usRegulationApplies: Bool,
        tcString: String?,
        usNoticeShown: Bool
    ) -> Bool {
        if gdprApplies == true { return (tcString ?? "").isEmpty }
        if usRegulationApplies { return !usNoticeShown }
        return false
    }

    /// A "Privacy choices" entry is owed wherever a form applies: the TCF
    /// regions and the US states with an opt-out notice.
    static func privacyOptionsRequired(configured: Bool, gdprApplies: Bool?, usRegulationApplies: Bool) -> Bool {
        configured && (gdprApplies == true || usRegulationApplies)
    }
}
