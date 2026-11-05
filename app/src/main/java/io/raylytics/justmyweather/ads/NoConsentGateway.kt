package io.raylytics.justmyweather.ads

import android.app.Activity

/**
 * The [ConsentGateway] for a build without ad ids (a clone with no
 * app/ads.properties, or one still carrying the example's placeholders): no
 * consent platform runs, so it never allows ads, needs no privacy options
 * and settles every request at once, and the launch sequence that waits on
 * consent (the location prompt) runs unchanged. A store build never uses
 * it: scripts/android/release-internal.sh refuses to cut without the ids.
 */
class NoConsentGateway : ConsentGateway {
    override fun canRequestAds(): Boolean = false

    override fun privacyOptionsRequired(): Boolean = false

    override fun update(activity: Activity, onDone: () -> Unit) = onDone()

    override fun showPrivacyOptions(activity: Activity, onDone: () -> Unit) = onDone()
}
