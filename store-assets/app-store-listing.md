# App Store Connect listing — Just My Weather (iOS)

Living reference for every field of the App Store listing. **Edit it here
first, then paste into App Store Connect**, in the same change that alters
the app. Mirror for Android: `play-store-listing.md`. The bootstrap and the
release procedure are in `docs/APP_STORE.md`.

Status 2026-10-13: draft. The App Store Connect record does not exist yet
(just-my-weather-k7j), so nothing here has been entered. Fields marked
**TBD** need a decision or a live URL before the first submission.

Everything below is documentation except the fenced copy blocks, which are
what gets pasted. App Store Connect fields take plain text: no Markdown.

## How the fields behave

- **App-level, set once** (App Information, Age Rating, App Privacy,
  Pricing and Availability): rarely change. Re-confirm when App Store Connect
  asks during a release.
- **Listing copy** (name, subtitle, promotional text, description,
  keywords, URLs): the name and subtitle change only with a new version;
  promotional text can change at any time without one.
- **Per release** (What's New, build, screenshots): see "Per-release
  fields".

## Differences from the Play listing, on purpose

- The store and the account are Apple's: "App Store", "Apple Account".
- No Gadgetbridge and no widgets: neither exists on iOS (docs/PARITY.md).
- Alerts are described as best effort in the background, because iOS
  decides when the check runs (plan decision 2). The app says the same on the
  Alerts screen.
- The ads line describes consent and Apple's tracking prompt. It does not
  say ads are "requested as non-personalized": that stopped being true on
  both platforms with commit da74242 (the Play description still says it,
  flagged in just-my-weather-vg0).

---

## App Information (app-level)

| Field | Value |
|---|---|
| Name | see "App Name" |
| Bundle ID | `io.raylytics.justmyweather` (same as Android) |
| SKU | `justmyweather-ios` |
| Primary Language | English (U.S.) |
| Primary Category | **Weather** |
| Secondary Category | **TBD**. Utilities is the obvious candidate; leave empty if unsure. |
| Content Rights | Does not contain, show, or access third-party content: **No**. The weather data is public government data the app fetches and displays itself; there is no third-party media. |

### Age Rating

Every content item **None** / **No**, except:

| Question | Answer |
|---|---|
| Advertising | **Yes**. The app shows an AdMob banner to anyone who has not bought Remove Ads. Leaving it unset halted open-frame's 1.5.1 review (2026-10-10); Apple's scan finds the SDK. |
| Unrestricted web access | No |
| Gambling and contests | No |
| User-generated content | No |
| Medical or treatment information | None |

Expected result: **4+**. Confirm in App Store Connect; Apple's questionnaire
changes, so answer any new question honestly and record it here.

---

## Listing copy

### App Name (30 chars max)

```
Just My Weather
```

(15 chars.) The same name as on Play. If App Store Connect reports it taken,
the fallback is `Just My Weather – Forecast` (just-my-weather-k7j).

### Subtitle (30 chars max)

```
Modular, custom weather view
```

(28 chars.) Carries the two search targets from `ASO_NOTES.md`
("modular weather app", "custom weather"). App Store search indexes the
name, subtitle and keywords together, so these words are not repeated in the
keyword field. Reserves: `Custom weather, your alerts` (27),
`Your weather, your own alerts` (29).

### Promotional Text (170 chars max, editable without a new build)

```
Your own weather view: pick the numbers, size the tiles, set alerts for the conditions you care about. Free, with one small banner.
```

(131 chars.)

### Description (4000 chars max)

Adapted from `full-description.txt` (the Play description) for iOS. Paste
verbatim. Plain text; the ALLCAPS lines are the headers.

```
Just My Weather is a modular weather app: a custom weather view built from the data points you care about, arranged how you want, with alerts on exactly the conditions you choose. Nothing else.

Most weather apps decide for you: ten-day forecasts, radar, air quality, pollen, UV, hourly scrubbers, and then they bury the one number you opened the app to check. Just My Weather inverts that. The default is quiet and sparse. The power is building your own custom weather view and your own alerts, and editing everything else away.

A MODULAR, CUSTOM WEATHER VIEW

• The glance is a grid of modules. Choose which data points show: temperature, feels like, conditions, humidity, wind, pressure, dew point, visibility, sun times, hourly forecast, daily forecast, and more.
• Size each module, wide and tall. Size is prominence: the thing you care about most gets the biggest tile.
• Touch and hold to arrange the grid in place, the way you arrange your Home Screen. Drag a corner to resize.
• Pick a density from spacious to compact. Theme it: light, dark, or system; your accent color; your typeface.
• Tap a tile for the details behind it.

YOUR OWN ALERTS

• Rule-based alerts on current conditions and the forecast: "overnight low below 35°", "chance of rain above 50% within 12 hours", "wind over 25 mph".
• Quiet by default. A rule fires once when the condition starts, not every hour it persists.
• Optional quiet hours. Official weather warnings in the US, if you want them.
• Evaluated and delivered on your iPhone. No account, no server.
• In the background, iOS decides when the app may check, so an alert can arrive late. Opening the app checks at once.

FORECASTS FROM THE SOURCE

• Current conditions, hourly, and daily forecasts from the US National Weather Service in the US, and from MET Norway everywhere else. No middleman.
• The daily view can reach nine days, the last two from MET Norway in the US, clearly marked.
• Units, dates and the clock follow your region automatically, or set them yourself.
• Saved places: search a bundled offline list of about 60,000 towns and cities worldwide, or enter coordinates. No geocoding service sees what you type.

FREE, WITH ONE SMALL BANNER

• Just My Weather is free. A single banner ad sits at the bottom of the screen, out of the way of your weather.
• A one-time $0.99 purchase removes it for good, on every device signed in to your Apple Account.

NO ACCOUNT, YOUR DATA STAYS PUT

• No sign-up and no analytics of our own. Your view, places, and alert rules stay on your iPhone.
• Location is approximate and optional. The weather services receive only the coordinates of the place you are looking at.
• The banner is served by Google AdMob, which measures its own ads. Ads are personalized only if you allow tracking and, where the law asks, consent.

If you have been looking for a customizable weather app that shows only what you ask for, this is it.

Weather from the US National Weather Service in the United States and from MET Norway elsewhere. Official weather warnings are available in the US only.
```

(3073 chars, measured with `wc -m`.)

### Keywords (100 chars max, comma-separated, no spaces)

```
forecast,alert,rain,temperature,wind,hourly,daily,minimal,simple,nws,notification,customizable,frost
```

(100 chars.) Strategy, following open-frame's: never repeat words already in
the name or subtitle ("just", "my", "weather", "modular", "custom", "view"),
since Apple already matches them; cover what people type instead of
"weather" (forecast, rain, temperature, wind) and the app's distinctive
features (alert, notification, frost). Revisit once App Store Connect shows
search data.

### URLs

| Field | Value |
|---|---|
| Privacy Policy URL | **TBD**: planned `https://raylytics.io/justmyweather/privacy-ios`, rendered from `store-assets/privacy-policy-ios.md` in the `raylytics-site` repo (just-my-weather-kvr). The app already links this exact URL from App Settings → About (`PRIVACY_POLICY_URL_IOS`), so it must be live before review. |
| Support URL (required) | **TBD**. It must be a live page with a way to reach us. Candidate: a `https://raylytics.io/justmyweather/support` page giving `dev@raylytics.io`; whether such a page exists is unverified. |
| Marketing URL (optional) | **TBD**. Candidate: `https://raylytics.io/justmyweather` once a landing page exists; until then leave empty. |

### Copyright

```
© 2026 Raylytics, LLC
```

---

## App Privacy

Entered under App Privacy, with the privacy policy URL above. Do not fill it
from this file: take the answers from the archive's privacy report, as
`docs/APP_STORE.md` "Privacy labels and the policy" explains (the Google
Mobile Ads and UMP SDKs declare what they collect in their own manifests,
and the report merges them). Record the published answers here once entered.

The app's own code collects nothing: it sends forecast coordinates to NWS
and MET Norway and keeps everything else on the device.

---

## In-App Purchase

| Field | Value |
|---|---|
| Type | Non-Consumable |
| Reference name | Remove ads |
| Product ID | `remove_ads` (must match Android; the shared code uses it) |
| Price | USD 0.99 |
| Display name (en-US) | Remove ads |
| Description (en-US) | One purchase removes the banner for good. |
| Review screenshot | App Settings with the Remove Ads row, from the simulator (the local StoreKit configuration shows the $0.99 price). |
| Family Sharing | Off (as in `Products.storekit`). **TBD** whether to turn it on. |

The first submission must include the in-app purchase with the version, or
Apple reviews the app without it.

---

## Pricing and Availability

| Field | Value |
|---|---|
| Price | Free |
| Availability | **Only the 15 live regions**: United States, Canada, Australia, New Zealand, United Kingdom, Ireland, India, Philippines, Nigeria, South Africa, Pakistan, Kenya, Ghana, Singapore, Malaysia (just-my-weather-aoi). |
| Make available in new countries automatically | **Off**. Regions open deliberately (docs/REGIONS.md, "On iOS"). |
| Pre-order | Not used. |

---

## App Review Information (per submission)

| Field | Value |
|---|---|
| Contact name | Evan Ray |
| Contact email | `dev@raylytics.io` |
| Contact phone | (provide at submission) |
| Sign-in required | **No**. The app has no accounts. |
| Demo video | Link in the Notes below; see `docs/APP_STORE.md` "Demo video for review". |

### Notes for Reviewer (paste into the textarea)

```
Just My Weather is a weather app that shows only the data points the user picks, in a grid they arrange, with alerts on conditions they define. Reviewer notes:

1. NO ACCOUNT. Every feature works without signing in. The app has no server of its own.

2. WEATHER DATA. Forecasts come from the US National Weather Service (api.weather.gov) for US places and from MET Norway (api.met.no) elsewhere. Both are free public government services. The app sends them only the coordinates of the place being viewed.

3. LOCATION. Location is optional, while using the app, approximate accuracy. If declined, the app shows its default place and the user can search for a place by name (an offline list bundled in the app).

4. ALERTS. Alerts are rules such as "temperature below 35° overnight", checked with iOS background app refresh, so they can arrive late; the Alerts screen says so. Official NWS warnings (opt-in) post as time-sensitive notifications and exist only for US places during active warnings. The attached video shows an alert arriving on a physical iPhone, since a reviewer cannot trigger one on demand.

5. ADS AND TRACKING. One AdMob banner at the bottom of the main screen. Google's consent form (UMP) runs first where required; Apple's tracking prompt follows only where ads may run. The ad is never given the user's places, rules or weather.

6. IN-APP PURCHASE. "Remove ads" (remove_ads), a $0.99 non-consumable in App Settings, removes the banner. "Restore purchase" sits under it.

Demo video: <link>
Contact: dev@raylytics.io
```

---

## Per-release fields

| Field | Source |
|---|---|
| Version (`CFBundleShortVersionString`) | `VERSION`, mirrored into `MARKETING_VERSION` by `scripts/sync-version.sh`. |
| Build (`CFBundleVersion`) | `CURRENT_PROJECT_VERSION` in `iosApp/project.yml`; bumped and uploaded by `scripts/ios/release-testflight.sh --confirm-upload`. |
| What's New in This Version | `docs/RELEASES.md`, the version's store-facing block under "iOS". Paste it; do not keep a second copy here. |
| Screenshots | `store-assets/screenshots/ios/`; sizes and capture steps in its README. |
