package io.raylytics.justmyweather.ads

import android.app.Activity

/**
 * The [ConsentGateway] in place while no consent platform is integrated:
 * google-ads-exit Stage A removed Google's UMP, and the third-party CMP
 * arrives in Stage B. It never allows ads, needs no privacy options and
 * settles every request at once, so the launch sequence that waits on
 * consent (the location prompt) runs unchanged.
 */
class NoConsentGateway : ConsentGateway {
    override fun canRequestAds(): Boolean = false

    override fun privacyOptionsRequired(): Boolean = false

    override fun update(activity: Activity, onDone: () -> Unit) = onDone()

    override fun showPrivacyOptions(activity: Activity, onDone: () -> Unit) = onDone()
}
