# Privacy Policy — Just My Weather (iOS)

_Last updated: 2026-10-13_

Just My Weather is a free iPhone weather app published by Raylytics, LLC. It
shows the weather for a place you choose, lets you build your own view of
it, and sends you notifications for conditions you define. This policy
explains what data the app accesses and what happens to it.

Canonical URL: https://raylytics.io/justmyweather/privacy-ios

(The Android app has its own policy, at https://raylytics.io/justmyweather/privacy.
The two apps handle data the same way; this one describes the iPhone.)

## Summary

**Just My Weather does not sell your personal information, and the developer
collects none unless you choose to email a bug report or an idea.** It has no
accounts and no backend server of its own, and the developer runs no
analytics, telemetry, or automatic crash reporting (the ad library's own
measurement is described under "Ads and purchases").

The app is free and shows one banner ad, served by **Google AdMob**, at the
bottom of the screen. AdMob receives standard advertising signals from your
device to serve that ad (see "Ads and purchases"). A one-time $0.99 purchase
removes the ad. In the United Kingdom, the European Economic Area and
Switzerland the app asks first, using Google's consent form, and shows no ad
until you have chosen; you can change your choice any time in App settings →
Privacy choices. The app may also show Apple's "Allow to track" prompt; the
ad receives your device's advertising identifier only if you allow it.

Everything you set up — which modules show, how they are arranged, your theme,
your saved places, your alert rules — stays on your iPhone (and in your own
iCloud or computer backup, if you use one). The only data the app itself
sends off your device is the latitude and longitude of the place you are
viewing, or that your alerts watch, sent to public weather services so the
app can fetch the forecast; separately, the ad library sends AdMob what it
needs to show the banner (where one is shown); each time the app opens it
asks Google's consent service whether your consent is needed (see "Ads and
purchases"), unless you have bought ad removal; and Apple's App Store handles
the ad-removal purchase and its restore check. A bug report or idea you send
from the app goes from your own email app, and you see every line of it
before you send it.

## What the app accesses

**Your device's approximate location (optional).** If you allow location
access, the app reads your iPhone's approximate position to show the weather
where you are. It asks for access only while you are using the app, and it
asks iOS for approximate (reduced-accuracy) positions only, never a precise
one. You can decline and pick a place by name instead.

Alert checks run in the background, when iOS allows them. They watch the
place you chose or, if you follow the device, the last position the app knew.
The app has no location access in the background, so it uses the last
position from when the app was open.

**Places you choose.** You can search a list of about 60,000 towns and
cities worldwide that is bundled inside the app, or type coordinates. The
search runs entirely on your iPhone; nothing you type is sent anywhere.

**Your region.** To show the weather in your units and your date and time
format, the app works out which country you are in. It reads the country of
your App Store account, or the country of your approximate location or of the
place you are viewing, matched against the bundled place list, or the Region
in your iPhone's settings, or your iPhone's time zone, matched against the
same list; you can also set your region yourself in App settings. Whether an
ad may be shown does not depend on any of this: Google's consent service
decides that (see "Ads and purchases"). All of this happens on your iPhone,
and the country is not sent to the developer or to any weather or ad service.

## Where your data goes

To fetch the weather, the app sends the coordinates of the selected place to:

- **The US National Weather Service** (api.weather.gov), a US federal agency,
  for current conditions, the hourly and seven-day forecasts, and active
  weather alerts. As the NWS requires, each request identifies the app and
  includes the developer's contact email; it does not include anything that
  identifies you.
- **The Norwegian Meteorological Institute** (api.met.no, "MET Norway"), a
  Norwegian government agency, for any place outside the National Weather
  Service's coverage (the whole forecast for that place), and for days eight
  and nine of the daily forecast when you have set it to show more than
  seven days. Weather from MET Norway is marked as such in the app. As MET
  requires, each request identifies the app and includes the developer's
  contact email; it does not include anything that identifies you.

Those services' own privacy practices apply to the requests they receive. The
app sends them coordinates and nothing else: no account, no device identifier,
no advertising identifier.

## Ads and purchases

Unless you have bought ad removal, the app shows **one banner ad** at the
bottom of the screen. Each time the app opens it asks **Google's consent
service** (the User Messaging Platform) whether your consent is needed where
you are; Google answers from the request (your IP address, as for any
connection), and stores your answer on your device. Where consent is needed —
the United Kingdom, the European Economic Area and Switzerland — the app shows
Google's consent form and starts the ad SDK only after you have chosen; if you
decline, Google may still show a limited ad that uses no cookies or advertising
identifiers. You can change your choice any time in App settings → Privacy
choices. The ad is served by **Google AdMob**, a Google service.

**Tracking and personalized ads.** Once Google's consent service allows ads,
the app may show Apple's "Allow to track" prompt, after a short explanation
from Google. It is never shown where ads won't run. If you allow it, AdMob can
use your device's advertising identifier to personalize ads; if you don't, it
never receives that identifier. You can change this at any time in iOS
Settings → Privacy & Security → Tracking. Ads may be personalized only where
you have allowed tracking and, where consent is required, consented;
otherwise Google serves non-personalized or limited ads. To serve, measure
and — where allowed — personalize that ad, the Google Mobile Ads SDK built
into the app may collect:

