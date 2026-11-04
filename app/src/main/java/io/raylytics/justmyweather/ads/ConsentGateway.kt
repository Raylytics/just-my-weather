package io.raylytics.justmyweather.ads

import android.app.Activity

/*
 * The seam between the app and the consent management platform (CMP), the
 * same shape as billing/BillingGateway: a few plain answers, so the decisions
 * around them (AdsConsent) test on the JVM against a fake, and the CMP's
 * gateway is the only file that imports its SDK. Google's UMP gateway was
 * removed in the google-ads-exit epic (Stage A); until the CMP's gateway
 * lands (Stage B), NoConsentGateway stands in and never allows ads.
 */
interface ConsentGateway {
    /** Whether ads may be requested now, from what the platform last said —
     * persisted by it across launches, so a returning user's choice holds
     * from the first frame. False until it has said so at least once. */
    fun canRequestAds(): Boolean

    /** Whether the user must be offered a way to change their choice (a
     * "Privacy choices" entry): true where a consent form applies. */
    fun privacyOptionsRequired(): Boolean

    /** Ask the platform, once per launch, whether consent is needed where
     * the user is, and show its form if it is. [onDone] runs when that
     * settles — a choice made, no form needed, or an error — never more than
     * once. */
    fun update(activity: Activity, onDone: () -> Unit)

    /** Reopen the platform's form so the user can change their choice. */
    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit)
}
