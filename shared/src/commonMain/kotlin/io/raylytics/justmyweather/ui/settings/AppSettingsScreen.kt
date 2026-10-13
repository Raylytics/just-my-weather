package io.raylytics.justmyweather.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.Platform
import io.raylytics.justmyweather.billing.RemoveAdsStatus
import io.raylytics.justmyweather.currentPlatform
import io.raylytics.justmyweather.resources.Res
import io.raylytics.justmyweather.resources.common_done
import io.raylytics.justmyweather.resources.common_places
import io.raylytics.justmyweather.resources.common_region
import io.raylytics.justmyweather.resources.common_region_units
import io.raylytics.justmyweather.resources.common_report_bug
import io.raylytics.justmyweather.resources.settings_about
import io.raylytics.justmyweather.resources.settings_ads
import io.raylytics.justmyweather.resources.settings_ads_removed
import io.raylytics.justmyweather.resources.settings_advanced
import io.raylytics.justmyweather.resources.settings_already_bought_restore_purchase
import io.raylytics.justmyweather.resources.settings_app_settings
import io.raylytics.justmyweather.resources.settings_banner_appears_once_app
import io.raylytics.justmyweather.resources.settings_change_what_agreed_about
import io.raylytics.justmyweather.resources.settings_checking_google_play
import io.raylytics.justmyweather.resources.settings_checking_google_play_ios
import io.raylytics.justmyweather.resources.settings_hands_each_new_reading
import io.raylytics.justmyweather.resources.settings_help
import io.raylytics.justmyweather.resources.settings_if_has_earned_place
import io.raylytics.justmyweather.resources.settings_if_has_earned_place_ios
import io.raylytics.justmyweather.resources.settings_location
import io.raylytics.justmyweather.resources.settings_no_accounts_what_leaves
import io.raylytics.justmyweather.resources.settings_no_ads_now
import io.raylytics.justmyweather.resources.settings_no_purchase_found_google
import io.raylytics.justmyweather.resources.settings_no_purchase_found_google_ios
import io.raylytics.justmyweather.resources.settings_one_time_purchase_no
import io.raylytics.justmyweather.resources.settings_opening_google_play
import io.raylytics.justmyweather.resources.settings_opening_google_play_ios
import io.raylytics.justmyweather.resources.settings_privacy_choices
import io.raylytics.justmyweather.resources.settings_privacy_policy
import io.raylytics.justmyweather.resources.settings_purchase_pending_banner
import io.raylytics.justmyweather.resources.settings_purchase_pending_banner_ios
import io.raylytics.justmyweather.resources.settings_rate_just_my_weather
import io.raylytics.justmyweather.resources.settings_remove_ads
import io.raylytics.justmyweather.resources.settings_remove_ads_2
import io.raylytics.justmyweather.resources.settings_send_gadgetbridge
import io.raylytics.justmyweather.resources.settings_something_wrong_email_developer
import io.raylytics.justmyweather.resources.settings_submit_idea_developer
import io.raylytics.justmyweather.resources.settings_thank_banner_gone
import io.raylytics.justmyweather.resources.settings_thank_banner_gone_ios
import io.raylytics.justmyweather.resources.settings_tile_alert_or
import io.raylytics.justmyweather.resources.settings_version
import io.raylytics.justmyweather.resources.settings_waiting_google_play_confirm
import io.raylytics.justmyweather.resources.settings_waiting_google_play_confirm_ios
import io.raylytics.justmyweather.resources.settings_weather_data_from_us
import io.raylytics.justmyweather.resources.settings_where_law_asks_first
import io.raylytics.justmyweather.ui.theme.textButtonColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings that are about the app, not the canvas. Customize is for what the
 * glance shows and how it looks; anything else — handing readings to another
 * app, getting in touch with the developer, what the app is — belongs here, so Customize stays
 * a page about the glance.
 */
