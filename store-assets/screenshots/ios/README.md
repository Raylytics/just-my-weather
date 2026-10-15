# iOS screenshots

App Store screenshots for Just My Weather: the 6.9" set (1320 × 2868),
captured 2026-10-14 on a temporary iPhone 16 Pro Max simulator (iOS 27),
plus `iap-review-remove-ads.png` for the Remove ads purchase's review
screenshot. The Android set lives one level up, in `screenshots/` and
`screenshots/final/`.

How the 2026-10-14 set was made: Google's test banner was painted over with
the page background on 01, 02 and 04 (the buy sheet asks for a real sandbox
Apple Account when the app is installed with `simctl install`, so the local
StoreKit configuration does not apply); 08 was taken before the first ad
loaded. The alert number pad has no way to dismiss it on iOS, so 05 was
taken after leaving and reopening Alerts.

## Sizes

**Verify the current requirements in App Store Connect** (the version page →
Previews and Screenshots → "View All Sizes in Media Manager") before
capturing. Apple changes the required display classes, and open-frame found
App Store Connect sometimes walking through an older class first and
rejecting anything larger than that class allows.

As of open-frame's last submission (2026-10):

| Display class | Portrait size | Simulator that captures it natively |
|---|---|---|
| 6.9" | 1320 × 2868 | iPhone 16 Pro Max / iPhone 17 Pro Max |
| 6.5" | 1284 × 2778 | iPhone 14 Plus / iPhone 13 Pro Max class |

The app is iPhone only (`TARGETED_DEVICE_FAMILY = 1`), so no iPad
screenshots are needed. 3 to 10 per class; PNG or JPEG, no transparency.

This project's simulator, "JMW iPhone (iOS 27)", is an **iPhone 16**, whose
screen (1179 × 2556) is not one of those classes. Capture on a Pro Max
simulator instead, created for the purpose and following the same hygiene
rules as the project's own (CLAUDE.md "iOS simulator hygiene": boot it for
the task, shut it down after, never alongside the Android emulator). The
6.5" set can be scaled from the 6.9" originals, as open-frame does.

## Fresh state

A simulator keeps the app's data between installs, as the Android emulator's
snapshot does (CLAUDE.md "A fresh emulator is not a fresh install"). iOS has
no `pm clear`; the equivalent is to uninstall and install again:

```bash
xcrun simctl uninstall <udid> io.raylytics.justmyweather
xcrun simctl install <udid> build/ios/DerivedData/Build/Products/<Config>-iphonesimulator/JustMyWeather.app
```

Uninstalling also resets the app's permission answers (location,
notifications, tracking) and Google's consent answer, so the first launch
shows those prompts again. Answer them before capturing.

## Capture

1. Build for the simulator. `scripts/e2e.sh ios <udid>` prints the
   `xcodegen` / `xcodebuild` / `simctl install` commands when the app is
   missing.
2. Set a clean status bar:
   ```bash
   xcrun simctl status_bar <udid> override --time 9:41 --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3
   ```
3. Give the simulator a location (Features → Location, or
   `xcrun simctl location <udid> set 38.25,-85.76` for Louisville, the place
   the Android set used).
4. Arrange each screen, then:
   ```bash
   xcrun simctl io <udid> screenshot store-assets/screenshots/ios/01-your-glance.png
   ```
5. Clear the override when done: `xcrun simctl status_bar <udid> clear`.

**The banner.** A simulator can only show Google's labelled test ads (Debug
always uses the sample ids). The Android shots cropped the banner off the
bottom; a 6.9" capture can't be cropped without leaving the class's size.
One way to a banner-free shot is to buy Remove Ads first through the local
StoreKit configuration (`Products.storekit`), which needs no App Store
account. That configuration applies to runs started from Xcode's scheme;
whether it also applies to an app installed with `simctl install` and
launched by hand is unverified.

## The set

Mirror the Android set, so both listings tell the same story, in this order:

1. `01-your-glance` — a customized glance, dark look
2. `02-arrange` — arrange mode with corner handles
3. `03-details` — a tile's detail sheet
4. `04-daily` — the forecast tile on Daily
5. `05-alerts` — personal rules and the builder
6. `06-places` — saved places
7. `07-customize` — per-tile size, light look
8. `08-default-glance` — the out-of-the-box glance, light look (take it right
   after a fresh install)

No widget or Gadgetbridge screens: neither exists on iOS.
