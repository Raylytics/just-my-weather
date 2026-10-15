package io.raylytics.justmyweather.ads

/**
 * The rules the banner is served under, as plain values so they test on the
 * JVM and the privacy policy's promises have one place to point at:
 *
 * - personalization is the user's consent, applied by the SDK: a request
 *   carries no override of its own, so Google reads the choice made in its
 *   consent form (ads/AdsConsent) and serves personalized, non-personalized
 *   or limited ads accordingly — open-frame's policy, and the privacy
 *   policy's (store-assets/privacy-policy.md, "Ads and purchases");
 * - a debug build never serves a real ad unit — Google's sample units show
 *   labelled test ads and earn nothing, and clicking one's own live ads is
 *   how AdMob accounts get suspended.
 *
 * Nothing about the place, the weather or the rules ever reaches a request:
 * AdRequests builds a bare one.
 */
object AdPolicy {
    /** Google's sample app id and anchored-adaptive-banner unit. */
    const val SAMPLE_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val SAMPLE_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    private const val SAMPLE_PUBLISHER = "ca-app-pub-3940256099942544"

    /** The unit this build serves: the configured one in release, Google's
     * sample in debug regardless of what was configured. */
    fun bannerUnitId(debugBuild: Boolean, configured: String): String =
        if (debugBuild) SAMPLE_BANNER_UNIT_ID else configured

    /** Whether an id is one of Google's samples — a release cut should
     * refuse to ship one (scripts/android/release-internal.sh). */
    fun isSample(id: String): Boolean = id.startsWith(SAMPLE_PUBLISHER)
}