@Composable
fun AppSettingsScreen(
    gadgetbridgeEnabled: Boolean,
    onSetGadgetbridgeEnabled: (Boolean) -> Unit,
    adsRemoved: Boolean,
    /** Whether Google's consent SDK says ads may run here. When not, there is
     * no banner, so no purchase to offer. */
    canShowAds: Boolean,
    /** Where a consent form applies, the user must be able to reopen it. */
    privacyOptionsRequired: Boolean,
    onPrivacyChoices: () -> Unit,
    /** "Showing Louisville, KY · 3 saved" — the Places row's second line. */
    placesSummary: String,
    onPlaces: () -> Unit,
    /** "United Kingdom · °C, mph" — the Region & units row's second line. */
    regionSummary: String,
    onRegion: () -> Unit,
    /** Play's localised price ("$0.99"), or null while Play has not answered. */
    removeAdsPrice: String?,
    removeAdsStatus: RemoveAdsStatus,
    onBuyRemoveAds: () -> Unit,
    onRestorePurchases: () -> Unit,
    onReportBug: () -> Unit,
    onSubmitIdea: () -> Unit,
    /** Opens the Play listing, where the rating lives. */
    onRateApp: () -> Unit,
    /** "0.2.0 (build 2)" — the line a bug reply would otherwise ask for. */
    version: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.settings_app_settings), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDone) { Text(stringResource(Res.string.common_done)) }
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // The glance's place name opens the same screen; this is the
                // entry for someone who looks for locations among the settings.
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(stringResource(Res.string.settings_location), style = MaterialTheme.typography.labelMedium)
                    AboutLink(
                        title = stringResource(Res.string.common_places),
                        detail = placesSummary,
                        onClick = onPlaces,
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(stringResource(Res.string.common_region), style = MaterialTheme.typography.labelMedium)
                    AboutLink(
                        title = stringResource(Res.string.common_region_units),
                        detail = regionSummary,
                        onClick = onRegion,
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                AdsSection(
                    adsRemoved = adsRemoved,
                    canShowAds = canShowAds,
                    privacyOptionsRequired = privacyOptionsRequired,
                    onPrivacyChoices = onPrivacyChoices,
                    price = removeAdsPrice,
                    status = removeAdsStatus,
                    onBuy = onBuyRemoveAds,
                    onRestore = onRestorePurchases,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                HelpSection(onReportBug = onReportBug, onSubmitIdea = onSubmitIdea, onRateApp = onRateApp)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                // Gadgetbridge is an Android app; the hand-off has no iOS
                // counterpart (docs/PARITY.md, "Dropped").
                if (currentPlatform == Platform.ANDROID) {
                    AdvancedSection(
                        gadgetbridgeEnabled = gadgetbridgeEnabled,
                        onSetGadgetbridgeEnabled = onSetGadgetbridgeEnabled,
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                AboutSection(version = version)
            }
        }
    }
}

/**
 * The banner and the one-time purchase that removes it. The price is Play's
 * own, in the user's currency, never typed here; until Play answers the row
 * says so rather than guessing. Restore is for a new phone or a reinstall.
 */
@Composable
private fun AdsSection(
    adsRemoved: Boolean,
    canShowAds: Boolean,
    privacyOptionsRequired: Boolean,
    onPrivacyChoices: () -> Unit,
    price: String?,
    status: RemoveAdsStatus,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(Res.string.settings_ads), style = MaterialTheme.typography.labelMedium)
        if (adsRemoved) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(Res.string.settings_ads_removed), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text =
                        stringResource(
                            onStore(Res.string.settings_thank_banner_gone, Res.string.settings_thank_banner_gone_ios),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        // Wherever a consent form applies, the choice can be changed here —
        // Google requires the entry, and it belongs beside the ads it governs.
        if (privacyOptionsRequired) {
            AboutLink(
                title = stringResource(Res.string.settings_privacy_choices),
                detail = stringResource(Res.string.settings_change_what_agreed_about),
                onClick = onPrivacyChoices,
            )
        }
        if (!canShowAds) {
            // Nothing to remove: selling Remove Ads where no banner is shown
            // would be selling nothing.
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(Res.string.settings_no_ads_now), style = MaterialTheme.typography.bodyLarge)
                Text(
                    // Consent wording only where a form applies; a first launch
                    // offline, anywhere, just hasn't reached Google yet (roborev 5436).
                    text =
                        if (privacyOptionsRequired) {
                            stringResource(Res.string.settings_where_law_asks_first)
                        } else {
                            stringResource(Res.string.settings_banner_appears_once_app)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        val busy = status == RemoveAdsStatus.Busy || status == RemoveAdsStatus.Restoring
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy, onClick = onBuy)
                    .padding(vertical = 8.dp)
                    .testTag("remove-ads"),
        ) {
            Text(
                text =
                    if (price != null) {
                        stringResource(Res.string.settings_remove_ads, price)
                    } else {
                        stringResource(Res.string.settings_remove_ads_2)
                    },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    if (price != null) {
                        stringResource(Res.string.settings_one_time_purchase_no)
                    } else {
                        stringResource(
                            onStore(
                                Res.string.settings_waiting_google_play_confirm,
                                Res.string.settings_waiting_google_play_confirm_ios,
                            ),
                        )
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val note =
            when (status) {
                RemoveAdsStatus.Idle -> null
                RemoveAdsStatus.Busy ->
                    stringResource(
                        onStore(Res.string.settings_opening_google_play, Res.string.settings_opening_google_play_ios),
                    )
                RemoveAdsStatus.Restoring ->
                    stringResource(
                        onStore(Res.string.settings_checking_google_play, Res.string.settings_checking_google_play_ios),
                    )
                RemoveAdsStatus.Pending ->
                    stringResource(
                        onStore(
                            Res.string.settings_purchase_pending_banner,
                            Res.string.settings_purchase_pending_banner_ios,
                        ),
                    )
                is RemoveAdsStatus.Failed -> status.message
                RemoveAdsStatus.NothingToRestore ->
                    stringResource(
                        onStore(
                            Res.string.settings_no_purchase_found_google,
                            Res.string.settings_no_purchase_found_google_ios,
                        ),
                    )
            }
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (status is RemoveAdsStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        TextButton(
            onClick = onRestore,
            enabled = !busy,
            colors = textButtonColors(),
            modifier = Modifier.testTag("restore-purchase"),
        ) {
            Text(stringResource(Res.string.settings_already_bought_restore_purchase))
        }
    }
}

/**
 * Settings most people never need, folded away behind a switch so the page
 * stays short. The switch only shows or hides them — it changes nothing on
 * its own. It opens itself whenever something inside is on, so a setting
 * that is doing something is never hidden.
 */
@Composable
private fun AdvancedSection(
    gadgetbridgeEnabled: Boolean,
    onSetGadgetbridgeEnabled: (Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Keyed on the value, not read once: the stored setting arrives a frame
    // after the screen does, so an initial value would always read "off".
    LaunchedEffect(gadgetbridgeEnabled) { if (gadgetbridgeEnabled) expanded = true }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.settings_advanced), style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = expanded,
                onCheckedChange = { expanded = it },
                modifier = Modifier.testTag("advanced-toggle"),
            )
        }
        if (expanded) {
            GadgetbridgeToggle(enabled = gadgetbridgeEnabled, onChange = onSetGadgetbridgeEnabled)
        }
    }
}

/**
 * Opt-in hand-off of each reading to Gadgetbridge, which relays it to a paired
 * watch. Off by default: it sends data to another app, so it stays something
 * you go and switch on rather than something you discover already running.
 */
@Composable
private fun GadgetbridgeToggle(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.settings_send_gadgetbridge), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                modifier = Modifier.testTag("gadgetbridge-toggle"),
            )
        }
        Text(
            text = stringResource(Res.string.settings_hands_each_new_reading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The ways to reach the developer — the bug-report form and the idea form —
 * and the way to the store page for a rating. The idea form is also offered
 * at the bottom of Customize, where someone notices the option they wanted
 * is missing; here is where someone comes looking for it on purpose. The
 * rating row asks once and plainly, here where someone has come looking,
 * rather than interrupting the glance with a pop-up. */
@Composable
private fun HelpSection(
    onReportBug: () -> Unit,
    onSubmitIdea: () -> Unit,
    onRateApp: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(Res.string.settings_help), style = MaterialTheme.typography.labelMedium)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReportBug)
                    .padding(vertical = 8.dp),
        ) {
            Text(stringResource(Res.string.common_report_bug), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(Res.string.settings_something_wrong_email_developer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSubmitIdea)
                    .padding(vertical = 8.dp)
                    .testTag("submit-idea"),
        ) {
            Text(stringResource(Res.string.settings_submit_idea_developer), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(Res.string.settings_tile_alert_or),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRateApp)
                    .padding(vertical = 8.dp)
                    .testTag("rate-app"),
        ) {
            Text(stringResource(Res.string.settings_rate_just_my_weather), style = MaterialTheme.typography.bodyLarge)
            Text(
                text =
                    stringResource(
                        onStore(Res.string.settings_if_has_earned_place, Res.string.settings_if_has_earned_place_ios),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Where the policy lives. It is also the URL in the Play listing
 * (store-assets/play-store-listing.md); change both together. */
const val PRIVACY_POLICY_URL = "https://raylytics.io/justmyweather/privacy"

/** The iOS app's own policy (store-assets/privacy-policy-ios.md): App Store,
 * Apple Account and Apple's tracking prompt where Android's says Google. */
const val PRIVACY_POLICY_URL_IOS = "https://raylytics.io/justmyweather/privacy-ios"

/**
 * What the app is and whose data it shows. The attribution is not a courtesy:
 * MET Norway's and GeoNames' CC BY 4.0 licences ask for it wherever their data
 * appears, and a store build is the place a user would look for it.
 */
@Composable
private fun AboutSection(version: String) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(Res.string.settings_about), style = MaterialTheme.typography.labelMedium)
        Text(
            // The screen already says which app this is; the line names what
            // a bug reply would ask for.
            text = stringResource(Res.string.settings_version, version),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 8.dp).testTag("about-version"),
        )
        AboutLink(
            title = stringResource(Res.string.settings_privacy_policy),
            detail = stringResource(Res.string.settings_no_accounts_what_leaves),
            onClick = {
                uriHandler.openUri(if (currentPlatform == Platform.IOS) PRIVACY_POLICY_URL_IOS else PRIVACY_POLICY_URL)
            },
        )
        Text(
            text = stringResource(Res.string.settings_weather_data_from_us),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AboutLink(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The store's own name in a sentence: Google Play on Android, the App Store
 * on iOS — each wording is its own catalog entry. */
private fun onStore(play: StringResource, appStore: StringResource): StringResource =
    if (currentPlatform == Platform.IOS) appStore else play
