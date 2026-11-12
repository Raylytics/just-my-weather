package io.raylytics.justmyweather.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/*
 * The only file that draws an ad. The glance puts this at its foot when the
 * user has not bought Remove Ads, and nowhere else: a settings page or a
 * sheet carries no banner. Everything network-specific lives in here, so
 * swapping the network, or removing ads altogether, touches this file and
 * one call site.
 *
 * google-ads-exit Stage A: the Google Mobile Ads banner is gone and the
 * Appodeal banner has not landed on Android yet (Stage B, iOS first), so this draws nothing.
 * The composable keeps its place and its test tag so the glance's layout and
 * the UI flows do not move twice. App.kt composes it only while the consent
 * gate says ads may run, which NoConsentGateway never does.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().testTag("ad-banner"))
}
