package io.raylytics.justmyweather.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.inmobi.cmp.ChoiceCmp
import com.inmobi.cmp.ChoiceCmpCallback
import com.inmobi.cmp.core.cmpapi.status.DisplayStatus
import com.inmobi.cmp.core.model.ACData
import com.inmobi.cmp.core.model.GDPRData
import com.inmobi.cmp.core.model.gbc.GoogleBasicConsents
import com.inmobi.cmp.core.model.mspa.USRegulationData
import com.inmobi.cmp.model.ActionButton
import com.inmobi.cmp.model.ChoiceError
import com.inmobi.cmp.model.DisplayInfo
import com.inmobi.cmp.model.NonIABData
import com.inmobi.cmp.model.PingReturn

/**
 * The InMobi CMP (Choice) behind [ConsentGateway] — google-ads-exit Stage B,
 * the Android twin of iosApp/JustMyWeather/Platform/AdsService.swift's CMP
 * half, with the same settle rules. The only file that imports the CMP.
 *
 * A request settles when the CMP is not showing anything: form dismissed, no
 * form needed here, a stored choice applied, an error, or a timeout for a
 * launch with no network. While a form is up the timeout is withdrawn — the
 * user takes as long as they take. The CMP writes the IAB TCF and GPP strings
 * to the default SharedPreferences, where Appodeal and its networks read
 * them; nothing is forwarded by hand, and nothing calls Appodeal's own
 * consent manager or `setNonPersonalized` (the guardrail refuses both), so
 * the CMP governs.
 *
 * The CMP's `ping()` is not public on Android, so the last `onCmpLoaded`
 * answer stands in for it, and the form's closing is read from
 * `onCMPUIStatusChanged` (the CMP is its own Activity here, so a rotation of
 * MainActivity does not take its form down) with a short fallback after a
 * choice. The p-code is passed without its "p-" prefix, as InMobi's Android
 * SDK expects; the property is picked by the app's package name, which the
 * portal's Just My Weather property carries (the iOS and Android apps share
 * one property and one bundle id / package name).
 *
 * All state lives on the main thread: the CMP calls back there, and the
 * gateway methods are called there (AdsConsent, from MainActivity).
 */
