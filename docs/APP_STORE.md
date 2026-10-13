# App Store runbook

Detailed reference for iOS releases: the iOS twin of `docs/PLAY_STORE.md`.
CLAUDE.md "iOS app" has the day-to-day commands. This file has the
bootstrap, the release procedure, and the gotchas carried over from
open-frame (`../open-frame/docs/APP_STORE.md`), which uses the same scripts
and the same Apple account.

Store copy lives in `store-assets/app-store-listing.md` (every App Store
Connect field) and `store-assets/privacy-policy-ios.md`. Per-version notes go
in `docs/RELEASES.md` under "iOS".

Status on 2026-10-13: no iOS build has been signed, archived or uploaded.
The steps below marked with a bd id are open human hand-offs.

## Account

- Apple Developer team: confirmed 2026-10-13, Paid Apps agreement active.
- The same Raylytics account already publishes open-frame on the App Store.
  Its App Store Connect API key and Apple Distribution certificate can be
  reused for this app (just-my-weather-06n).
- Public contact: `dev@raylytics.io`, as on Android.

## Bootstrap (first time for this app)

Do these in order. Each one is a hand-off to Evan unless it says otherwise.

### 1. Bundle id — just-my-weather-r6d

Register the explicit App ID `io.raylytics.justmyweather` under
Certificates, Identifiers & Profiles. It matches Android's applicationId.
open-frame had to use a different id on iOS because `com.openframe` was
taken on Apple. Here the name was free on both stores, so one id serves both
apps and the one Maestro suite (`APP_ID` in `scripts/ios/common.sh`
and `scripts/e2e.sh`).

Capabilities: **In-App Purchase** and **Time Sensitive Notifications**.
Push Notifications is not needed: every notification is local.

Time Sensitive is there for safety alerts, which post with `.timeSensitive`
(`Platform/Notifier.swift`) so a tornado warning gets through Focus. The app
declares the entitlement in `iosApp/JustMyWeather/JustMyWeather.entitlements`
(`CODE_SIGN_ENTITLEMENTS` in `project.yml`). The App ID must have the
matching capability **before** `create-profile.sh` runs: a profile made
without it does not carry the entitlement, and signing fails. If the App ID
was registered first, tick the capability and run `create-profile.sh`
again. (The hand-off text in just-my-weather-r6d lists In-App Purchase only.)

### 2. App Store Connect record and Remove Ads — just-my-weather-k7j

- New App: iOS, name `Just My Weather`, English (U.S.), bundle id above,
  SKU `justmyweather-ios`.
- In-App Purchase: **Non-Consumable**, product id **`remove_ads`** (the
  shared code uses the same id on both stores), **USD 0.99**, display name
  "Remove ads", description "One purchase removes the banner for good."
  It shows "Missing Metadata" until a build is attached, which is expected.
  Review wants a screenshot of the purchase; take one from the simulator.
- Category **Weather**. Age rating: **Advertising: Yes** (see "Gotchas").
- Every field's value is in `store-assets/app-store-listing.md`.
- Copy the app's numeric **Apple ID** (App Information → Apple ID) into
  `JMWAppStoreID` in `iosApp/JustMyWeather/Info.plist`. App Settings →
  Rate opens the App Store's write-review page with it; while it is empty
  the row falls back to Apple's rate-limited review prompt, which never
  shows in TestFlight.

### 3. AdMob iOS app and banner — just-my-weather-6p3

AdMob console → Apps → Add app → iOS → Just My Weather, then a Banner unit.
Put both ids in `iosApp/Config/AdMob.xcconfig` (gitignored; copy
`AdMob.xcconfig.example`). xcconfig reads `//` as a comment, so write the
unit id's slash as `/$()/`, as the example does.

- Debug always serves Google's sample ids (`Config/Debug.xcconfig`).
- Release uses the sample ids unless `AdMob.xcconfig` exists
  (`Config/Release.xcconfig` ends with `#include? "AdMob.xcconfig"`).
