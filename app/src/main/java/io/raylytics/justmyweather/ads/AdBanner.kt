package io.raylytics.justmyweather.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appodeal.ads.Appodeal

/*
 * The only file that draws an ad. The glance puts this at its foot when the
 * user has not bought Remove Ads and the consent gate says ads may run, and
 * nowhere else: a settings page or a sheet carries no banner. Everything
 * network-specific lives in here and in AppodealAds, so swapping the network,
 * or removing ads altogether, touches two files and one call site.
 *
 * google-ads-exit Stage B: Appodeal's one shared banner view (320x50,
 * stretched to the width by smart banners, matching iOS), shown only after
 * the SDK's init callback.
 */

/**
 * The banner's 50dp are reserved as soon as the composable is placed, before
 * the SDK has initialized or the ad has loaded: the glance above must not
 * jump when the ad lands a second after launch (it did with AdMob, and a tap
 * aimed at the action bar in that second missed). Shown when it enters the
 * composition and hidden when it leaves.
 */
@Composable
fun AdBanner(
    ads: AppodealAds,
    modifier: Modifier = Modifier,
) {
    val ready by ads.initialized.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxWidth().height(BANNER_HEIGHT_DP.dp).testTag("ad-banner")) {
        val activity = context.findActivity()
        if (ready && activity != null) {
            val banner = remember { Appodeal.getBannerView(context.applicationContext) }
            AndroidView(
                factory = { host ->
                    FrameLayout(host).apply {
                        (banner.parent as? ViewGroup)?.removeView(banner)
                        addView(banner, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(banner, activity) {
                Appodeal.show(activity, Appodeal.BANNER_VIEW)
                onDispose {
                    Appodeal.hide(activity, Appodeal.BANNER_VIEW)
                    (banner.parent as? ViewGroup)?.removeView(banner)
                }
            }
        }
    }
}

/** Appodeal's phone banner height; smart banners stretch only the width. */
private const val BANNER_HEIGHT_DP = 50

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
