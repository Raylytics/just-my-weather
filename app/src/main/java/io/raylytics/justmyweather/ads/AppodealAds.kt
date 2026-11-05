package io.raylytics.justmyweather.ads

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import com.appodeal.ads.Appodeal
import com.appodeal.ads.initializing.ApdInitializationError
import io.raylytics.justmyweather.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Appodeal mediation (google-ads-exit Stage B, on Appodeal's own network
 * accounts): started once, by AdsConsent's hook, only after the InMobi CMP
 * allows ads and only for a user who has not bought Remove Ads. [initialized]
 * turns true from the SDK's init callback, and [AdBanner] loads nothing
 * before that — the same order as iOS (AdsService.swift).
 *
 * Banner type only. Debug builds serve Appodeal's test creatives (100% fill,
 * never a real ad) with verbose logs that name the networks answering, for
 * Stage C. Consent reaches the networks through the IAB strings the CMP
 * stored; nothing here sets a personalization flag.
 */
class AppodealAds(
    context: Context,
    private val appKey: String,
) {
    private val appContext = context.applicationContext
    private val ready = MutableStateFlow(false)
    private var started = false

    /** True once the SDK has initialized and a banner may load. */
    val initialized: StateFlow<Boolean> = ready.asStateFlow()

    @MainThread
    fun start() {
        if (started || appKey.isEmpty()) return
        started = true
        Log.i(TAG, "appodeal: initializing (banner)")
        Appodeal.setTesting(BuildConfig.DEBUG)
        if (BuildConfig.DEBUG) Appodeal.setLogLevel(com.appodeal.ads.utils.Log.LogLevel.verbose)
        Appodeal.setSmartBanners(true)
        Appodeal.initialize(appContext, appKey, Appodeal.BANNER) { errors ->
            errors?.forEach { Log.w(TAG, "appodeal init: $it") }
            val critical = errors.orEmpty().any { it is ApdInitializationError.Critical }
            Log.i(TAG, "appodeal: initialized (critical error: $critical)")
            ready.value = !critical
        }
    }

    private companion object {
        const val TAG = "ads"
    }
}