- `release-testflight.sh` refuses to run if `AdMob.xcconfig` is missing,
  still holds the example's placeholders, or holds Google's sample publisher
  `ca-app-pub-3940256099942544`. Unlike Android there is no `--sample-ads`
  escape: a TestFlight build is promoted to the App Store as is.
- In AdMob Privacy & messaging, make sure the GDPR message also targets the
  iOS app, and publish an **IDFA explainer** (the ATT message) for it.
  Google's consent SDK shows that explainer before Apple's "Allow to track"
  prompt.

### 4. team.properties and the API key

```bash
cp iosApp/team.properties.example iosApp/team.properties   # gitignored
```

Fill in the 10-character team id, then the App Store Connect API key:
App Store Connect → Users and Access → Integrations → Keys → **+**, role
**App Manager** (or reuse open-frame's key). Apple lets you download the
`.p8` file once. Keep it in `~/.appstoreconnect/private_keys/` (one of the
paths altool searches) and never commit it. `.gitignore` blocks
`**/AuthKey_*.p8` as a backstop. Environment variables `IOS_TEAM_ID`,
`IOS_ASC_KEY_ID`, `IOS_ASC_ISSUER_ID` and `IOS_ASC_KEY_PATH` override the
file.

### 5. Signing — just-my-weather-06n (with an agent)

```bash
scripts/ios/setup-team.sh                  # team id into the gitignored Team.xcconfig
scripts/ios/register-device.sh <UDID>      # at least one device on the team
# Xcode → Settings → Accounts → team → Manage Certificates → + → Apple Distribution
scripts/ios/create-profile.sh              # App Store profile "Just My Weather App Store"
```

- **`setup-team.sh`** writes `iosApp/Config/Team.xcconfig` (gitignored,
  `DEVELOPMENT_TEAM = <id>`) and touches nothing tracked: `Debug.xcconfig`
  and `Release.xcconfig` already `#include? "Team.xcconfig"` (the `?` makes
  it a no-op on a clone without one), and the committed
  `iosApp/ExportOptions.plist` keeps its `REPLACE_WITH_TEAM_ID` placeholder —
  `export-ipa.sh` writes the team into the temporary copy it exports with.
  So a release tree stays clean and no developer's team id is committed.
  The team does not go in `project.yml` either: XcodeGen writes
  `project.yml` settings over the xcconfigs, so even `DEVELOPMENT_TEAM: ""`
  there blanks the team. `archive.sh` checks this by name before it builds.
- **`register-device.sh`** registers a UDID through the App Store Connect
  API. A fresh team needs one registered device before automatic Development
  signing (which `archive.sh` uses) can make a profile. `idevice_id -l`
  (`brew install libimobiledevice`) reads a connected iPhone's UDID.
- **The Apple Distribution certificate** is installed by hand in Xcode.
  Skip this if open-frame's is already in this keychain.
- **`create-profile.sh`** creates the App Store provisioning profile through
  the API and installs the `.mobileprovision` locally. It is idempotent: when
  the profile already exists, it downloads it again.

After this, every release is one command with no GUI.

### 6. Availability — just-my-weather-aoi

Pricing and Availability → Availability: select **only the 15 live
regions** (US, CA, AU, NZ, GB, IE, IN, PH, NG, ZA, PK, KE, GH, SG, MY) and
leave "make available in new countries automatically" **off**. This mirrors
Play, where regions are opened deliberately (docs/REGIONS.md, "On iOS").
Every storefront opened here also needs a row in `region/Storefronts.kt`.

### 7. Privacy labels and the policy — just-my-weather-kvr

The policy source is `store-assets/privacy-policy-ios.md`, to be hosted at
`https://raylytics.io/justmyweather/privacy-ios` from the `raylytics-site`
repo. The app already links that URL from App Settings → About
(`PRIVACY_POLICY_URL_IOS` in `ui/settings/AppSettingsScreen.kt`), so the
page must be live before review.

The labels are entered in App Store Connect → App Privacy. Do not answer from
memory or from this file. Take the answers from the **archive's privacy
report** (Xcode Organizer → the archive → Generate Privacy Report). That
report merges the privacy manifests of the Google Mobile Ads and UMP SDKs
into the app's own.

- The app itself collects nothing. It sends only forecast coordinates to NWS
  and MET Norway.
- The Google SDKs collect data, so the answer to "Do you or your third-party
  partners collect data?" is **Yes**.
- For reference, open-frame (same SDKs) entered, after correcting an earlier
  "No data collected": Device ID (tracking: yes), Coarse Location, Product
  Interaction, Advertising Data, Crash Data, Performance Data, Other
  Diagnostic Data (`../open-frame/store-assets/app-store-listing.md`, "App
  Privacy").
- The hand-off text in just-my-weather-kvr marks several types "not linked
  to identity". open-frame's table marks them "linked". These disagree.
  Settle it from the privacy report before publishing the labels.

## Releasing (day-to-day)

```bash
scripts/ios/release-testflight.sh                   # rehearsal: gates, archive, export; no bump, no upload
scripts/ios/release-testflight.sh --confirm-upload  # the real cut, with Evan's go-ahead for THIS upload
```

**Release confirmation gate.** Uploading any build to App Store Connect
(TestFlight internal included) and submitting any version for review each
need Evan's explicit go-ahead for that specific upload or submission, given
in the current exchange. An earlier go-ahead does not carry forward to a
respin. `--confirm-upload` is how that go-ahead is spelled; an agent never
passes it on its own initiative. Running `upload-testflight.sh` directly
also uploads, under the same rule. See CLAUDE.md "Release confirmation gate".

The orchestrator runs, and stops at the first failure:

0. `scripts/release-drift.sh`: an advisory report of the `shared/` commits
   iOS has not shipped. It never blocks.
1. Refuses a tree with uncommitted changes to tracked files.
2. Refuses sample or placeholder AdMob ids.
3. `scripts/verify.sh` (the JVM gate), then `./gradlew :shared:allTests`
   (the shared module's iOS tests, which need a Mac).
4. `scripts/sync-version.sh --check`: `VERSION`, Android `versionName` and
   iOS `MARKETING_VERSION` agree.
5. `bump-build-number.sh`: `CURRENT_PROJECT_VERSION` + 1 in
   `iosApp/project.yml`. Real cut only.
6. `archive.sh`: `xcodegen generate`, then a Release archive at
   `build/ios/JustMyWeather.xcarchive`. The target's pre-build phase runs
   `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, so the first
   Release archive is slow (Kotlin/Native release linking), not stuck.
7. `export-ipa.sh --method app-store-connect`: the IPA, signed manually.
8. `upload-testflight.sh`: `xcrun altool`. It greps altool's output,
   because altool can exit 0 on a validation failure. Real cut only.

The rehearsal leaves the tree clean. Its IPA carries the *current* build
number, which App Store Connect may already have seen, so it is not for
uploading.

After a real cut:

```bash
git commit iosApp/project.yml -m "release: iOS build N to TestFlight"
```

Then log the cut under "iOS" in `docs/RELEASES.md`, and set
`shared-commit:` in docs/PARITY.md "Last iOS build" to the commit the build
was cut from. The script prints both lines. If the run fails after the bump,
it says whether the number is burned (Apple saw the upload) or can be undone
with `git checkout iosApp/project.yml`.

The Maestro suite is not run by the orchestrator. Run it on the simulator
against the commit being cut first (`scripts/e2e.sh ios <udid>`; CLAUDE.md
"iOS app").

### Versioning

- `VERSION` (repo root) is the one semver for both apps.
  `scripts/sync-version.sh` writes it into Android's `versionName` and iOS's
  `MARKETING_VERSION` (`iosApp/project.yml`); `--check` only verifies.
  Plain `MAJOR.MINOR.PATCH`: App Store Connect rejects a suffix in
  `CFBundleShortVersionString`.
- `CURRENT_PROJECT_VERSION` (`CFBundleVersion`) is iOS's own build counter.
  It must increase on every upload, failed ones included, and is
  independent of Android's `versionCode`.
- A version that reached production on either store is frozen (CLAUDE.md
  "Versioning"). Bump `VERSION` first when behaviour changed.
- The platforms ship on their own schedules (plan decision 8). Drift is
  allowed and reported, not blocked.
- Never edit versions in the generated `.xcodeproj`; it is regenerated
  from `project.yml` and not committed.

### TestFlight: internal, not external

To test on our own devices, attach the build to an **Internal Testing**
group. Internal has no Beta App Review and is available within minutes of
processing. External testing needs Apple's review (a day or more). If a
build shows "Ready to Submit" with a "Remove from Review" button, it went to
External by mistake: pull it back and attach it to Internal.

### Local StoreKit testing

No App Store Connect record is needed to test Remove Ads:

- `iosApp/JustMyWeather/Products.storekit` defines `remove_ads` as a
  non-consumable at 0.99, US storefront.
- The scheme's Run action uses it (`storeKitConfiguration` in
  `project.yml`), so on a simulator the product can be bought and restored
  with no sandbox account. It is excluded from the shipped app.
- `iosApp/JustMyWeatherTests/StoreServiceTests.swift` drives
  `StoreService` through `SKTestSession` over the same file. Its four tests
  cover the $0.99 offer, a bought product being owned, nothing bought
  meaning nothing owned, and an unknown product having no offer. Run them
  with `xcodebuild test` (CLAUDE.md "iOS app").
- A sandbox purchase on a real device from a TestFlight build is a separate
  step of the first release (just-my-weather-nyt.10).

## Gotchas (from open-frame, and where they apply here)

- **Age Rating: "Advertising" must be Yes.** Apple's automated scan finds
  the Google Mobile Ads SDK. open-frame's 1.5.1 review was halted on
  2026-10-10 because the descriptor was unset. The fix is metadata only.
  Never answer that there are no ads.
- **Export uses manual signing.** `signingStyle: automatic` sends export
  through Apple's cloud signing, which an App Manager-role API key may not
  drive ("Cloud signing permission error", "No profiles for X were found").
  `export-ipa.sh` writes a temporary ExportOptions with `signingStyle=manual`
  and the profile pinned by name (`IOS_APP_STORE_PROFILE_NAME` in
  `scripts/ios/common.sh`, "Just My Weather App Store"). The committed
  `ExportOptions.plist` stays as it is. Rename the profile in the portal
  only together with that constant.
- **Archive needs a registered device.** The archive is development-signed
  with `-allowProvisioningUpdates`, and automatic Development signing fails
  on a team with no devices. Run `register-device.sh` first.
- **A regenerated Distribution certificate makes the profile stale.** The
  certificate expires yearly. After a new one, export fails with
  "Provisioning profile … doesn't include signing certificate". Fix:
  ```bash
  rm ~/Library/Developer/Xcode/UserData/'Provisioning Profiles'/*.mobileprovision
  rm ~/Library/MobileDevice/'Provisioning Profiles'/*.mobileprovision
  scripts/ios/create-profile.sh
  ```
  The first command deletes every cached profile on the machine, open-frame's
  included. `create-profile.sh` restores this app's; run open-frame's for its
  own.
- **Export compliance** is answered in `Info.plist`:
  `ITSAppUsesNonExemptEncryption = false` (HTTPS only).
- **Privacy manifest.** `iosApp/JustMyWeather/PrivacyInfo.xcprivacy`
  declares the required-reason APIs (SystemBootTime 35F9.1, UserDefaults
  CA92.1, DiskSpace E174.1 / 85F4.1, FileTimestamp 3B52.1 / C617.1 /
  0A2A.1), taken from open-frame's reviewed manifest. It collects no data
  types. App Store Connect emails a warning within minutes of an upload if
  the manifest is missing or malformed. Update it when a dependency adds a
  required-reason API.
- **`NSPrivacyTracking` is false at the app level**, as in open-frame. The
  app's own code does no tracking. The Google Mobile Ads SDK declares its own
  tracking in its own manifest, and the archive's privacy report merges the
  two. **Re-check this at review.** If Apple reads the app-level `false` as
  contradicting the "Data Used to Track You" label, the fix is a manifest
  change and a new build. open-frame ships the same arrangement (its
  roborev #5443); whether a review has examined it there is unverified.
- **"The build you uploaded was not valid"** usually means a duplicate
  `CFBundleVersion`. Bump, rebuild, upload again. The number is burned.
- **A stuck submission blocks everything else.** A submission left in
  `UNRESOLVED_ISSUES` still holds the app version, and a new submission then
  reports "add an app version" even though one exists. Resolve or withdraw
  the old submission. The App Store Connect API shows the conflict
  (`GET /v1/reviewSubmissions?filter[app]=<appId>`); the web UI does not.
- **iPhone only.** `TARGETED_DEVICE_FAMILY = 1` (plan decision 5). iPads
  install it in iPhone compatibility mode. A universal build needs iPad icons
  and iPad screenshots. Layouts are width-adaptive, so the switch is mostly
  configuration.

### Demo video for review: when to make one

open-frame needs a demo video on *every* submission, because App Review
cannot test it without a Samsung TV (Guideline 2.1, "Information Needed").
This app needs no hardware. A reviewer can use the glance, forecast,
Customize, Places, Region & units and Remove Ads directly.

Two features a reviewer cannot make happen on demand:

- **A personal alert firing.** It depends on the weather crossing the rule's
  threshold, and on iOS choosing to run the background check.
- **A safety alert.** It needs an active NWS warning at the place, which
  only exists in the US and only during severe weather.

So the plan (just-my-weather-nyt.10) includes a short video, attached to the
first submission: on a **physical iPhone, not a simulator, and not an
Android phone** (both apps draw the same UI, so an Android recording is easy
to submit by mistake, and Apple rejects it). Show a rule set with a
threshold that holds right now, its notification arriving, the
notification-permission prompt, the location prompt, and the consent and
tracking prompts. Put the link in App Review Information → **Notes**; a file
attached to a Resolution Center reply does not count. If review later asks
for a video again, Apple's wording means it will ask every time after that.

## Info.plist and permissions

- `NSLocationWhenInUseUsageDescription`: approximate location for the
  weather where you are. When-in-use only, reduced accuracy.
- `NSUserTrackingUsageDescription`: the ATT prompt's text. Asked only after
  Google's consent SDK allows ads, never on top of Google's form, and never
  where ads will not run.
- `UIBackgroundModes: fetch` and `BGTaskSchedulerPermittedIdentifiers:
  io.raylytics.justmyweather.alerts`: the background alert check.
- `SKAdNetworkItems`: Google's 50 SKAdNetwork ids.
- `GADApplicationIdentifier` and `JMWBannerUnitID` come from the xcconfig
  (step 3).
- `JMWAppStoreID`: the app's numeric App Store id, for the Rate row
  (step 2). Empty until the App Store Connect record exists.
- Entitlements (`JustMyWeather.entitlements`): Time Sensitive Notifications
  only (step 1).
- Notifications need no plist key. Permission is asked when the user opens
  Alerts.
- Scene lifecycle (`UIApplicationSceneManifest`) from the start, as
  open-frame learned to (its BUG-026).

## Store listing assets

- `store-assets/app-store-listing.md`: every App Store Connect field. Edit
  it first, then paste into App Store Connect.
- `store-assets/privacy-policy-ios.md`: the iOS policy source.
- `store-assets/screenshots/ios/README.md`: sizes and how to capture.
- App icon: `iosApp/JustMyWeather/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`,
  rendered opaque at 1024 px from `store-assets/icon-512.svg`, the same art
  as the Android launcher.
