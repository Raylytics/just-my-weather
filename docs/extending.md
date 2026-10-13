# Extending the app

The common things you'd want to add each live in one obvious place. Most catalogs
are enums consumed by exhaustive `when` expressions with no `else`, so when you
add an entry **the compiler points at every spot you still need to handle** —
follow the errors and you're done.

**Where the files are.** Both apps run the same code, so almost everything
below is in the shared module: paths are relative to
`shared/src/commonMain/kotlin/io/raylytics/justmyweather/` unless they say
otherwise. A change there reaches Android and iOS at once. The exceptions are
marked: **(app)** is Android-only code under
`app/src/main/java/io/raylytics/justmyweather/`, and iOS-only code is under
`iosApp/JustMyWeather/` or `shared/src/iosMain/`. When a change is
user-visible, check it on both platforms and give it a row in
`docs/PARITY.md` (see "Add a platform service" for the rule).

> Trap: inside a property accessor, a bare `field` is Kotlin's backing-field
> keyword. When you mean a constructor property named `field`, write `this.field`
> (see `FieldSetting.label` in `view/ViewConfig.kt`).

## Add a data point

Say you want **humidity** on the glance and as something to alert on. A station
reading needs nothing in the module catalog — `ModuleKey.Reading` wraps every
`WeatherField` automatically.

1. **`view/WeatherField.kt`** — add the catalog entry and handle the exhaustive
   `when`s the compiler flags:
   ```kotlin
   HUMIDITY("humidity", "Humidity"),   // key is the stable persistence id — never rename it
   ```
   Add branches to `numericValue`, `formatValue` (e.g. `"${value.roundToInt()}%"`),
   `format` if it needs special wording, `forecastValue` (return `null` unless
   the forecast carries it — also flip `isForecastable` if it does),
   `defaultSize` (the footprint the module ships at on the glance lattice — a
   single cell for a short number, 2×1 for longer text) and `minSize` (the
   smallest the user may drag it to — one cell for anything that is a number
   and a unit, wider for prose). `isNumeric` is true for everything except
   `CONDITIONS`, so it's alertable automatically. The detail sheet a tap opens
   lists every `WeatherField` from the snapshot (`view/Details.ofObservation`),
   so the new point appears there with no further wiring.
2. **`data/WeatherSnapshot.kt`** — add `val humidityPct: Double?`.
3. **`data/nws/`** — add the field to `NwsWire.ObservationProps`, project it in
   `NwsModels.CurrentObservation`, and read it in `NwsClient.getObservation`.
4. **`data/WeatherRepository.kt`** — map it onto the `WeatherSnapshot` in `load`.

The customize screen and the glance grid iterate `ModuleKey.catalog` (which
includes every `WeatherField` through `ModuleKey.Reading`), and the alert
builder iterates `WeatherField` directly, so the new point shows up in each with
no further wiring.

## Add a module that is not a station reading

Sun times are the worked example: computed on the device, no threshold to alert
on, and a table rather than a value.

1. **`view/ModuleKey.kt`** — add a subtype. Give it a stable `key` (never rename
   it), a `defaultLabel`, a `defaultSize` and a `minSize` (the floor the corner
   drag stops at — size it to what the module has to say).
2. **`view/ViewRender.kt`** — add a `ModuleContent` variant carrying whatever
   the tile needs to draw, and a branch in `render`. If the data is not in
   `WeatherSnapshot` (sun times are not), pass it into `render` as its own
   parameter rather than forcing it into the snapshot.
3. **`ui/home/`** — add a branch in `ModuleTile`'s `when` over the content, and
   put the drawing in its own file (see `SunModule.kt`). A module whose content
   changes shape with width should adapt there, not be flattened to fit.
4. **`widget/WidgetContent.kt`** (app) — the same `when`, in Glance: how the module
   draws on the home screen. If it needs data the refresh worker does not
   fetch, add it to `WidgetData` (and its codec) and to `WidgetNeeds`.

Every `when` involved is exhaustive with no `else`, so the compiler lists what
you still owe. Persistence, arranging, resizing and the accessibility actions
all follow from the catalog with no further work.

## Add an alert subject (a forecast-only metric)

Subjects that aren't view fields — like chance of rain — are `AlertSubject`s.

1. **`alerts/AlertSubject.kt`** — add a subclass/`data object` implementing
   `currentValue` (return `null` if there's no live reading — it then never fires
   on a `NOW` rule), `forecastValue`, and `format`. Register it in `byKey` and in
   the `current` / `forecast` lists so the builder offers it in the right windows.
2. If it comes from the forecast, extend `data/nws/NwsModels.ForecastPoint` (and
   its parsing in `NwsClient.getHourlyForecast` / `NwsWire`) — as
   `precipProbabilityPercent` already does.

The evaluator compares whatever the subject reports against the threshold, so no
evaluator changes are needed.

## Add a forecast framing

**`view/ForecastMode.kt`** — add an entry (stable `key`, chip `label`), give it
data in `HomeViewModel.ensureForecast`, and render it in `ForecastModule`'s
`when`. Both `when`s are exhaustive, so the compiler lists what you still owe.
The toggle in the forecast module's header and the customize picker both
iterate `entries`, so neither needs touching.

