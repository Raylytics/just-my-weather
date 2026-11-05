package io.raylytics.justmyweather.ads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** InMobiConsentGateway's decisions with the CMP's answers handed in as
 * plain values: no InMobi CMP, no Appodeal, no device. Mirrors the iOS
 * AdsRulesTests case for case. */
class AdsRulesTest {
    @Test
    fun `an id is taken as is and trimmed`() {
        assertEquals("abc123", AdsRules.id("abc123"))
        assertEquals("p-abc", AdsRules.id("  p-abc  \n"))
    }

    @Test
    fun `a missing file or a placeholder is no id`() {
        assertEquals("", AdsRules.id(null))
        assertEquals("", AdsRules.id(""))
        assertEquals("", AdsRules.id("   "))
        assertEquals("", AdsRules.id("REPLACE_WITH_APPODEAL_APP_KEY"), "the committed example")
    }

    @Test
    fun `configured needs both ids and a p-code shape`() {
        assertTrue(AdsRules.configured("k", "p-abc"))
        assertFalse(AdsRules.configured("", "p-abc"))
        assertFalse(AdsRules.configured("k", ""))
        assertFalse(AdsRules.configured("k", "abc"), "an InMobi p-code starts with p-")
    }

    @Test
    fun `unconfigured never requests ads`() {
        assertFalse(AdsRules.canRequestAds(false, 0, false, "tc", "1"))
    }

    @Test
    fun `before the CMP has said which regime applies there are no ads`() {
        assertFalse(AdsRules.canRequestAds(true, null, null, null, null))
    }

    @Test
    fun `outside the TCF ads may run`() {
        assertTrue(AdsRules.canRequestAds(true, 0, null, null, null), "stored answer")
        assertTrue(AdsRules.canRequestAds(true, null, false, null, null), "live answer when nothing is stored")
    }

    @Test
    fun `the stored answer wins over the live ping`() {
        assertFalse(AdsRules.canRequestAds(true, 1, false, "", ""), "stored says GDPR applies, no consent yet")
        assertTrue(AdsRules.canRequestAds(true, 0, true, "", ""), "stored says outside the TCF")
    }

    @Test
    fun `under the TCF purpose 1 consent is the floor`() {
        assertTrue(AdsRules.canRequestAds(true, 1, null, "CP...", "1011"))
        assertFalse(AdsRules.canRequestAds(true, 1, null, "CP...", "0111"), "purpose 1 refused")
        assertFalse(AdsRules.canRequestAds(true, 1, null, "", "1"), "no TC string yet")
        assertFalse(AdsRules.canRequestAds(true, 1, null, "CP...", null), "purposes never written")
    }

    @Test
    fun `under the TCF the form comes until a TC string is stored`() {
        assertTrue(AdsRules.formExpected(true, false, null, null))
        assertTrue(AdsRules.formExpected(true, false, "", "gpp"))
        assertFalse(AdsRules.formExpected(true, false, "CP...", null))
    }

    @Test
    fun `in a US state the notice comes until a GPP string is stored`() {
        assertTrue(AdsRules.formExpected(false, true, null, null))
        assertTrue(AdsRules.formExpected(null, true, null, ""))
        assertFalse(AdsRules.formExpected(false, true, null, "DBABLA~..."))
    }

    @Test
    fun `elsewhere no form is coming`() {
        assertFalse(AdsRules.formExpected(false, false, null, null))
        assertFalse(AdsRules.formExpected(null, false, null, null))
    }

    @Test
    fun `privacy choices are owed wherever a form applies`() {
        assertTrue(AdsRules.privacyOptionsRequired(true, true, false))
        assertTrue(AdsRules.privacyOptionsRequired(true, false, true))
        assertFalse(AdsRules.privacyOptionsRequired(true, false, false))
        assertFalse(AdsRules.privacyOptionsRequired(true, null, false))
        assertFalse(AdsRules.privacyOptionsRequired(false, true, true), "unconfigured owes nothing")
    }
}
