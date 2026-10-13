# Android ↔ iOS parity

Where the two apps match, where they differ, and why. Tracked under the iOS
port epic, `just-my-weather-nyt`.

The rule this file enforces: **a shared feature is done when it has a row
here.** A row says what each platform does, whether that is parity, and
which bd issue owns any gap. A feature with no row has not been looked at
on the other platform, so it is not done.

Audited 2026-10-13 against branch `ios-port` at `3eb62ba`. iOS has not
shipped a build yet, so "iOS" below means what the code does on the iOS 27
simulator. It does not mean what users have.

Four parts:

1. [Feature matrix](#1-feature-matrix): every feature, on each platform.
2. [Divergence ledger](#2-divergence-ledger): every `expect`/`actual` and
   every Swift twin, each marked justified or open.
3. [Deferred](#3-deferred): absent from iOS for now, may come back.
4. [Dropped](#4-dropped): absent from iOS for good.

The last section, [Last iOS build](#last-ios-build), records the shared
commit the last iOS build was cut from. `scripts/release-drift.sh` reads it.

---

## 1. Feature matrix

Status values:

- **parity**: same behaviour on both platforms.
- **parity, platform-shaped**: same result for the user, through each
  platform's own mechanism.
- **best effort**: present on iOS, but the OS limits it (see the note).
- **open bug**: a known defect, with a bd id.
- **deferred** / **dropped**: see parts 3 and 4.

The Maestro column names the flow in `.maestro/` that covers the feature.
`scripts/e2e.sh` runs the same suite on both platforms. Last iOS run:
iPhone 16 simulator, iOS 27, 8/9 passed. Last Android run: Pixel 7, API 35,
10/10 passed (commit `3eb62ba`).

| Feature | Android | iOS | Status | bd | Maestro |
|---|---|---|---|---|---|
| Glance: modules on the lattice, tap for details | shared Compose UI (`ui/home/`) | same code | parity | — | `00-smoke` |
| Arrange mode: drag to move, corner-drag to resize | shared `ui/home/ModuleGrid` | same code; the arrange bar replaces the action bar at the foot while arranging | parity | — | `06-arrange` (both) |
| Forecast module: Hourly / Daily toggle, up to nine days | shared `ui/home/ForecastModule`; NWS plus MET Norway | same code | parity | — | `07-forecast` |
| Customize: which modules, sizes, density | shared `ui/customize/` | same code | parity | — | `01-customize` |
| Themes: light/dark/system, accent, typeface | shared `ui/theme/` | same code, but the "system sans" typeface is bundled Roboto on iOS (ledger: `sansFamily`) | parity | — | `01-customize` |
| Places: offline gazetteer search, saved places | shared `ui/places/`; `places.tsv` from assets (`AssetPlaceSource`) | same code; the same `places.tsv` bundled (`BundlePlaceSource`) | parity | — | `08-places` |
| Region & units | shared `region/`; clues from `AndroidRegionSignals` | shared `region/`; clues from `IosRegionSignals` (storefront, fix, place, Region setting, time zone) | parity, platform-shaped (see docs/REGIONS.md "On iOS") | — | — (unit-tested: `RegionResolverTest`, `StorefrontsTest`) |
| Personal alerts: rules, evaluation, notify-once | shared `AlertCheck`, run by WorkManager (`AlertWorker`) on the user's schedule | shared `AlertCheck`, run by `BGAppRefreshTask` (`AlertRefresh.swift`) when iOS allows | **best effort** on iOS (plan decision 2). The Alerts screen says so, and says it more firmly when Background App Refresh is off (`BackgroundAlerts`). | — | `02-alerts` |
| Safety alerts (official NWS warnings) | `HIGH`-importance channel; ignores quiet hours | `.timeSensitive` interruption level (needs the Time Sensitive entitlement); ignores quiet hours | parity, platform-shaped | — | `05-safety-alerts` |
| Quiet hours | low-importance "quiet" channel | `.passive` interruption level (no sound, no banner) | parity, platform-shaped | — | `04-quiet-hours` |
| Notifications: permission and posting | `AlertNotifier`; Android 13+ asks | `Notifier.swift`; asked when the user opens Alerts | parity, platform-shaped | — | `02-alerts` |
| Banner ad and consent | AdMob banner; UMP (`AdsConsent`, `UmpConsentGateway`); no personalization flag | AdMob banner; UMP, then Apple's tracking prompt (ATT) where ads may run; no personalization flag (`AdsService.swift`) | parity, platform-shaped (plan decision 4) | — | — |
| Remove Ads ($0.99, `remove_ads`) | Play Billing (`PlayBillingGateway`); shared `RemoveAdsManager` decides | StoreKit 2 (`StoreService.swift`, `StoreKitGateway` in `IosApp.kt`) under the App Store id `remove_ads_just_my_weather` (`AppStoreProducts`); shared `RemoveAdsManager` decides | parity, platform-shaped | — | — (iOS: `StoreServiceTests`, 4 XCTests over `Products.storekit`) |
| Support mail: bug report and idea | shared `SupportMail`; `SupportIntents` reads `Build` and the app's own logcat, hands off via `ACTION_SENDTO` | shared `SupportMail`; `SupportService.swift` reads the app's own `OSLogStore`, hands off to Mail (`mailto:` fallback) | parity, platform-shaped | just-my-weather-nyt.12 (copy built outside composition, both platforms) | `09-support` |
| Rate the app | opens the Play listing (`StoreListing`) | opens the App Store's write-review page (`JMWAppStoreID` 6822450301) | parity, platform-shaped | — | — |
| UI strings | shared catalog `shared/src/commonMain/composeResources/values/strings.xml` | same catalog; the App Settings lines that name the store have an `_ios` variant (App Store, Apple Account) | parity | just-my-weather-nyt.12 | all |
| Home screen widgets | Glance widgets (`widget/`, `ui/widget/`) | none | **deferred** (part 3) | — | — |
| Gadgetbridge hand-off | `data/gadgetbridge/`, behind App Settings → Advanced | none; the Advanced section is not shown | **dropped** (part 4) | — | `03-gadgetbridge` (tagged `android-only`) |

Toolchain follow-up that touches both platforms: just-my-weather-nyt.11
(AGP 9.1 and compileSdk 37, then Compose Multiplatform 1.12).

---

## 2. Divergence ledger

Every place the platforms run different code. Each entry is **justified**
(the difference is inherent to the platform, or chosen on purpose) or
**open** (a gap someone still owes).

### Re-derive the list

The `expect` declarations live in `shared/src/commonMain`, and each one has
an `actual` in `androidMain` and in `iosMain`. This lists all three (run
from the repo root):

```bash
git grep -nE '^(internal )?(expect|actual) ' -- shared/src
```

On 2026-10-13 that printed 36 lines: 12 `expect` declarations in 7
`commonMain` files, plus 12 `actual`s on each platform. Each file appears
in the table below.

The Swift twins are one file per Android service, named after the service
they mirror:

```bash
ls iosApp/JustMyWeather/Platform
```

On 2026-10-13: `AdsService.swift`, `AlertRefresh.swift`,
`IosPlatformImpl.swift`, `LocationService.swift`, `Notifier.swift`,
`StoreService.swift`, `SupportService.swift`.

The Kotlin twins in `iosMain` that are not `actual`s (they implement a
common interface instead):

```bash
git grep -lE '^(class|object|interface|fun) ' -- shared/src/iosMain
```

If a re-run shows a file or declaration with no row below, it is an
unaudited divergence. Add a row, justified or open.

The one value-level fork in common code is `currentPlatform`
(`Platform.kt`). Find its uses with
`git grep -n 'currentPlatform ==' -- shared/src/commonMain`. On 2026-10-13
it is used three times, all in `ui/settings/AppSettingsScreen.kt`: hide the
Gadgetbridge section on iOS, choose the iOS privacy-policy URL, and choose
the App Store wording (`onStore`).

### `expect` / `actual` (Kotlin)

| Declaration (commonMain) | Android `actual` | iOS `actual` | Verdict |
|---|---|---|---|
| `Platform.kt`: `currentPlatform` | `Platform.ANDROID` | `Platform.IOS` | **Justified.** This is the one place a platform name is spelled. Its three uses are listed above. |
| `billing/PurchaseHost.kt`: `expect open class PurchaseHost` | `typealias` to `android.app.Activity` (Play Billing needs one) | `typealias` to `UIViewController` | **Justified.** The shared code only passes it from the host to the gateway. |
| `data/places/TextFold.kt`: `decomposeCanonically` | `java.text.Normalizer` NFD | `NSString.decomposedStringWithCanonicalMapping` | **Justified.** Same Unicode operation, pinned by `FormattingParityTest`, which passes on the JVM and the iOS simulator. |
| `region/CountryLocale.kt`: `isoCountryCodes`, `englishCountryName`, `countryDateTimePatterns` | `java.util.Locale` + `java.time` | `NSLocale` + `NSDateFormatter` | **Justified, with a known difference.** For a country *not* in the registry's prepared list, the date order and clock are derived from each platform's CLDR data, and Java and Apple ship different CLDR versions, so a few unprepared countries may read differently. Prepared regions (every region we ship to) write their date order and clock down in `Regions.kt`, so they are unaffected. |
| `ui/TestTags.kt`: `Modifier.exposeTestTags()` | `testTagsAsResourceId = true`, so Maestro and Espresso see test tags as resource ids | no-op: Compose on iOS already maps `testTag` to the accessibility identifier | **Justified.** |
| `ui/theme/SansFamily.kt`: `sansFamily()` | `FontFamily.SansSerif` (the system's Roboto) | bundled Roboto, four weights (`shared/src/iosMain/composeResources/font/`, SIL OFL in `ROBOTO-OFL.txt`) | **Justified.** SF Pro sets wider than Roboto, and the glance, sized and tested against Roboto, truncated and wrapped. |
| `view/TimeFormat.kt`: `LocalDate/LocalTime/LocalDateTime.format(TimePattern)`, `fixedDecimals` | `DateTimeFormatter.ofPattern(p, Locale.ENGLISH)`; `String.format(Locale.US)` | `en_US_POSIX` `NSDateFormatter`; half-up `NSNumberFormatter` | **Justified.** Android's side is the shipped code, unchanged. `FormattingParityTest` pins clock, date and number output on both. `fixedDecimals` also builds MET Norway request URLs and point-cache keys, so both must stay byte-identical. |

### Kotlin twins in `iosMain` (common interface, iOS implementation)

| iOS file | Android twin | Common contract | Verdict |
|---|---|---|---|
| `IosApp.kt` | `MainActivity` + `AppContainer` (`JustMyWeatherApp.kt`) | `AppGraph`, `AppRoot`, `AppViewModels` | **Justified.** Each host builds the same graph from its own parts. iOS builds ViewModels once for the app's life (no configuration changes to survive). |
| `IosApp.kt`: `StoreKitGateway` | `PlayBillingGateway` | `BillingGateway` | **Justified.** StoreKit 2 has no acknowledge step and no purchase tokens: a verified transaction is the entitlement, so every owned product reads as purchased and acknowledged, and `acknowledge` returns true. |
| `IosBridges.kt`: `StoreBridge`, `AdsBridge`, `IosPlatform` | — | — | **Justified.** Callback-shaped protocols the Swift twins implement, so Swift needs no Kotlin coroutines. `IosApp` adapts them to the common contracts. |
| `data/nws/UrlSessionTransport.kt` | `OkHttpTransport` | `HttpTransport` | **Justified.** The plan named Ktor's Darwin engine. Ktor 3.5 needs kotlinx-coroutines 1.11 and the app is on 1.9, so about fifty lines of `NSURLSession` avoid the version skew. Same 10 s timeout; NWS uncached, MET Norway with a small URL cache; failures are okio's `IOException`, which on Android is `java.io.IOException`. |
| `data/places/BundlePlaceSource.kt` | `AssetPlaceSource` | `suspend () -> PlaceCatalog` | **Justified.** The same `places.tsv`; `iosApp/project.yml` copies it from `app/src/main/assets`, so there is one file. |
| `region/IosRegionSignals.kt` | `AndroidRegionSignals` | `RegionSignals` | **Justified.** An iPhone app cannot read a mobile-network country, so iOS uses the App Store storefront and the device time zone instead. Android returns null for both, so its order and answers are unchanged. See docs/REGIONS.md "On iOS". |

### Swift twins (`iosApp/JustMyWeather/Platform/`)

| Swift file | Android twin | Common contract | Verdict |
|---|---|---|---|
| `LocationService.swift` | `location/LocationProvider` | `LocationSource` | **Justified.** CoreLocation, when-in-use, reduced accuracy, last fix only. One difference in timing: iOS asks for location at launch, while Android asks after the consent form. Whether the iOS location prompt can collide with the consent form or the ATT prompt on a first launch is **unverified**. |
| `Notifier.swift` | `alerts/AlertNotifier` | `AlertNotifications` | **Justified.** Same titles and bodies; quiet hours `.passive`, safety alerts `.timeSensitive`. Apple honours `.timeSensitive` only with the Time Sensitive Notifications entitlement, which `iosApp/JustMyWeather/JustMyWeather.entitlements` declares; the App ID must carry the matching capability (docs/APP_STORE.md, step 1). |
| `AlertRefresh.swift` | `alerts/AlertWorker` (scheduling only) | `IosPlatform.scheduleAlertRefresh`, shared `AlertCheck` | **Justified (plan decision 2).** WorkManager runs on schedule. `BGAppRefreshTask` only takes "not before N minutes" (at least 15) and iOS decides the rest; with Background App Refresh off it never runs. The check itself is the shared `AlertCheck`, not a Swift copy. |
| `StoreService.swift` | `billing/PlayBillingGateway` | `StoreBridge` → `BillingGateway` | **Justified.** StoreKit 2; `RemoveAdsManager` makes every decision. Transactions that change outside the purchase sheet (`Transaction.updates`: an Ask to Buy approved later, a refund) are finished and forwarded through `StoreBridge.onTransactionUpdate`, as Play Billing's purchase listener does on Android. Restore re-reads `Transaction.currentEntitlements`. It does not call `AppStore.sync()`; whether App Review expects that for an explicit "Restore purchase" is **unverified**. |
| `AdsService.swift` | `ads/AdsConsent` + `ads/UmpConsentGateway` + `ads/AdBanner` | `AdsBridge` (iOS only) | **Justified.** Ads consent stays a per-platform twin with no common class: iOS adds Apple's ATT prompt after UMP, only where ads may run, and the two flows are shaped too differently for one class to fit both. Requests carry no personalization flag on either platform (decision 4). GMA 12.11, UMP 3.0. |
| `SupportService.swift` | `support/SupportIntents` | `SupportHost` | **Justified.** OSLogStore (the app's own process, last 50 lines) in place of the app's own logcat; Mail with a `mailto:` fallback in place of `ACTION_SENDTO`. |
| `IosPlatformImpl.swift`: `openStoreListing()` | `support/StoreListing` | `IosPlatform.openStoreListing` | **Justified.** Android opens the Play listing. iOS opens `https://apps.apple.com/app/id<id>?action=write-review`, with the id read from `JMWAppStoreID` in `Info.plist` (6822450301, set 2026-10-13, just-my-weather-k7j). Should the key ever be empty, the row falls back to `SKStoreReviewController.requestReview`, which Apple rate-limits and never shows in TestFlight. The page only resolves once the app is live on the store. |
| `IosPlatformImpl.swift`: the rest | `MainActivity` (permissions, version line) | `IosPlatform` | **Justified.** Notification permission, the version line, Background App Refresh status, the presenting view controller. |

### Outside the code

| Where | Divergence | Verdict |
|---|---|---|
| `.maestro/*.yaml` | `checked:` assertions on switches and `hideKeyboard` run on Android only (`when: platform: Android`): iOS reports no switch state to Maestro, and its number pad has no dismiss key (the Add button beside the field is tapped instead — justified). On iOS, 01-customize and 07-forecast still check their switch's visible effect, but the switch taps in 02-alerts (rule on/off, quiet hours) and 05-safety-alerts (the opt-in default) go **unchecked**. | **Open** — just-my-weather-nyt.14. |
| `.maestro/07-forecast.yaml` | Back closes a sheet on Android; on iPhone a tap on the scrim above it does. | **Justified.** |
| `.maestro/03-gadgetbridge.yaml` | Tagged `android-only`; `scripts/e2e.sh ios` excludes the tag. | **Justified** (part 4). |
| Store wording | The App Settings strings that name the store have an `_ios` variant (App Store, Apple Account), chosen by `onStore`. The privacy policy URL differs: `/justmyweather/privacy` vs `/justmyweather/privacy-ios`. | **Justified.** |
| `RegionSource.DEVICE_SETTINGS` copy | The Region & units screen says "from your phone's language settings". On iOS the clue is the Region setting (`NSLocale.currentLocale.countryCode`), not the language. | **Open, minor copy.** |

---

## 3. Deferred

### Home screen widgets (decision B3)

Not in the first iOS release. They may return through WidgetKit. Android's
Glance widgets are unchanged.

The two platforms build widgets on different models, so a port is a
redesign of the refresh and storage, not a copy:

| | Android (today) | iOS (WidgetKit) |
|---|---|---|
| Drawing | Jetpack Glance, rendered to RemoteViews | SwiftUI views in a widget extension. Compose does not run there, so the tile drawing would be written again in SwiftUI. |
| Refresh | `WidgetRefreshWorker` fetches every 15 minutes for all widgets into one `WidgetData` | The app hands WidgetKit a timeline of entries. iOS decides when to reload, against a daily budget, so 15-minute freshness is not guaranteed. |
| Data | DataStore in the app's own storage, mirrored into Glance's per-widget state (`WidgetState`) | The widget is a separate process, so config and data need an App Group container both can read. Today the settings file lives in the app's own Documents directory (`IosApp.settingsFile`). |
| Configuration | `WidgetConfigureActivity`, reusing Customize's pickers | App Intents-based configuration, in Swift. |

## 4. Dropped

### Gadgetbridge hand-off (decision B2)

Gadgetbridge is an Android app that relays data to watches. There is
nothing on iOS to hand off to, so the feature stays Android-only behind its
existing seam (`app/…/data/gadgetbridge/`). The setting is a common stored
value (`GadgetbridgeSettingsRepository`) because App Settings reads it on
both platforms; iOS never shows it. `IosApp` passes a no-op where Android
passes the exporter.

---

## Last iOS build

The shared commit the most recent iOS build (TestFlight or App Store) was
cut from. Update it after every iOS cut; `scripts/ios/release-testflight.sh
--confirm-upload` prints the line to paste. `scripts/release-drift.sh` reads
the first `shared-commit:` line in this section and lists the `shared/`
commits since then, which is the shared code iOS has not shipped yet.

shared-commit: 4003b7f