class InMobiConsentGateway(
    context: Context,
    pcode: String,
) : ConsentGateway,
    ChoiceCmpCallback {
    private val app = context.applicationContext as Application
    private val pcodeNoPrefix = pcode.removePrefix("p-")
    private val main = Handler(Looper.getMainLooper())

    private enum class Phase { IDLE, IN_FLIGHT, SETTLED }

    private var phase = Phase.IDLE
    private var lastPing: PingReturn? = null

    /** Every `update` caller of the request in flight, told once it settles. */
    private val waiting = mutableListOf<() -> Unit>()

    /** `showPrivacyOptions` callers, told when the re-opened form closes. */
    private val privacyOptionsWaiting = mutableListOf<() -> Unit>()
    private var onChange: () -> Unit = {}
    private var timeout: Runnable? = null

    /** After the CMP loads, a short grace in which a form may still appear. */
    private var grace: Runnable? = null

    /** A CMP form is on screen. */
    private var formVisible = false

    /** A choice was made on the form; its own closing is awaited briefly. */
    private var choiceSettle: Runnable? = null

    // --- ConsentGateway ------------------------------------------------------

    override fun canRequestAds(): Boolean {
        val p = prefs()
        return AdsRules.canRequestAds(
            configured = true,
            storedGdprApplies = if (p.contains(KEY_GDPR_APPLIES)) p.intOrNull(KEY_GDPR_APPLIES) else null,
            pingGdprApplies = lastPing?.gdprApplies,
            tcString = p.getString(KEY_TC_STRING, null),
            purposeConsents = p.getString(KEY_PURPOSE_CONSENTS, null),
        )
    }

    override fun privacyOptionsRequired(): Boolean {
        val ping = lastPing ?: return false
        return AdsRules.privacyOptionsRequired(true, ping.gdprApplies, ping.usRegulationApplies == true)
    }

    override fun update(
        activity: Activity,
        onDone: () -> Unit,
    ) {
        Log.i(TAG, "update: phase=$phase")
        when (phase) {
            Phase.SETTLED -> onDone()
            Phase.IN_FLIGHT -> waiting += onDone
            Phase.IDLE -> {
                phase = Phase.IN_FLIGHT
                waiting += onDone
                // No network on a first launch: the CMP cannot answer, and the
                // rest of the launch sequence (the location prompt) must not
                // wait forever. Before an answer there are no ads, so settling
                // early is safe; the CMP keeps loading and its answer applies
                // when its form closes (setOnChange) or on the next launch.
                armTimeout(OFFLINE_TIMEOUT_MS)
                ChoiceCmp.startChoice(app, app.packageName, pcodeNoPrefix, this)
            }
        }
    }

    override fun showPrivacyOptions(
        activity: Activity,
        onDone: () -> Unit,
    ) {
        val ping = lastPing
        when {
            ping == null -> onDone()
            ping.gdprApplies == true -> {
                privacyOptionsWaiting += onDone
                ChoiceCmp.forceDisplayUI(activity)
            }
            ping.usRegulationApplies == true -> {
                privacyOptionsWaiting += onDone
                ChoiceCmp.showUSRegulationScreen(activity)
            }
            else -> onDone()
        }
    }

    override fun setOnChange(listener: () -> Unit) {
        onChange = listener
    }

    // --- Settling ------------------------------------------------------------

    /** Where the CMP writes the IAB keys: the app's default SharedPreferences. */
    private fun prefs(): SharedPreferences =
        app.getSharedPreferences("${app.packageName}_preferences", Context.MODE_PRIVATE)

    private fun armTimeout(ms: Long) {
        cancel(timeout)
        val r = Runnable { settle() }
        timeout = r
        main.postDelayed(r, ms)
    }

    private fun cancel(r: Runnable?) {
        r?.let { main.removeCallbacks(it) }
    }

    /** Once per request: the CMP has answered (or cannot). */
    private fun settle() {
        if (phase != Phase.IN_FLIGHT) return
        phase = Phase.SETTLED
        cancel(timeout)
        timeout = null
        cancel(grace)
        grace = null
        Log.i(TAG, "consent settled: canRequestAds=${canRequestAds()}")
        val ready = waiting.toList()
        waiting.clear()
        ready.forEach { it() }
    }

    /**
     * A form closing after the request settled (the privacy-options form, or
     * a first-launch form that outlived the offline timeout) may have changed
     * the answer: the privacy-options callers are told, then AdsConsent.
     */
    private fun formClosed() {
        if (phase == Phase.IN_FLIGHT) {
            settle()
            return
        }
        val ready = privacyOptionsWaiting.toList()
        privacyOptionsWaiting.clear()
        ready.forEach { it() }
        onChange()
    }

    // --- ChoiceCmpCallback ---------------------------------------------------

    /**
     * Loaded. Whether a form is coming is read from what the CMP has stored
     * (AdsRules.formExpected). With nothing coming the request settles after
     * a short grace (a re-consent form would show by then); with a form
     * coming it waits for the form, with a longer backstop than the offline
     * timeout since the page is loading over the network.
     */
    override fun onCmpLoaded(info: PingReturn) {
        lastPing = info
        Log.i(
            TAG,
            "cmp loaded: gdprApplies=${info.gdprApplies} usRegulation=${info.usRegulationApplies} " +
                "display=${info.displayStatus} status=${info.cmpStatus}",
        )
        if (phase != Phase.IN_FLIGHT || formVisible) return
        val p = prefs()
        val expected =
            AdsRules.formExpected(
                gdprApplies = info.gdprApplies,
                usRegulationApplies = info.usRegulationApplies == true,
                tcString = p.getString(KEY_TC_STRING, null),
                gppString = p.getString(KEY_GPP_STRING, null),
            )
        if (expected) {
            armTimeout(FORM_BACKSTOP_MS)
            return
        }
        val r = Runnable { if (!formVisible) settle() }
        grace = r
        main.postDelayed(r, LOAD_GRACE_MS)
    }

    /** The request is settled the moment the CMP is not showing anything. */
    override fun onCMPUIStatusChanged(status: DisplayInfo) {
        Log.i(TAG, "cmp ui: display=${status.displayStatus} regulation=${status.regulationShown}")
        if (status.displayStatus == DisplayStatus.VISIBLE) {
            formVisible = true
            cancel(timeout)
            timeout = null
            cancel(grace)
            grace = null
        } else {
            formVisible = false
            cancel(choiceSettle)
            choiceSettle = null
            formClosed()
        }
    }

    override fun onIABVendorConsentGiven(gdprData: GDPRData) {
        Log.i(TAG, "cmp iab consent: tc=${if (gdprData.tcString.isNullOrEmpty()) "empty" else "set"}")
        choiceMade()
    }

    override fun onNonIABVendorConsentGiven(nonIABData: NonIABData) {}

    /** Google's Additional Consent string carries AppLovin (provider 1301),
     * which Appodeal mediates; the AppLovin SDK reads it from storage, so
     * nothing is forwarded here. */
    override fun onGoogleVendorConsentGiven(acData: ACData) {}

    override fun onGoogleBasicConsentChange(consents: GoogleBasicConsents) {}

    override fun onReceiveUSRegulationsConsent(usRegulationData: USRegulationData) {
        val gpp = if (usRegulationData.gppString.isNullOrEmpty()) "empty" else "set"
        Log.i(TAG, "cmp us regulations consent: gpp=$gpp")
        choiceMade()
    }

    override fun onCCPAConsentGiven(consentString: String) {
        choiceMade()
    }

    /**
     * A choice landed while a form was up. The form normally reports its own
     * closing through onCMPUIStatusChanged; if it has not within a few
     * seconds the request settles anyway — the choice is made, so nothing can
     * stack on a form still being answered.
     */
    private fun choiceMade() {
        if (!formVisible || choiceSettle != null) return
        val r =
            Runnable {
                choiceSettle = null
                if (!formVisible) return@Runnable
                Log.i(TAG, "cmp form still reported visible after a choice; settling")
                formVisible = false
                formClosed()
            }
        choiceSettle = r
        main.postDelayed(r, CHOICE_SETTLE_MS)
    }

    /** An error is an answer too: no form will come. Ads stay off unless a
     * stored choice allows them (canRequestAds reads the IAB keys the CMP
     * persisted on an earlier launch). */
    override fun onCmpError(error: ChoiceError) {
        Log.w(TAG, "cmp error: $error")
        formClosed()
    }

    /** The user moved into another regulation's territory; the CMP shows its
     * form on the next request. What applies is re-read whenever
     * canRequestAds is asked. */
    override fun onUserMovedToOtherState() {}

    override fun onActionButtonClicked(actionButton: ActionButton) {}

    private companion object {
        const val TAG = "ads"
        const val OFFLINE_TIMEOUT_MS = 15_000L
        const val FORM_BACKSTOP_MS = 60_000L
        const val LOAD_GRACE_MS = 2_000L
        const val CHOICE_SETTLE_MS = 3_000L
        const val KEY_GDPR_APPLIES = "IABTCF_gdprApplies"
        const val KEY_TC_STRING = "IABTCF_TCString"
        const val KEY_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"
        const val KEY_GPP_STRING = "IABGPP_HDR_GppString"
    }
}

/** IABTCF_gdprApplies is an Int by the TCF spec; a CMP that wrote it as a
 * String or Boolean must not crash the read. */
private fun SharedPreferences.intOrNull(key: String): Int? =
    when (val v = all[key]) {
        is Int -> v
        is Long -> v.toInt()
        is String -> v.toIntOrNull()
        is Boolean -> if (v) 1 else 0
        else -> null
    }
