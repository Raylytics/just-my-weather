# iOS release checklist: 0.4.2 build 4 after the Google ads exit

The steps from "H9 done" to "build submitted for review", in order, with
who does each. **Agent** means an agent may do it unprompted; **Evan**
means a human decision, credential or portal click. Nothing here uploads,
pushes or submits without Evan's explicit go-ahead for that step, given in
the exchange where it happens (CLAUDE.md "Release confirmation gate";
runbook hard rules). Companion documents: `docs/APP_STORE.md` (bootstrap,
signing, gotchas), `store-assets/app-store-listing.md` (every App Store
Connect field), `raylytics/docs/ad-migration/RUNBOOK.md` (the epic, the
hard rules, Stage C and D), bd `just-my-weather-hnz` (the 3.1.1
resubmission runbook, which this list absorbs).

Why this release: App Review rejected 0.4.0 (build 3) on 2026-11-02 under
3.1.1 (no distinct Restore Purchases). The fix (`dc4070a`) is on this
branch, and so is the Google ads exit: AdMob and UMP are gone, the InMobi
CMP gates Appodeal mediation. The resubmission is build 4, version 0.4.2.

## 0. Preconditions

| # | Step | Who | How |
|---|------|-----|-----|
| 0.1 | H9 done: the Android half has its ad SDK (Appodeal 4.4.0 + InMobi CMP 2.4.3) and passes `scripts/check-no-google-ads.sh`. The runbook ships both platforms together; shipping iOS first is a decision Evan records in `raylytics-98a`, not something an agent concludes. | Evan | `bd show raylytics-40w` (H9), `bd show raylytics-98a` |
| 0.2 | Appodeal dashboard: the iOS app exists, banner ad type on, the eight default-account networks connected, no AdMob/Ad Manager/Meta/Yandex. | Evan | https://app.appodeal.com |
| 0.3 | InMobi CMP portal: the property's p-code is live, TCF + US state regulations enabled, Google Vendors tab reviewed (H8). | Evan | https://support.inmobi.com/choice |
| 0.4 | Local machine has the gitignored files: `iosApp/team.properties`, `iosApp/Config/Team.xcconfig` (`scripts/ios/setup-team.sh`), `iosApp/Config/Ads.xcconfig` (real app key and p-code), `~/.appstoreconnect/private_keys/AuthKey_*.p8`. A worktree does not inherit them: copy `team.properties` and `Ads.xcconfig` from the main checkout, then run `setup-team.sh`. | Agent | `ls iosApp/team.properties iosApp/Config/Team.xcconfig iosApp/Config/Ads.xcconfig` |

## 1. Prove the branch (agent, repeat after any new commit)

