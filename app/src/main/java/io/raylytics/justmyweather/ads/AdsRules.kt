package io.raylytics.justmyweather.ads

/**
 * The decisions [InMobiConsentGateway] and the app make from plain values:
 * which ids count, whether the consent platform's stored answer allows ads,
 * whether its form is still to come, and whether a "Privacy choices" entry
 * is owed. Nothing here touches the InMobi CMP or Appodeal, so
 * AdsRulesTest runs them on the JVM; the gateway reads the SDK and the
 * SharedPreferences and hands the values in. The iOS twin is
 * iosApp/JustMyWeather/Platform/AdsRules.swift; keep the two in step.
 */
object AdsRules {
    /** An id from BuildConfig (filled in from app/ads.properties), or empty:
     * the committed example carries placeholders, and a placeholder is not
     * an id. */
    fun id(value: String?): String {
        val s = value.orEmpty().trim()
        return if (s.isEmpty() || s.startsWith("REPLACE_WITH")) "" else s
    }

    /** Both ids present, and the p-code shaped like one (the release gate,
     * scripts/android/common.sh require_ads_configured, checks the same). */
    fun configured(
        appKey: String,
        pcode: String,
    ): Boolean = appKey.isNotEmpty() && pcode.startsWith("p-")

    /**
     * What the CMP last said, persisted by it across launches in the IAB keys
     * (so a stored choice holds offline, or while the CMP is failing to load
     * this time): under the TCF (GDPR applies) a TC string with purpose 1
     * (store and/or access information on a device) consented is the floor
     * every ad request needs; outside the TCF ads may run, with the US
     * opt-out riding on the GPP string the networks read; before the CMP has
     * said which applies, no ads.
     *
     * [storedGdprApplies] is `IABTCF_gdprApplies` (1 or 0), or null when the
     * CMP has not written it; [pingGdprApplies] is then the CMP's live answer
     * from its last load, itself null before the CMP has loaded.
     *
     * [answeredThisLaunch]: the CMP has loaded (or failed) on this launch.
     * Before that the stored keys are an earlier launch's answer and may be
     * stale (consent expired, a new vendor list, the user moved), and the
     * runbook forbids starting Appodeal before the CMP resolves — so no ads,
     * even when a request settled early on the offline timeout (roborev
     * 6989). After an error the stored answer stands: the CMP cannot say
     * otherwise this launch.
     */
    fun canRequestAds(
        configured: Boolean,
        answeredThisLaunch: Boolean,
        storedGdprApplies: Int?,
        pingGdprApplies: Boolean?,
        tcString: String?,
        purposeConsents: String?,
    ): Boolean {
        if (!configured || !answeredThisLaunch) return false
        val gdprApplies = storedGdprApplies?.let { it == 1 } ?: pingGdprApplies ?: return false
        if (!gdprApplies) return true
        return !tcString.isNullOrEmpty() && purposeConsents.orEmpty().startsWith("1")
    }

    /**
     * Once the CMP has loaded, whether a form is still coming: under the TCF,
     * no TC string yet means the form; under US state law, a notice not yet
     * shown means the notice ([usNoticeShown] is the CMP's `MSPAShown`, the
     * twin of iOS's `INMOBI_MSPA_Shown`; not the GPP string, which the
     * Android CMP writes at load, before the notice is up); elsewhere, or
     * with a choice on file, nothing.
     */
    fun formExpected(
        gdprApplies: Boolean?,
        usRegulationApplies: Boolean,
        tcString: String?,
        usNoticeShown: Boolean,
    ): Boolean =
        when {
            gdprApplies == true -> tcString.isNullOrEmpty()
            usRegulationApplies -> !usNoticeShown
            else -> false
        }

    /** What a non-visible CMP UI status means (Android, where the CMP's
     * statuses arrive out of step with its screens). */
    enum class HiddenStatus {
        /** A form was up: it has closed. */
        FORM_CLOSED,

        /** Nothing was coming (a privacy-options request the CMP declined,
         * or a load that expected no form): the CMP has answered. */
        ANSWER,

        /** A form was expected but none is up: the CMP may send this just
         * before its notice appears, or because it decided no notice is
         * needed here. Settle after a short grace unless a form appears. */
        ANSWER_AFTER_GRACE,
    }

    fun hiddenStatus(
        formVisible: Boolean,
        privacyOptionsPending: Boolean,
        loaded: Boolean,
        formExpected: Boolean,
    ): HiddenStatus =
        when {
            formVisible -> HiddenStatus.FORM_CLOSED
            privacyOptionsPending || (loaded && !formExpected) -> HiddenStatus.ANSWER
            else -> HiddenStatus.ANSWER_AFTER_GRACE
        }

    /** A "Privacy choices" entry is owed wherever a form applies: the TCF
     * regions and the US states with an opt-out notice. */
    fun privacyOptionsRequired(
        configured: Boolean,
        gdprApplies: Boolean?,
        usRegulationApplies: Boolean,
    ): Boolean = configured && (gdprApplies == true || usRegulationApplies)
}