Note there is no "no forecast" framing: whether the forecast appears at all is
the forecast *module's* visibility (`ModuleKey.Forecast`, switched like any
other tile), not a member of this enum. That split is deliberate — see the
file's own comment.

## Add a forecast tile element

**`view/ForecastElement.kt`** — add an entry (stable `key`, chip `label`), then
draw it in `ForecastModule`'s `ElementLines` when the entry is in the set and
the tile's data carries a value. The customize chips iterate `entries`, so the
toggle appears on its own. Leave it out of `DEFAULT` unless it is something
most people plan by: the tile ships as calm as it was.

## Add a forecast window

**`alerts/AlertWindow.kt`** — add an entry with its `key`, chip `label`, and
sentence `phrase`, then add a branch to `contains(time, now, zone)` describing
which forecast hours fall in it. `BELOW` rules watch the window's min, `ABOVE`
its max — that's handled centrally in `AlertEvaluator`.

## Add a theme accent or typeface

- **Accent:** add an `AccentChoice` entry in `view/ThemeConfig.kt`, a `Color` in
  `ui/theme/Color.kt`, and a branch in `accentColor(...)` in `ui/theme/Theme.kt`.
- **Typeface:** add a `TypeChoice` entry and a branch in `fontFamily(...)` in
  `ui/theme/Theme.kt`. A typeface that is not a system generic must ship as a
  font file in `shared/src/commonMain/composeResources/font/`, so both apps
  have it. Note that "sans" is the platform's own on Android and bundled
  Roboto on iOS (`sansFamily()`; docs/PARITY.md says why).

Both appear as chips on the customize screen automatically.

## Add a density level

Add a `Density` entry in `view/Density.kt` and a branch in `Density.spec()` in
`ui/home/DensitySpec.kt` with the full-module value size, the grid gap and the
section spacing. The customize chip row and home view read it from there.

Density controls size and spacing only — not which facts appear. A level that
hid a field would make the same fact discoverable at one setting and not
another, which is how the observation time came to be missing from the calmest
view while the forecast beside it looked like a contradiction.

## A new persisted setting

Mirror the existing config trio: a pure data class + a `*Codec` (JSON by stable
string keys, every field defaulted so old/partial/corrupt blobs degrade to the
default rather than crash) + a `*Repository` over the shared DataStore, wired in
`AppGraph` (`AppGraph.kt`), which both apps build. `ThemeConfig` /
`AlertSettings` are the templates. Never rename a stored key: Android
installs carry settings files from earlier versions, and
`PreferencesCompatibilityTest` holds them to it.

## Add or change words on a screen

Every string a screen draws lives in
`shared/src/commonMain/composeResources/values/strings.xml` and is read with
`stringResource(Res.string.<key>)`. Both apps use this one catalog, so a
translation is written once.

- Name the key after the screen plus a few words (`alerts_add_alert`); use
  `common_` for words several screens share.
- Write whole sentences with numbered placeholders (`%1$s`), never pieces
  joined in code: fragments can't be translated.
- Write quotes plainly. Compose resources show a backslash for `\'`.
  `StringCatalogTest` enforces this and gap-free placeholder numbers.
- If the sentence names the store or the account, add an `_ios` twin
  ("App Store", "Apple Account") and pick between them with `onStore` in
  `ui/settings/AppSettingsScreen.kt`.
- Code that can't call a composable (a snackbar in an effect, accessibility
  actions) reads its strings first and passes them in. Copy built in
  ViewModels is not yet in the catalog (just-my-weather-nyt.12).

## Add a platform service

Anything that needs an OS API (a sensor, a store, a system sheet) is "one
contract, two native twins":

1. **A common contract** in `commonMain`: an interface (`LocationSource`,
   `AlertNotifications`, `BillingGateway`) that says what the shared code
   needs, in plain values. Use `expect`/`actual` only for a small function
   or type with an exact equivalent on each side (`TimeFormat`,
   `TextFold`).
2. **The Android twin** in `app/` (or `shared/src/androidMain` when it needs
   no Android Context), passed in through `AppContainer`.
3. **The iOS twin**: a Swift file in `iosApp/JustMyWeather/Platform/`, named
   after the Android twin, when the API is Swift-first (StoreKit, the Google
   SDKs, CoreLocation). Expose it through `IosPlatform` in
   `shared/src/iosMain/.../IosBridges.kt` with callbacks rather than
   suspend functions, and adapt it to the common contract in `IosApp.kt`. A
   twin that Kotlin/Native can write directly against Foundation
   (`UrlSessionTransport`) goes in `shared/src/iosMain` instead.
4. **A row in `docs/PARITY.md`**: in the feature matrix, and in the
   divergence ledger, marked justified or open. A shared feature is not done
   until it has its row. If the feature cannot exist on one platform, say so
   there (Deferred or Dropped) and keep the UI from offering it
   (`currentPlatform`, as App Settings does for Gadgetbridge).
5. **A Maestro flow** that passes on both platforms (`scripts/e2e.sh`), with
   any platform-specific step in a `when: platform:` block, or the flow
   tagged `android-only` with the reason in its header.
