import XCTest
@testable import JustMyWeather

/// AdsService's decisions, with the SDK and UserDefaults values handed in as
/// plain data: no InMobi CMP, no Appodeal, no network. The seams that could
/// not be tested while they lived inside the service.
final class AdsRulesTests: XCTestCase {
    // MARK: Ids from Info.plist

    func testAnIdIsTakenAsIsAndTrimmed() {
        XCTAssertEqual(AdsRules.id("abc123"), "abc123")
        XCTAssertEqual(AdsRules.id("  p-abc  \n"), "p-abc")
    }

    func testAMissingXcconfigOrPlaceholderIsNoId() {
        XCTAssertEqual(AdsRules.id(nil), "", "key absent")
        XCTAssertEqual(AdsRules.id(""), "")
        XCTAssertEqual(AdsRules.id("   "), "")
        XCTAssertEqual(AdsRules.id("$(JMW_APPODEAL_APP_KEY)"), "", "xcconfig missing: reference unexpanded")
        XCTAssertEqual(AdsRules.id("REPLACE_WITH_APPODEAL_APP_KEY"), "", "the committed example")
        XCTAssertEqual(AdsRules.id(42), "", "not a string")
    }

    func testConfiguredNeedsBothIdsAndAPCodeShape() {
        XCTAssertTrue(AdsRules.configured(appKey: "k", pcode: "p-abc"))
        XCTAssertFalse(AdsRules.configured(appKey: "", pcode: "p-abc"))
        XCTAssertFalse(AdsRules.configured(appKey: "k", pcode: ""))
        XCTAssertFalse(AdsRules.configured(appKey: "k", pcode: "abc"), "an InMobi p-code starts with p-")
    }

    // MARK: Whether ads may run

    func testUnconfiguredNeverRequestsAds() {
        XCTAssertFalse(AdsRules.canRequestAds(
            configured: false, storedGdprApplies: 0, pingGdprApplies: false, tcString: "tc", purposeConsents: "1"
        ))
    }

    func testBeforeTheCmpHasSaidWhichRegimeAppliesThereAreNoAds() {
        XCTAssertFalse(AdsRules.canRequestAds(
            configured: true, storedGdprApplies: nil, pingGdprApplies: nil, tcString: nil, purposeConsents: nil
        ))
    }

    func testOutsideTheTcfAdsMayRun() {
        XCTAssertTrue(AdsRules.canRequestAds(
            configured: true, storedGdprApplies: 0, pingGdprApplies: nil, tcString: nil, purposeConsents: nil
        ), "stored answer")
        XCTAssertTrue(AdsRules.canRequestAds(
            configured: true, storedGdprApplies: nil, pingGdprApplies: false, tcString: nil, purposeConsents: nil
        ), "live answer when nothing is stored")
    }

    func testTheStoredAnswerWinsOverTheLivePing() {
        XCTAssertFalse(AdsRules.canRequestAds(
            configured: true, storedGdprApplies: 1, pingGdprApplies: false, tcString: "", purposeConsents: ""
        ), "stored says GDPR applies and no consent yet; the ping's 'no' does not override")
        XCTAssertTrue(AdsRules.canRequestAds(
            configured: true, storedGdprApplies: 0, pingGdprApplies: true, tcString: "", purposeConsents: ""
        ), "stored says outside the TCF")
    }

    func testUnderTheTcfPurposeOneConsentIsTheFloor() {
        XCTAssertTrue(AdsRules.canRequestAds(
            configured: true, storedGdprApplies: 1, pingGdprApplies: nil, tcString: "CP...", purposeConsents: "1011"
        ))
        XCTAssertFalse(AdsRules.canRequestAds(
            configured: true, storedGdprApplies: 1, pingGdprApplies: nil, tcString: "CP...", purposeConsents: "0111"
        ), "purpose 1 refused")
        XCTAssertFalse(AdsRules.canRequestAds(
            configured: true, storedGdprApplies: 1, pingGdprApplies: nil, tcString: "", purposeConsents: "1"
        ), "no TC string yet")
        XCTAssertFalse(AdsRules.canRequestAds(
            configured: true, storedGdprApplies: 1, pingGdprApplies: nil, tcString: "CP...", purposeConsents: nil
        ), "purposes never written")
    }

    // MARK: Whether a form is still coming after the CMP loads

    func testUnderTheTcfTheFormComesUntilATcStringIsStored() {
        XCTAssertTrue(AdsRules.formExpected(gdprApplies: true, usRegulationApplies: false, tcString: nil, usNoticeShown: false))
        XCTAssertTrue(AdsRules.formExpected(gdprApplies: true, usRegulationApplies: false, tcString: "", usNoticeShown: false))
        XCTAssertFalse(AdsRules.formExpected(gdprApplies: true, usRegulationApplies: false, tcString: "CP...", usNoticeShown: false))
    }

    func testUnderUsStateLawTheNoticeComesOnce() {
        XCTAssertTrue(AdsRules.formExpected(gdprApplies: false, usRegulationApplies: true, tcString: nil, usNoticeShown: false))
        XCTAssertFalse(AdsRules.formExpected(gdprApplies: false, usRegulationApplies: true, tcString: nil, usNoticeShown: true))
    }

    func testElsewhereNoFormIsExpected() {
        XCTAssertFalse(AdsRules.formExpected(gdprApplies: false, usRegulationApplies: false, tcString: nil, usNoticeShown: false))
        XCTAssertFalse(AdsRules.formExpected(gdprApplies: nil, usRegulationApplies: false, tcString: nil, usNoticeShown: false),
                       "the CMP has not said; nothing to wait for beyond the grace")
    }

    // MARK: The Privacy choices row

    func testPrivacyChoicesAreOwedWhereAFormApplies() {
        XCTAssertTrue(AdsRules.privacyOptionsRequired(configured: true, gdprApplies: true, usRegulationApplies: false))
        XCTAssertTrue(AdsRules.privacyOptionsRequired(configured: true, gdprApplies: false, usRegulationApplies: true))
        XCTAssertFalse(AdsRules.privacyOptionsRequired(configured: true, gdprApplies: false, usRegulationApplies: false))
        XCTAssertFalse(AdsRules.privacyOptionsRequired(configured: true, gdprApplies: nil, usRegulationApplies: false))
        XCTAssertFalse(AdsRules.privacyOptionsRequired(configured: false, gdprApplies: true, usRegulationApplies: true),
                       "no CMP without ids, so no row")
    }
}