| # | Step | Who | How |
|---|------|-----|-----|
| 1.1 | JVM gate | Agent | `scripts/verify.sh` (expects PASS; the branch has 529 tests) |
| 1.2 | No Google ad code declared, imported or initialized | Agent | `scripts/check-no-google-ads.sh` (expects `clean`) |
| 1.3 | Drift report (advisory) | Agent | `scripts/release-drift.sh` |
| 1.4 | iOS unit tests on the project simulator "JMW iPhone (iOS 27)": StoreService (5) + AdsRules (12), 17 in all | Agent | boot by UDID, `xcodebuild test -project iosApp/JustMyWeather.xcodeproj -scheme JustMyWeather -destination "id=<udid>" -derivedDataPath build/ios/DerivedData`; rerun with `test-without-building` if the first run after boot times out on StoreKit; shut the simulator down |
| 1.5 | Release rehearsal: gates, `xcodegen`, archive, App Store export, no bump, no upload | Agent | `scripts/ios/release-testflight.sh` (no flag). Output: `build/ios/JustMyWeather.xcarchive`, `build/ios/export/JustMyWeather.ipa` |
| 1.6 | Archive inspection (2026-11-04 result: 31 embedded frameworks, no `GoogleMobileAds.framework`, no Google Mobile Ads SDK code anywhere; `UserMessagingPlatform.framework` present as Appodeal's transitive dependency and its classes statically linked through Stack Consent Manager, nothing in `iosApp/` imports or calls it; `InMobiCMP.framework` embedded, signed by the team, signature verifies; ids resolved; counts 230 and 7; ATS true; the `GAD*` names in the app binary are delegate protocols and name probes from the network adapters, not Google classes): no `GoogleMobileAds.framework`; `UserMessagingPlatform.framework` only if it arrived with Appodeal's Stack Consent Manager and nothing in `iosApp/` imports it; `InMobiCMP.framework` embedded and signed; final Info.plist ids resolved, `SKAdNetworkItems` 230, `AdAttributionKitItems` 7, `NSAllowsArbitraryLoads` true, `NSUserTrackingUsageDescription` present | Agent | see "Archive inspection" in this file's appendix |
| 1.7 | Privacy report from the archive's merged privacy manifests, saved under `build/release-prep/` | Agent | appendix; or Xcode Organizer > the archive > Generate Privacy Report |
| 1.8 | Simulator smoke (Debug build, Appodeal test mode; 2026-11-04 result: the full sequence `gather:` → `cmp loaded:` → `cmp ui: USRegulation screen shown` → `cmp us regulations consent: gpp=set` → `consent settled: canRequestAds=true` → `appodeal: initializing (banner)` → `appodeal: initialized; loading 1 banner(s)` in 5 s after the choice, test banner on the glance; the relaunch settled on "no need to re-trigger" with no form, 5 s after `gather:` on this network, both runs' CMP load took about 5 s; Privacy choices reopened the notice. Two simulator-only artifacts: a simctl-installed Debug build has no StoreKit configuration, so StoreKit's "Sign in to Apple Account" sheet stacks on the notice and recurs until cancelled; and the ATT prompt did not show because the simulator still held this morning's answer, Settings > Privacy & Security > Tracking lists the app as allowed, so the status was not undetermined and the code correctly skipped it. Both to be re-seen on a device in Stage C): first launch shows the consent notice, a choice, ATT, `appodeal: initializing (banner)`, `appodeal: initialized; loading N banner(s)`; relaunch settles in about a second with no form; App Settings > Privacy choices reopens the form | Agent | `xcrun simctl spawn <udid> log stream --level info --predicate 'subsystem == "io.raylytics.justmyweather" AND category == "ads"'` while driving the app; shut the simulator down |
| 1.9 | roborev clean on every commit of the branch, or disagreements documented | Agent | `roborev fix --from-skill --list` |

## 2. Merge (Evan)

| # | Step | Who | How |
|---|------|-----|-----|
| 2.1 | H6: push `feat/applovin-max`, open the PR against `main` (title = Conventional Commit subject), CI green, squash-merge | Evan | `git push -u origin feat/applovin-max`, `gh pr create`, merge in GitHub |
| 2.2 | Confirm `release/0.4.2` is fully contained (it is the merge-base today) and retire it, or merge main back into it; the cut is made from the merged commit | Evan | `git branch --contains 9e932ae` |
| 2.3 | What's new text for 0.4.2 is already in the build (`whatsnew/ReleaseNotes.kt`, 3e9a7a4: Restore purchases row, caret fix). Decide whether the ad change gets a line (recommendation: no; users see a consent form, not a feature) | Evan | edit `ReleaseNotes.kt` + strings only if yes, before the cut |

## 3. Stage C: consent verification on a device (Evan)

| # | Step | Who | How |
|---|------|-----|-----|
| 3.1 | Install the merged build on a physical iPhone | Evan / Agent | `scripts/ios/install-device.sh <udid>` (keeps data) |
| 3.2 | US path: first launch shows the US opt-out notice (where the state applies) or no form, then ATT, then a test banner (Debug) or a live banner (Release) | Evan | watch Console.app, subsystem `io.raylytics.justmyweather`, category `ads` |
| 3.3 | EEA/UK path, **unverified so far**: with a VPN exit in the EEA (or an EU test account/region), the TCF form appears before ATT; "Accept" yields `IABTCF_TCString` set and purpose 1 consented and the banner loads; "Reject" yields no ATT prompt and no banner; Appodeal shows no consent prompt of its own | Evan | same log; InMobi portal's consent report after the fact |
| 3.4 | Privacy choices from App Settings reopens the right form in both regimes | Evan | App Settings > Privacy choices |
| 3.5 | Paying user: buy Remove ads (sandbox) and relaunch: no CMP prompt, no `appodeal:` lines at all | Evan | log shows no `gather:` past the store check |
| 3.6 | Record the outcome in the runbook's Stage C section and in `raylytics-98a` | Evan | |

## 4. Cut and upload (Evan's go, agent runs)

| # | Step | Who | How |
|---|------|-----|-----|
| 4.1 | Clean tree on the merged commit, rehearsal green again | Agent | `scripts/ios/release-testflight.sh` |
| 4.2 | **Evan says "upload build 4"** in the exchange | Evan | |
| 4.3 | The cut: bumps `CURRENT_PROJECT_VERSION` to 4, archives, exports, uploads | Agent | `scripts/ios/release-testflight.sh --confirm-upload` |
| 4.4 | Commit the bump; log the cut in `docs/RELEASES.md` ("iOS"); set `shared-commit:` in `docs/PARITY.md` "Last iOS build" | Agent | `git commit iosApp/project.yml`, then docs commit; not pushed |
| 4.5 | App Store Connect processes the build (10 to 30 min); watch for the missing-manifest or export-compliance mail | Evan | ASC > TestFlight |
| 4.6 | TestFlight internal: install build 4, repeat 3.2 and 3.5 on the Release binary (live ads, real consent) | Evan | TestFlight app |

## 5. App Store Connect metadata (Evan; agent drafts text)

| # | Step | Who | How |
|---|------|-----|-----|
| 5.1 | Version: rename the rejected 0.4.0 record to 0.4.2 (or create 0.4.2), attach build 4 | Evan | ASC > App Store > iOS App |
| 5.2 | App Privacy: redo the labels from the archive's privacy report (Stage D), not from the old AdMob/UMP answers in `app-store-listing.md`; "Data Used to Track You" follows what the Appodeal and network manifests declare | Evan | ASC > App Privacy; report under `build/release-prep/` |
| 5.3 | Age rating: Advertising stays **Yes** | Evan | ASC > Age Rating |
| 5.4 | In-app purchase "Remove ads" (6822465262) stays attached to the submission; replace its review screenshot with `store-assets/screenshots/ios/iap-review-remove-ads.png` (recaptured 2026-11-02 with the Restore purchases row) | Evan | ASC > In-App Purchases |
| 5.5 | Privacy policy URL unchanged, but the page itself must match the build: port `store-assets/privacy-policy-ios.md` to `raylytics-site/justmyweather/privacy-ios.html` via a site PR (merging deploys), then confirm the live page names Appodeal and InMobi and no longer says all traffic is HTTPS | Agent (PR) / Evan (merge) | https://raylytics.io/justmyweather/privacy-ios |
| 5.6 | Support page: confirm https://raylytics.io/justmyweather/support says "Restore purchases" (PR #71 merged 2026-11-02; Netlify had not built it as of 11-04) | Evan | Netlify > raylytic-io > Deploys |
| 5.7 | Reviewer notes: paste from `store-assets/app-store-listing.md` "Notes for Reviewer". Item 5 describes the consent flow (InMobi form where the law asks, then Apple's tracking prompt only where ads may run) and item 6 the Restore purchases row; no video line | Evan | ASC > App Review Information > Notes |
| 5.8 | Resolution Center: reply in the thread of submission b4b3ba3e (text in bd `just-my-weather-hnz`, item 8), adding that the ad SDK changed to Appodeal behind the InMobi consent form, then **Submit for Review** | Evan | ASC > App Review |

## 6. The first 48 hours (Evan)

| # | Watch | Where | What is normal |
|---|-------|-------|----------------|
| 6.1 | Review status; a 2.1 "Information Needed" asking for a demo video is answered with a recording from a physical iPhone (not a simulator, not Android) | ASC > App Review | approval in 1 to 3 days |
| 6.2 | Appodeal fill rate and eCPM for the iOS banner; a near-zero fill means a network is misconfigured or the test flag leaked | Appodeal dashboard > Statistics | fill climbing over the first day |
| 6.3 | Crash-free rate (Xcode Organizer > Crashes, or ASC > Analytics) | Xcode / ASC | no new crash group from `Appodeal`, `APD*`, `InMobiCMP` |
| 6.4 | Consent rates in the InMobi portal: TCF accept/reject split, US opt-out count | InMobi CMP portal > Reports | any non-zero EEA traffic proves the TCF path ran in the field |
| 6.5 | App Store Connect mail: privacy-manifest warnings on the build, export-compliance questions | mail | none |
| 6.6 | Support mail (dev@raylytics.io): "ads after buying", "can't restore", consent form stuck | mail | none; each becomes a bd issue |
| 6.7 | Afterwards: close `just-my-weather-hnz`, `nyt.10`, the `nyt` epic; update bd memory `jmw-launch`; fix `docs/PARITY.md` "has not shipped" (a12) and `CLAUDE.md`'s "iOS app has not shipped yet" | Agent | |

## Appendix: archive inspection commands

```bash
A=build/ios/JustMyWeather.xcarchive/Products/Applications/JustMyWeather.app
ls "$A/Frameworks"                                   # expect InMobiCMP.framework and the SDKs; no GoogleMobileAds.framework
codesign -dv "$A/Frameworks/InMobiCMP.framework" 2>&1 | grep -E 'Identifier|TeamIdentifier'
plutil -p "$A/Info.plist" | grep -E 'JMWAppodealAppKey|JMWInMobiPcode|NSUserTrackingUsageDescription|NSAllowsArbitraryLoads'
plutil -extract SKAdNetworkItems json -o - "$A/Info.plist" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))'      # 230
plutil -extract AdAttributionKitItems json -o - "$A/Info.plist" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))' # 7
grep -rn 'import UserMessagingPlatform' iosApp/ || echo "no UMP import"
find "$A" -name PrivacyInfo.xcprivacy                # the manifests the privacy report merges
```