- your device's **advertising identifier**, only if you allow tracking;
- your **IP address**, from which an approximate location can be inferred;
- **ad interactions** — whether an ad loaded, was shown, or was tapped;
- **diagnostic information** about the SDK and how ads perform.

That data goes to Google, not to the developer, and Google's processing is
governed by the [Google Privacy Policy](https://policies.google.com/privacy)
and [how Google uses information from apps that use its services](https://policies.google.com/technologies/partner-sites).
The ad is never given your saved places, your alert rules, or the weather
you are looking at.

**Remove ads ($0.99, one-time).** The purchase is handled by **Apple** through
the App Store; the developer never sees your payment details. The app keeps
only a note on your device that ads are removed, and checks the App Store to
restore the purchase on a new device signed in to the same Apple Account.
Once ads are removed, the app stops requesting ads and stops asking Google's
consent service.

All of the app's network traffic uses HTTPS.

## What stays on your iPhone

The app stores the following on your device, in its private app storage:

- your view configuration (modules, sizes, order, labels, density, theme);
- your saved places and the last location the app used;
- your alert rules, whether each has fired, and your quiet-hours setting;
- whether you have bought ad removal, and your choice in Google's consent form
  (stored by Google's consent SDK on your device);
- your region and unit settings, and the country the app last worked out;
- a short-lived cache of the most recent weather so the app opens instantly;
- a small cache of up to 32 recently looked-up approximate areas (coordinates
  rounded to about 1 km) with the weather-station grid each one maps to, so
  that lookup is not repeated. The oldest lookups are replaced first, and the
  cache is deleted along with the rest of the app's data;
- a small cache of recent MET Norway forecast responses, kept as MET Norway
  asks, so an unchanged forecast is not downloaded twice.

Deleting the app deletes all of it. If you use iCloud Backup or back up your
iPhone to a computer, the backup may include the app's stored data — your
settings (including your region and your choice in Google's consent form),
saved places, last position, the country the app last worked out, and the
area cache — under your control; the developer has no access to that backup.

## Notifications

When you have alert rules (or safety alerts) turned on, the app fetches the
current conditions, forecast, and active NWS alerts for the place your alerts
watch, and evaluates your rules on your iPhone. In the background this
happens when iOS allows it, which can be later than the schedule you chose,
and not at all while Background App Refresh is off for the app; opening the
app checks at once. Notifications are generated and delivered locally on
your iPhone. No notification service outside your device is involved.

## Bug reports and ideas you send

App settings has "Report a bug", and the foot of Customize offers to submit an
idea. Either one opens a form in the app. Nothing leaves your iPhone until you
tap "Email bug report" or "Email idea", and even then the app only hands a
drafted email to your own email app, addressed to dev@raylytics.io, for you to
review and send yourself. The app never sends it on its own.

- **A bug report** includes what you write, plus a diagnostics block shown on
  the form before you send: the app version, your iOS version and iPhone
  model, a few app settings (whether location access is granted, how many
  modules are shown, density, theme, and whether the weather loaded, with a
  fixed description of the error if it did not), the screen you sent it from,
  the time, and the most recent log lines written by the app's process (up
  to 50, read from the iPhone's own log for this app). It does not include
  your location, saved places, or alert rules.
- **An idea** includes what you write, the app version, your iOS version and
  iPhone model, and which screen you sent it from.

Because it is an ordinary email, the developer also receives your email
address and whatever your email provider adds. The developer uses these emails
only to fix bugs, consider ideas, and reply to you; they are not shared with
anyone or used for marketing. To have an email you sent deleted, ask at the
same address.

## Permissions

iOS asks you before the app gets any of these, and you can change each one
later in iOS Settings → Just My Weather.

- **Location (while using the app, approximate)** — optional; to show the
  weather where you are.
- **Notifications** — to deliver the alerts you set up. Asked when you first
  open Alerts.
- **Tracking** — optional; asked through Apple's "Allow to track" prompt,
  lets ads be personalized as described under "Ads and purchases".
- **Background App Refresh** — on by default in iOS; lets the app check your
  alert rules while it is closed. Turning it off stops background checks.

## What the app does NOT do

- No accounts or sign-in.
- No personalized ads without a choice: ads are personalized only where you
  allowed tracking and, where required, consented (see "Ads and purchases").
- No analytics, telemetry, or automatic crash reporting run by the developer.
  The AdMob SDK's own ad measurement and diagnostics are described under
  "Ads and purchases".
- No selling of personal information.
- No precise location. The positions the app keeps are the ones listed above;
  they leave your iPhone only as forecast coordinates sent to the weather
  services described here, or inside your own backup if you use one; never to
  the developer. (AdMob may infer an approximate location from your IP
  address, as any website can.)

## Children

The app is intended for users 13 and older and is not directed at children.
It does not knowingly collect any data from children.

## Changes

If this policy changes, the updated version will be published at the same URL
with a new "Last updated" date.

## Contact

Just My Weather is published by Raylytics, LLC. Questions about this policy or
how the app handles data: [dev@raylytics.io](mailto:dev@raylytics.io). This is
the same address the app's support entry uses.
