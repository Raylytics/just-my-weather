# Project Instructions for AI Agents

This file provides instructions and context for AI coding agents working on this project.

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:7510c1e2 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/SYNC_CONCEPTS.md for details and anti-patterns.

## Session Completion

When ending a work session:

1. **File issues for remaining work** — `wyk create` an issue for anything
   that needs follow-up
2. **Run quality gates** (if code changed) — `scripts/verify.sh`
3. **Update issue status** — close finished work, update in-progress items
4. **Commit everything** — no work left uncommitted
5. **Hand off** — update `.claude/state/QUEUE.md` with context for the next
   session

Pushing follows the "Commit cadence" rule below: **confirm with the user
before pushing** — local commits are the completion bar for a session, not
`git push`. Never clear stashes or prune branches you didn't create; that
state may be someone else's WIP.

<!-- END BEADS INTEGRATION -->

**`.beads/` is gitignored (decision 2026-10-20).** Nothing under `.beads/` — the JSONL export, hooks, config — is committed to the git tree; `git ls-files .beads` is empty and must stay that way (no `git add -f`, no `chore(bd): sync export` commits). Issue data moves between machines only through bd's Dolt remote (`refs/dolt/data`, via `bd dolt push/pull`), never through the git tree; the JSONL export is a local convenience. This rule lives outside bd's managed block above on purpose, so `bd` regenerating that block cannot drop it.


## What this app is

**Just My Weather** is a minimalist, customizable weather app for Android and
iPhone (the iOS app has not shipped yet; `docs/PARITY.md`). The
default is calm and sparse — one glance answers "what's it like right now." The
power is letting users build their own view (which data points, order, density,
look) and their own *personal* alerts (rule-based: "tell me when the overnight
low drops below 35°F"). It is the personal-alerting sibling to `almanac-bell`
(official hazard alerts); shared NWS data backbone, distinct intent. Built to be
open-sourced, so legibility of the code is a product requirement, not a nicety.

## Build & Test

Requires JDK 17+ and the Android SDK (`local.properties` → `sdk.dir`). Uses the
Gradle wrapper — no global Gradle needed.

```bash
scripts/verify.sh                   # the whole JVM gate; --fix formats first
./gradlew :app:testDebugUnitTest    # fast: JVM unit tests (JUnit 5)
./gradlew :app:ktlintCheck          # style gate
./gradlew :app:ktlintFormat         # auto-fix style
./gradlew :app:assembleDebug        # build the debug APK
scripts/hooks/install.sh            # verify/install the pre-commit gate wiring
scripts/android/release-internal.sh # cut + upload a Play internal build (needs approval)
```

iOS commands are under "iOS app" below.

The pre-commit hook runs `:app:testDebugUnitTest :shared:testAndroidHostTest
:app:ktlintCheck :shared:ktlintCheck` on any Kotlin/resource change (see "The pre-commit gate" below for how it is wired
under bd's `core.hooksPath`). Bypass with `--no-verify` only in emergencies.

## Commit cadence

Commit after any significant change rather than batching unrelated work into
one big commit. "Significant" means: a working bug fix, a completed refactor,
a feature in a shippable state, or a doc/process change. One logical concern
per commit, with a message that explains **why**. The pre-commit gate runs
unit tests + ktlint on every Kotlin/resource commit, so each commit is
runnable.

**Don't push without explicit user approval, even when committing
autonomously.** Local commits are cheap to rewrite; pushed ones are not.

This section is the single authoritative push policy. If any bd-generated
guidance says otherwise — the managed "Beads Issue Tracker" block above
(which bd may regenerate) or `bd prime` hook output — this section wins. The
repo sets `no-git-ops: true` in `.beads/config.yaml` so `bd prime` omits git
commands from its session-close protocol; don't unset it. (Adopted
2026-10-01 from open-frame, where it has worked well.)

## The pre-commit gate

`scripts/hooks/pre-commit` is the tracked gate (unit tests + ktlint +
instrumented-test compile on any staged `.kt/.kts/.xml/.pro/.properties`;
docs-only commits skip it). **bd sets `core.hooksPath` to `.beads/hooks`**, so
git never reads `.git/hooks/` — the old symlink there ran nothing. The gate is
therefore *called* from `.beads/hooks/pre-commit` (present locally but untracked since 2026-10-20, when `.beads/` was gitignored; a fresh clone gets the gate from `scripts/hooks/install.sh`), in a
`PROJECT GATE` block above bd's own markers (bd regenerates only what sits
between its markers). `scripts/hooks/install.sh` verifies that wiring, or
installs the symlink on a clone that has no `core.hooksPath`.

- The hook runs against the **working tree**, not the staged snapshot: stage
  everything you intend to ship, or stash the noise first.
- Bypass in emergencies only: `git commit --no-verify`.
- Not in the hook: Maestro flows (CI's `ui` job) and anything needing a
  device.

## User settings must survive every update (gate)

**A user's settings are the product.** Their glance layout, theme, alert
rules, saved places, units and widgets are hours of their tuning, and an
update that loses or rewrites them is a disaster for an app like this, not
a bug. So no version ships unless the settings an earlier version wrote
still read, unchanged, in the new one. This is a release gate, not a
nicety: an agent may not weaken it to make a build pass.

What enforces it today:

- **`PreferencesCompatibilityTest`** (`shared/src/androidHostTest/.../data/`)
  reads real settings files captured from shipped builds
  (`shared/src/androidHostTest/resources/prefs/*.preferences_pb`). Each must
  re-serialize to the same bytes, and every stored value must decode and
  re-encode unchanged through its codec. The one change allowed is a
  catalog module appended on read, hidden.
- It runs in the pre-commit hook, `scripts/verify.sh`, CI, and so in every
  `release-internal.sh` cut. iOS uses the same DataStore file format and
  the same codecs, so the same test covers it.
- Each codec's own tests (`*CodecTest`) cover a blob from before each field
  existed decoding to the default.

The rules:

1. **Never rename or repurpose a stored key or a stable `key` token**
   (enum keys, unit keys, module keys). Add new ones instead.
2. **Every codec field is defaulted**, so an older, partial or corrupt blob
   decodes to a working value. It never throws, and never resets
   neighbouring settings.
3. **Never re-capture a fixture to make the test pass.** The fixtures are
   what shipped builds wrote. A failure means users would lose something.
4. **A release that adds or changes persisted state adds a fixture from
   the previous production build** before it ships, so the gate covers
   what users actually have on their phones (how: the fixture section of
   `PreferencesCompatibilityTest`'s KDoc).
5. **A new store or codec joins the test's codec map** in the same commit.
6. **Before a production promote, run the upgrade check:** install the
   current production build on the API 35 emulator, use it, install the
   candidate over it with `adb install -r`, and confirm the settings are
   intact. This is how the What's new sheet was checked for 0.4.1; the
   bd memory `whats-new-sheet` has the steps.

Android Auto Backup is on (`allowBackup`), so a phone restored from backup
also hands an older settings file to a newer app. That is the same
problem, and the same gate covers it.

Known gaps (just-my-weather-rtu): the fixtures date from 0.3.1, so the
fields 0.4.0 and 0.4.1 added are covered only by codec tests; Android
widget configs have no fixture; the upgrade check is manual.

## Release confirmation gate (agents)

Uploading ANY build to Google Play — internal track included — requires
Evan's explicit confirmation or direct request for **that specific upload**,
given in the current exchange. A prior "release to internal" does NOT carry
forward: a same-day respin needs its own go-ahead. When a fix is ready, stop
and ask; "proceed" on a bug fix is not approval to ship it. The release
scripts (`scripts/android/release-internal.sh`, `upload-play.sh`,
`promote-play.sh`) are the only things that upload; `bundleRelease` alone is
local and safe.

The same rule covers Apple: every upload to App Store Connect (TestFlight's
internal group included) and every App Store submission for review needs its
own explicit go-ahead. `scripts/ios/release-testflight.sh --confirm-upload`
and `scripts/ios/upload-testflight.sh` are what upload; never pass
`--confirm-upload` on your own initiative. `release-testflight.sh` with no
flag is a rehearsal (gates, archive, export; no bump, no upload) and is safe.

## Play Store Releases

See `docs/PLAY_STORE.md` for the bootstrap, console quirks, and signing;
`store-assets/play-store-listing.md` for every console field;
`docs/RELEASES.md` for the per-version log.

### Versioning

- **`versionName`** (semver, human-facing) and **`versionCode`** (integer,
  Play-facing) both live in `app/build.gradle.kts`. `versionName` is written
  there from the repo-root `VERSION` by `scripts/sync-version.sh`, which also
  sets iOS's version (see "One version, two apps").
- `versionCode` must increase on **every upload** to Play, failed and
  internal ones included. Play rejects a reused number; gaps are fine.
  `scripts/android/bump-version-code.sh` bumps by one.
- **A released `versionName` is FROZEN.** Once a version reaches production
  the next change of any kind gets a new one. Builds for testing carry the
  version they *will be promoted as* — bump `versionName` first, then let
  `versionCode` tick underneath it. A `versionCode` bump alone is not a
  substitute when behaviour changed.
- Log every cut (versionCode, date, track) in `docs/RELEASES.md`.

### Release procedure (day-to-day)

```bash
# One-time per machine: docs/PLAY_STORE.md "Bootstrap"
cp play.properties.example play.properties         # service-account key path
scripts/android/preflight-play.sh                  # auth + permissions, no side effects

# Cut: refuses a dirty tree, runs scripts/verify.sh, bumps versionCode,
# builds a SIGNED AAB, uploads to the internal track (no Play review).
scripts/android/release-internal.sh --notes "What testers will see"
git commit app/build.gradle.kts -m "release: versionCode N to internal"

# Promote the tested build (a track move, not a re-upload):
scripts/android/promote-play.sh [--rollout 0.2]
```

CI's `ui` job (Maestro on API 35) must be green on the commit being cut; the
orchestrator does not run the flows itself. The very first upload is manual
(Play's API refuses an app with no prior console upload). The service-account
JSON, `app/keystore.properties` and `iosApp/team.properties` are derived
copies; 1Password is the source of truth for their values (see "Secrets").

## iOS app

The iOS app is a thin SwiftUI host (`iosApp/`) around the shared Compose UI.
Needs Xcode with the iOS 27 SDK, `xcodegen` (`brew install xcodegen`) and,
for the UI flows, Maestro. The `.xcodeproj` is generated from
`iosApp/project.yml` and not committed: edit `project.yml`, never the
project. Building runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`
from the target's pre-build phase, so the Kotlin is compiled by the same
build. `<udid>` is this project's simulator (see "iOS simulator hygiene").

```bash
./gradlew :shared:allTests          # shared tests on the JVM AND the iOS simulator (Mac only)
(cd iosApp && xcodegen generate)    # regenerate the Xcode project after any project.yml change
xcodebuild build -project iosApp/JustMyWeather.xcodeproj -scheme JustMyWeather \
  -configuration Debug -destination "id=<udid>" -derivedDataPath build/ios/DerivedData
xcrun simctl install <udid> build/ios/DerivedData/Build/Products/Debug-iphonesimulator/JustMyWeather.app
xcodebuild test -project iosApp/JustMyWeather.xcodeproj -scheme JustMyWeather \
  -destination "id=<udid>" -derivedDataPath build/ios/DerivedData   # StoreKit tests (SKTestSession)
scripts/e2e.sh ios <udid>           # the Maestro suite on that simulator (installs nothing)
scripts/e2e.sh android <serial>     # the same suite on your emulator
scripts/ios/release-testflight.sh   # release rehearsal; see docs/APP_STORE.md
```

- Always pass `-destination "id=<udid>"`. A destination by name, or none,
  lets Xcode pick a simulator, which may be another session's.
- Boot the simulator and wait (`xcrun simctl boot <udid> && xcrun simctl
  bootstatus <udid> -b`) before `xcodebuild test`. On a simulator xcodebuild
  cold-boots itself, the StoreKit test service is not ready and every
  StoreKit test fails with "no products" (seen 2026-10-13; 4/4 pass once the
  simulator is up).
- `:shared:allTests` boots and shuts down its own simulator. It is still a
  simulator: don't run it while the Android emulator is up.
- The pre-commit hook does not look at `.swift` or `project.yml`: a
  Swift-only change is checked only by building it. Build it before
  committing.
- iOS is gated locally only (plan decision 7). CI runs the Android jobs and
  skips the shared module's iOS targets.
- Debug builds always use Google's sample AdMob ids. Signing, AdMob ids and
  releases: `docs/APP_STORE.md`.

## App Store Releases

See `docs/APP_STORE.md` for the bootstrap, signing, the release procedure
and the gotchas; `store-assets/app-store-listing.md` for every App Store
Connect field; `docs/RELEASES.md` ("iOS") for the per-version log. Uploads
fall under "Release confirmation gate" above.

### One version, two apps

- **`VERSION`** (repo root) is the one semver for both apps.
  `scripts/sync-version.sh` writes it into Android's `versionName` and iOS's
  `MARKETING_VERSION` (`iosApp/project.yml`); `--check` only verifies.
  Change `VERSION` and run the script, never the two fields by hand. The
  frozen-version rule under "Versioning" above applies to `VERSION`.
- The build counters stay per platform: `versionCode` on Android,
  `CURRENT_PROJECT_VERSION` on iOS (`scripts/ios/bump-build-number.sh`).
- **Drift is allowed** (plan decision 8): each platform cuts on its own
  schedule, and iOS is expected to trail. `scripts/release-drift.sh` is
  advisory: it lists the `shared/` commits since the last iOS build
  (`docs/PARITY.md` "Last iOS build") and whether the three version fields
  agree. It never blocks. After every iOS cut, update that `shared-commit:`
  line.

## Devices & background processes — leave the machine as you found it

An Android emulator costs ~8 CPU cores while it runs, and a forgotten one keeps
costing them for the rest of the session. Gradle/Kotlin daemons idle cheaply but
add up. **Every long-running process you start, you kill before you finish.**

Ownership is the whole game — other agent sessions and the developer's Android
Studio own processes that look just like yours, and an AVD name alone cannot
tell your emulator from a peer session's or from a leftover orphan. So make
ownership a **fact the emulator carries**: `ours()` finds emulators running this
project's AVD (`adb -s <serial> emu avd name`, verified), and the booting session
**claims** it with `setprop debug.jmw.owner <session id>` (verified settable
without root, read back intact, ~22 ms, well inside Android's 91-character
property limit). Ownership is then read, never inferred — and it needs no local
state, which matters because agent tool calls share no shell state and every
carry-forward scheme fails open as "everything is mine".

The rules that follow:

- **This project's AVD is `Pixel_7_API_35`**, and no other project's session may
  use that name. A second one, **`Pixel_7_API_36`** (Android 16, the targetSdk
  image, created 2026-10-04 for the pre-production pass), belongs to this
  project too and follows every rule below unchanged — run the same three
  calls with `AVD=Pixel_7_API_36`. Never have both up at once: the ownership
  claim is per emulator, but the cores are not. At most one emulator can run a given AVD — the emulator refuses
  to start one already running (verified: two boot attempts, one qemu process) —
  which is why taking `ours | head -1` is complete rather than lossy.
- **Only one session of this project holds it at a time.** Call 1 refuses to
  boot when the AVD is already up, and calls 2 and 3 each independently refuse
  to act on an emulator this session doesn't own — the guard is in every call,
  not just the first, because the calls are self-contained and call 1's warning
  may sit unread in a background stream.
- **The claim is made at boot, not at first use.** Call 1 claims the emulator as
  soon as adb answers, in the same call that launches it. That matters twice
  over: an unclaimed emulator could be claimed out from under you by a peer, and
  — worse — one you booted but never claimed is one *you* can no longer kill,
  since `mine()` wouldn't match it. A window of up to one poll interval (~1 s;
  the `setprop` itself is ~22 ms) remains between adb answering and the claim
  landing; it cannot be closed with these tools (the
  emulator's `-prop` flag accepts only `qemu.*` names and even those never
  surface via `getprop` — both verified), which is exactly why call 1 refuses to
  boot into a running AVD. Don't run two sessions through call 1 at once.
- **An unclaimed or foreign-claimed emulator is a human's problem, not the next
  session's.** You can *identify* an orphan (it runs your AVD but carries no
  claim, or a stale one) but you cannot tell it from a live peer, so never
  reclaim one silently — report it and stop. The one exception is deliberate and
  explicit: if *you* ran call 1, saw it report nothing already running, and the
  emulator came up unclaimed anyway, it is yours — claim it by hand with
  `adb -s <serial> shell setprop debug.jmw.owner "$ME"` so call 3 can clean it up.
- A warning that doesn't stop execution is not a guard: `adb -s ""` falls back
  to "the only attached device", so every step needing a serial is *gated*. And
  every call re-checks the SDK, because an unusable `adb` empties `ours()` and
  would let the audit report "clean" while eight cores burn.

```bash
# ── Preamble: run at the top of every call. Nothing carries between calls —
#    and nothing needs to. local.properties resolves from the repo root, not
#    $PWD, so a call from a subdirectory can't silently blank the SDK.
ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo .)
SDK=$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" 2>/dev/null); SDK=${SDK:-$ANDROID_HOME}
ADB="$SDK/platform-tools/adb"
AVD=Pixel_7_API_35
EMU="$SDK/emulator/emulator"
# Identity. This MUST be non-empty: an unclaimed emulator's owner reads as the
# empty string, so an empty $ME would make mine() match every unclaimed
# emulator on the machine — including a peer's freshly booted one — and call 3
# would kill it. mine() therefore refuses outright rather than trusting callers.
ME=$CLAUDE_CODE_SESSION_ID
ours()  { for s in $("$ADB" devices 2>/dev/null | awk '/^emulator-/ && $2=="device" {print $1}'); do
            [ "$("$ADB" -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')" = "$AVD" ] \
              && echo "$s"
          done; }
owner() { [ -n "$1" ] && "$ADB" -s "$1" shell getprop debug.jmw.owner 2>/dev/null | tr -d '\r'; }
# Empty identity owns nothing. This returns 0 so `$(mine)` stays usable in for/
# while loops — it is NOT a permission check, so every call still gates on
# [ -z "$ME" ] itself rather than reading this function's exit status.
mine()  { [ -n "$ME" ] || return 0
          for s in $(ours); do [ "$(owner "$s")" = "$ME" ] && echo "$s"; done; }
# Stop a set of PIDs: TERM, up to 10s to exit cleanly, then KILL what is left.
# Bounded — never blocks indefinitely — and it REPORTS rather than assuming: a
# PID that survives KILL (uninterruptible sleep, EPERM) must not look identical
# to a clean stop, or the audit inherits a lie.
stop_pids() { [ -n "$1" ] || return 0
  kill $1 2>/dev/null
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    left=""; for p in $1; do kill -0 "$p" 2>/dev/null && left="$left $p"; done
    [ -n "$left" ] || return 0
    sleep 1
  done
  for p in $left; do kill -0 "$p" 2>/dev/null && kill -9 "$p" 2>/dev/null; done
  sleep 1
  for p in $left; do kill -0 "$p" 2>/dev/null \
    && echo "!! survived KILL: $p — $AVD may still be running"; done; }
# Used by call 1's trap. Defined here, with the other helpers, so the trap can
# never outlive its handler if the block is edited or partially copied.
stop_emu() {
  if [ -n "${EMU_PID:-}" ]; then stop_pids "$EMU_PID"; return; fi
  # A signal can land in the instant between the fork and the EMU_PID
  # assignment. Scope to THIS shell's own children — a peer session's emulator
  # is never a child of ours, so it can't be caught. `ps -ww` because a
  # truncated line would drop the -avd argument, and here a missed match means
  # an orphan, not a cosmetic miss.
  kids=$(pgrep -P $$ 2>/dev/null)
  k=""; for p in $kids; do
    ps -ww -o command= -p "$p" 2>/dev/null | grep -qE -- "-avd $AVD( |$)" && k="$k $p"
  done
  # In most of that instant the child has forked but not yet exec'd, so it still
  # carries THIS shell's argv and the -avd match misses. Call 1 backgrounds
  # exactly one child and is only reachable when ours() was just empty, so
  # falling back to every child is correct here rather than merely convenient.
  [ -n "$k" ] || { k=$kids; [ -n "$k" ] && echo "note: no -avd match (pre-exec); stopping own children"; }
  stop_pids "$k"; }
echo "identity: ${ME:-<UNSET>}"     # every call prints it: never guess which scheme is live

# ── Call 1 (launch it with the harness's background-run mode): boot AND claim.
#    Alone, because it does not return until the emulator exits.
O=$(ours); FIRST=$(echo "$O" | head -1)      # sample once; two samples disagree
if [ ! -x "$ADB" ]; then
  echo "!! no adb at $ADB — set sdk.dir in local.properties or ANDROID_HOME; STOP"
elif [ ! -x "$EMU" ]; then
  echo "!! no emulator at $EMU — the SDK path is fine but the 'emulator'
        package isn't installed; STOP"
elif [ -z "$ME" ]; then
  echo "!! CLAUDE_CODE_SESSION_ID unset — an empty identity would match every
        unclaimed emulator, so call 3 would kill a peer's. STOP; set it first."
elif [ -n "$O" ]; then
  echo "!! $AVD is ALREADY running (owner='$(owner "$FIRST")'). STOP: do not
        boot, install, or kill. A peer session or an orphan — a human decides."
else
  # One account of the whole arrangement, in order:
  #
  # 1. The '&' is deliberate and scoped — NOT the forbidden "detach and forget"
  #    pattern. The emulator is backgrounded only so this call can claim it the
  #    moment adb answers (an emulator you booted but never claimed is one you
  #    can no longer kill). The `wait` at the end restores the blocking
  #    behaviour the harness's background-run mode expects.
  # 2. The claim loop below stops on either exit path: if the launch dies its
  #    `kill -0` test breaks the loop; if a signal arrives the trap's `exit`
  #    ends the script, so it can't setprop a device on its way out.
  # 3. Backgrounding costs the signal propagation a foreground command gets for
  #    free, hence the trap: without it, killing the call (timeout, interrupt,
  #    session end) orphans the emulator — and it's claimed, so only this
  #    now-dead session could ever have killed it.
  # 4. The trap is installed BEFORE the launch (its body is evaluated at signal
  #    time, so naming the not-yet-set PID is fine). Both of stop_emu's paths
  #    escalate through the same bounded stop_pids — TERM, up to 10s, then KILL
  #    — so neither can hang, and a survivor is reported rather than assumed
  #    away. A plain
  #    `wait` here would block forever on an emulator that ignores TERM.
  #
  # Verified: `emulator` EXECS into qemu-system rather than forking it (launcher
  # and qemu share one PID), so killing $EMU_PID reaches the process holding the
  # cores; a TERM to this shell left 0 qemu processes for the AVD.
  #
  # Residual, accepted: SIGKILL cannot be trapped, so a hard-killed call still
  # leaves a *claimed* emulator that no later session may touch (a new $ME never
  # matches the stale claim). Clear it by hand — see the recovery in the prose.
  trap 'stop_emu; exit 143' INT TERM HUP
  "$EMU" -avd "$AVD" -no-snapshot-save -no-audio -no-window & EMU_PID=$!
  for _ in $(seq 120); do
    kill -0 "$EMU_PID" 2>/dev/null || break          # launch died: stop claiming
    s=$(ours | head -1)
    [ -n "$s" ] && { "$ADB" -s "$s" shell setprop debug.jmw.owner "$ME"; break; }
    sleep 1
  done
  wait "$EMU_PID"
fi

# ── Call 2: build and install onto YOUR emulator — or do nothing at all. A
#    headless AVD needs ~60s and is invisible until it reaches 'device' state,
#    so poll before calling the boot a failure.
if [ ! -x "$ADB" ] || [ -z "$ME" ]; then
  echo "!! no SDK or no session id — STOP"
else
  for _ in $(seq 24); do S=$(mine | head -1); [ -n "$S" ] && break; sleep 5; done
  if [ -n "$S" ]; then
    "$ADB" -s "$S" shell getprop ro.build.version.release   # sanity-check the image
    ./gradlew :app:assembleDebug \
      && "$ADB" -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk
  else
    O=$(ours); FIRST=$(echo "$O" | head -1)  # only on the failure path; sample once
    if [ -n "$O" ]; then
      echo "!! $AVD is running but not claimed by you (owner='$(owner "$FIRST")').
            STOP — never install onto or kill a device you don't own. If you ran
            call 1 and it reported nothing running, this one IS yours: claim it
            by hand (see the prose) and re-run."
    else
      echo "!! $AVD never reached 'device' state in 2 min — the boot failed. STOP:
            adb -s '' would target whatever single device is attached."
    fi
  fi
fi

# ── Call 3: after verifying, kill what you own, let it go, then audit.
if [ ! -x "$ADB" ] || [ -z "$ME" ]; then
  echo "!! no SDK or no session id — cannot audit; STOP and do NOT assume the
        machine is clean. (This is the call that kills, so it is the one that
        must never run with an identity that matches unclaimed emulators.)"
else
  mine | while read -r s; do "$ADB" -s "$s" emu kill; done
  # adb lists a dying emulator for a beat, and its qemu process outlives that,
  # so settle BOTH before judging — otherwise a clean kill reads as wedged and
  # you learn to ignore the audit.
  for _ in 1 2 3 4 5 6 7 8 9 10; do [ -z "$(mine)" ] && break; sleep 2; done
  for _ in 1 2 3 4 5; do ps auxww | grep "[q]emu-system" | grep -qE -- "-avd $AVD( |$)" || break; sleep 2; done
  LEFT=$(mine); STRAY=$(ours)          # sample once: two samples can disagree
  if [ -n "$LEFT" ]; then
    echo "!! STILL RUNNING (yours): $(echo $LEFT) — re-run this call; if it
          persists, kill it by hand."
  elif [ -n "$STRAY" ]; then
    echo "note: $AVD is running but not claimed by you ($(echo $STRAY)) — leave
          it alone; a human resolves peer-vs-orphan."
  elif ps auxww | grep "[q]emu-system" | grep -qE -- "-avd $AVD( |$)"; then
    echo "!! a $AVD qemu process is alive but adb can't see it — wedged. Find
          the pid with:  ps auxww | grep '[q]emu-system' | grep -- '-avd $AVD'
          and kill only a line naming this AVD."
  else
    echo "clean — no $AVD emulator running"
  fi
fi
```

**Daemons are deliberately not killed.** Gradle and Kotlin daemons idle near 0%
CPU and reap themselves on an idle timeout, while every way of killing them is
wrong: `./gradlew --stop` and `pkill -f KotlinCompileDaemon` also stop Android
Studio's, costing the developer their warm build; filtering by JDK path
misjudges ownership in both directions; and a before/after PID diff blames you
for any daemon that merely *appeared* later — including one Studio spawned for a
mid-session sync. The emulator is the process worth chasing: it costs ~8 cores,
where a daemon costs some idle RAM. If you do want to look, `pgrep -f
'[G]radleDaemon|[K]otlinCompileDaemon'` lists them — informational only.

Two shell details worth knowing, both verified on this machine. **Never plain
`ps aux | grep`** — `ps` truncates to terminal width on a tty and silently drops
matches whose text trails a long classpath (3 daemons via a pipe, only 2 through
an 80-column terminal). Either `pgrep`, or `ps auxww` (the `ww` is what makes it
safe, and is why the audit above uses that form to match on the emulator's `-avd`
argument, which `pgrep -f` patterns handle less readably). Whichever you use,
**bracket the pattern** (`[q]emu-system`) so the pipeline can't match its own
shell: `ps auxww | grep` matched 3 processes for a sentinel that existed only in
the script text, whereas macOS `pgrep -f` matched 0. Don't rely on that
difference — Linux `pgrep` reads `/proc/*/cmdline` and *does* match the invoking
shell — so bracket everywhere and never pipe such a list into `kill`.

Rules that follow from this:

- **Boot per task, not per session.** A headless AVD boots in ~60s; that is
  cheaper than leaving one running for an hour. Never keep one "warm".
- **A "fresh" emulator is not a fresh install.** `-no-snapshot-save` only
  skips *saving*; call 1 still *loads* the AVD's quick-boot snapshot, and the
  app's data from an earlier session comes with it (verified 2026-10-03: a
  glance someone had rearranged passed for the shipped default and got filed
  as a layout bug — and captured as store screenshot 08). Before any
  first-run check or screenshot, `adb -s <serial> shell pm clear
  io.raylytics.justmyweather`.
- **Kill only your own AVD's emulator.** Never `xargs` a kill across every
  listed emulator and never hardcode `emulator-5554` — ports get reused, and the
  device on one may be another session's. Ownership comes from asking the
  emulator its AVD name, never from what a process looks like from outside: an
  agent building under Studio's bundled JBR is indistinguishable by JDK path, so
  such a filter both masks your own leak and flags processes that aren't yours.
- **Always target a device explicitly.** A bare `installDebug` or `adb install`
  fans out to *every* attached device, including a developer's personal phone
  and other projects' emulators. The unambiguous path is the two-step build and
  install in call 2 above (`./gradlew :app:assembleDebug`, then
  `adb -s <the serial from mine()> install -r
  app/build/outputs/apk/debug/app-debug.apk`).
  (`ANDROID_SERIAL` steers the `adb` CLI, but AGP's own install tasks talk to
  devices via ddmlib, so don't assume it constrains them; the
  `android.injected.device.serial` property could not be confirmed in the pinned
  AGP 8.7.3 artifacts, and Gradle silently ignores unknown `-P` flags — an
  unverified flag gives false confidence, which is how the fan-out happens.)
  **Maestro needs its own flag: `maestro --device <serial> test .maestro/`.**
  It ignores `ANDROID_SERIAL` and silently picks a device when several are
  attached — verified the hard way, running a suite against another session's
  emulator and an older build, which reported the new testTags as missing.
- **Prefer the JVM gate.** Unit tests + ktlint + assemble need no device at all;
  reach for an emulator only when the change is genuinely visual or runtime
  (insets, notifications, WorkManager, permissions).
- **Never run `connectedAndroidTest` against the developer's phone.** It
  UNINSTALLS the app when it finishes, and an uninstall takes the app's data:
  alert rules, the safety-notification opt-in, view config, the remembered
  reading, the point cache. Verified the hard way — a suite run "just to
  prove it works on hardware" wiped a real install and every setting on it.
  Run instrumented tests on the emulator; if a physical device is genuinely
  required, say so first, and reinstall afterwards knowing the data is gone.
  A plain `adb install -r` is the safe way to put a build on a phone.
  **Maestro is off-limits on the phone for the same reason**: every flow in
  `.maestro/` starts with `clearState: true`, which wipes the app's data.
- UI verification runs on **API 35+** (Android 15 enforces edge-to-edge for
  `targetSdk 35`; API 34 does not, and that gap once shipped a broken layout).
  A real device counts — instrumented tests run there again. They could not
  for a while: Espresso 3.5, which `compose-bom` drags in, resolves
  `android.hardware.input.InputManager.getInstance` by reflection and that
  method is gone in Android 17, so every test died at `Espresso.onIdle`
  before its body ran. `app/build.gradle.kts` therefore pins
  `espresso-core` and `test:runner` **ahead of the BOM**; don't drop those
  pins back to whatever the BOM offers without re-running the suite on a
  current device.

## iOS simulator hygiene

The same principle as the emulator rules above: leave the machine as you
found it. A booted simulator costs CPU and memory for as long as it runs.

- **This project's simulator is "JMW iPhone (iOS 27)"**: an iPhone 16 on the
  iOS 27 runtime. Find its UDID with
  `xcrun simctl list devices | grep 'JMW iPhone (iOS 27)'`. Use it and no
  other; never boot, shut down, erase or install onto another simulator.
- **Boot per task, shut down what you boot.** `xcrun simctl boot <udid>`,
  then `xcrun simctl shutdown <udid>` when you are done. `xcodebuild` with
  `-destination "id=<udid>"` boots the simulator by itself (a build, not
  only a test) and leaves it running: shut it down after that too. If it is already
  Booted when you start, you did not boot it: it may be a peer session's, so
  don't shut it down or install over it. Report it and let a human decide,
  as with an unclaimed emulator.
- **Never run the simulator while the Android emulator is up, and the other
  way round.** One device at a time; `:shared:allTests` counts as a
  simulator.
- **Run the iOS gate on a simulator whose iOS major matches the SDK** (iOS 27
  today). An older runtime can pass a build that the shipping SDK's runtime
  breaks; that is how API-34-vs-35 once shipped a broken layout on Android.
- **A fresh state is an uninstall.** Like the emulator's quick-boot
  snapshot, a simulator keeps the app's data between installs. Before any
  first-run check or screenshot: `xcrun simctl uninstall <udid>
  io.raylytics.justmyweather`, then install again. That also resets the
  location, notification and tracking answers. Maestro's `clearState: true`,
  which every flow starts with, does the same for a flow run.
- **Always target the device explicitly**: `-destination "id=<udid>"` for
  xcodebuild, the UDID for `simctl`, `maestro --device <udid>` (which
  `scripts/e2e.sh` does). Never `booted` as a device name: with two
  simulators up it means whichever one simctl picks.
- **Physical iPhones:** `scripts/ios/install-device.sh <udid>` installs over
  the existing app and keeps its data. Never run Maestro or `scripts/e2e.sh`
  on a phone: `clearState` wipes the app's data (`e2e.sh` refuses anything
  that is not a booted simulator).

## Architecture Overview

Kotlin Multiplatform + Compose Multiplatform + Material 3, MVVM, in three
modules: **`shared/`** (the app itself: `commonMain` runs on both platforms,
`androidMain`/`iosMain` hold the `actual`s and Kotlin twins), **`app/`** (the
Android host and Android-only services) and **`iosApp/`** (the SwiftUI host,
generated by XcodeGen, with Swift twins in `JustMyWeather/Platform/`). Manual
DI: one readable `AppGraph` (`shared/…/AppGraph.kt`) that Android's
`AppContainer` extends and iOS's `IosApp` builds — no Hilt. The packages below
are under `shared/src/commonMain/kotlin/io/raylytics/justmyweather/` unless
marked (app); `docs/architecture.md` has the seams and `docs/PARITY.md` every
platform difference:

- **`data/nws/`** — the weather backbone, ported from `almanac-bell`. `NwsClient`
  talks to the free public NWS API (api.weather.gov, no key); `HttpTransport`
  isolates the HTTP call so the retry/parse logic is unit-tested with no network;
  `Units` holds the pure conversions (°C→°F, mm→in, →mph, Pa→inHg, wind parsing);
  `NwsWire` is the JSON shapes, `NwsModels` the cleaned-up domain.
- **`data/`** — `WeatherRepository` is the single seam between NWS and the app
  (cache point resolution, fill labels). `WeatherSnapshot` is the one domain
  object the UI depends on — wire shapes never leak past the repository.
- **`ads/`** (app) + **`billing/`** — the banner and the purchase that removes it.
  `AdBanner` is the only composable that draws an ad (one, at the foot of the
  glance; personalization follows the user's consent, per `AdPolicy`);
  `RemoveAdsManager` keeps the on-device entitlement in step with Play behind
  the `BillingGateway` seam.
  See `docs/architecture.md`.
- **`data/metno/`** — MET Norway, the second source: the whole forecast for a
  place NWS doesn't cover (routed in `WeatherRepository`, which caches "not
  NWS territory" with the point), and days 8–9 at home.
- **`data/places/`** — the bundled gazetteer (US Census + GeoNames, with a
  country and zone per foreign place) and `PlaceLookup`, "nearest place to a
  coordinate" with no geocoder.
- **`region/`** — a region is a Google Play country (on iOS, an App Store
  storefront, mapped in `Storefronts.kt`). `Regions.kt` is the
  registry; `RegionResolver` picks the region (manual choice > phone network
  > last fix > place shown > language settings; the iPhone's order is in
  docs/REGIONS.md "On iOS"). It drives units, dates
  and clock. Whether ads may run is Google's UMP consent SDK's call
  (`ads/AdsConsent`), not the region's. **docs/REGIONS.md** has how it works and the
  step-by-step for adding, opening, pausing or removing a region. Never
  change the Play Console country list or App Store Connect's availability
  yourself — hand it off.
- **`view/Conventions.kt`** — the ONE place a canonical value (°F, mph, inHg,
  in) becomes display text; every formatter takes a `Conventions`, provided
  to Compose as `LocalConventions`. Data and alert thresholds stay canonical.
- **`location/`** — `LocationSource`; Android's `LocationProvider` (app) over
  the platform `LocationManager` (coarse only, no Play Services, so it builds
  from source anywhere).
- **`ui/theme/`** — the small palette + type scale, all in one place so the
  customization layer has obvious files to drive.
- **`ui/home/`** — the default glance: `HomeViewModel` exposes a single sealed
  `HomeUiState`; `HomeScreen` renders it.
- **`ui/settings/`** — App Settings: settings about the app rather than the
  canvas (the bug-report and idea entries; the Gadgetbridge hand-off, folded
  under an Advanced switch). Customize stays about what the glance shows and
  how it looks.
- **`widget/`** + **`ui/widget/`** (app; Android only) — home screen widgets: one glance tile
  each. `WidgetConfig` = a `ViewConfig` with one visible module + a
  `ThemeConfig`, so the glance's render, codecs and Customize pickers are
  reused; `WidgetRefreshWorker` (15 min, one fetch for all widgets) stores a
  `WidgetData` the Glance composition draws from via the per-widget state
  (`WidgetState` — never read stores in `provideGlance`, a live session won't
  re-run it). See `docs/architecture.md`.
- **`whatsnew/`** + **`ui/whatsnew/`** — the once-per-update "What's new"
  sheet over the glance. `WhatsNew` decides (pure: update vs fresh install,
  which releases), `ReleaseNotes` is the per-version catalog (strings in
  `strings.xml`), `WhatsNewRepository` stores the seen version. Decide each
  release's notes before the cut; docs/extending.md has the steps.
- **`support/`** — the two mails to the developer, ported from open-frame:
  `SupportMail.kt` builds them (pure, tested), `SupportIntents.kt` (app) reads the
  device and own logcat and hands off to the email app. A bug report carries
  full diagnostics; an idea (from the bottom of Customize, or App Settings)
  carries only a four-line context. `ui/support/SupportScreen` is the form for both.

Data flow: `HomeViewModel` → `WeatherRepository` → `NwsClient` → `HttpTransport`.

## Conventions & Patterns

- **Kotlin + Compose only.** Material 3. ktlint enforces style (config in
  `.editorconfig`: 120 cols, required trailing commas, Composables may be
  PascalCase). Run `ktlintFormat` before committing.
- **Pure logic stays pure.** Conversions and (later) the alert evaluator take
  data in, return data out — no I/O, no clock reads, no Android types — so they
  test on the JVM. Push side effects to the edges (transport, repository, VM).
- **One seam per concern.** Swapping the weather source or adding a cache happens
  only in `WeatherRepository`; the wire DTOs never escape `data/nws`.
- **Legibility is the spec.** This is going open source. Favor a well-named
  simple piece over a clever abstraction; comment the *why*. Adding a data point,
  an alert rule type, or a theme should be obvious — keep those extension points
  in one file each.
- **Tests:** JUnit 5 (`useJUnitPlatform`), Mockito-Kotlin, `runTest` for
  coroutines. Unit tests mirror the main package: `shared/src/commonTest/`
  (runs on the JVM and the iOS simulator), `shared/src/androidHostTest/`
  (JVM only), and `app/src/test/` for the Android-only code.

## Parity definition of done

A shared feature is **done** when:

1. `docs/PARITY.md` has its row: what Android does, what iOS does, the
   status, and the bd id of any gap. Any new `expect`/`actual` or platform
   twin also gets a row in the divergence ledger, justified or open (the
   file has the `git grep` that lists them).
2. Its Maestro flow passes on both platforms (`scripts/e2e.sh android` and
   `scripts/e2e.sh ios`), or carries a `when: platform:` block or an
   `android-only` tag with the reason written in the flow.

A feature that cannot exist on one platform is recorded as Deferred or
Dropped in `docs/PARITY.md`, not left out. "Fixed in shared" is not "shipped
on iOS": check `scripts/release-drift.sh`.

## Secrets

**Every credential an agent uses lives in 1Password.** API keys, tokens, DSNs, OAuth client secrets, webhook URLs, signing keys: if Claude Code, a cron job, an MCP server, or CI needs it, 1Password is the source of truth, and anything else (a local `.env`, SSM, GitHub Secrets, a server-side env file) is a derived copy seeded from it. This applies to the credentials this repo already uses, not only to new ones.

- **Reference, don't copy.** Tracked files carry env var *names* and `op://<vault>/<item>/<field>` references, never values. Load at run time with `op run --env-file=op.env -- <cmd>` (an `op.env` of `op://` lines, gitignored) or `op read "op://..."` piped straight into the consumer. This repo's release scripts read `key=value` properties files instead (`app/keystore.properties`, `iosApp/team.properties`, repo-root `play.properties`, each with a tracked `.example`); those files are the derived copies here, and env vars override them.
- **Name items `<repo>-<service>`** (for example `mc-epn`), one item per service, fields named after the env vars. Use the 1Password account that owns the work (personal `my.1password.com`, VisionWrights, or Newtack) and pass `--account` when more than one is signed in.
- **A literal `.env` is debt, not a pattern.** It must be gitignored and `chmod 600`, and the inventory below marks it as pending migration. Move the values into 1Password the next time you touch them. Key *files* (`app/release.keystore`, `AuthKey_*.p8`, `play-service-account.json`) belong in 1Password as document attachments of the item that uses them; `docs/PLAY_STORE.md`'s "back up in the password manager" means that item.
- **Never print a secret.** Prove a key works by calling the API (`scripts/android/preflight-play.sh` does exactly that for Play). Don't `cat` env files, echo variables, or paste values into transcripts, commit messages, or this file.
- **Found a literal secret in a tracked file or in history?** Stop and tell the owner. It needs rotating and purging, and that is their call.
- **Adding a credential:** create the 1Password item, add the `op://` line to `op.env.example` (or this repo's equivalent template), add a row below, and confirm the local copy is ignored.

### Credentials this repo uses

| Env var | Used by / purpose | Where it lives today | 1Password item |
|---|---|---|---|
| `storePassword`, `keyPassword` (with `keyAlias`, `storeFile`) | Release signing in `app/build.gradle.kts`; `scripts/android/bundle-release.sh` refuses to build without them | gitignored `app/keystore.properties`, literal values; `app/release.keystore` beside it (migrate) | `jmw-android-signing` (to create) |
| `IOS_ASC_KEY_ID`, `IOS_ASC_ISSUER_ID`, `IOS_ASC_KEY_PATH` (`ascKeyId` / `ascIssuerId` / `ascKeyPath`) | `scripts/ios/*`: TestFlight upload, profile creation (ASC API) | gitignored `iosApp/team.properties`, literal values; `.p8` in `~/.appstoreconnect/private_keys/` outside the repo (migrate) | `jmw-asc` (to create; if it is open-frame's key, share `open-frame-asc`) |
| `PLAY_SERVICE_ACCOUNT` (`serviceAccountJsonPath`) | `scripts/android/upload-play.sh`, `promote-play.sh`, `preflight-play.sh`, `play-countries.sh` | path in gitignored repo-root `play.properties`; JSON key at repo-root `play-service-account.json`, gitignored (migrate) | `jmw-play` (to create; if it is open-frame's service account, share `open-frame-play`) |

Not secrets, stay where they are: `IOS_TEAM_ID` (`teamID`), Play `track` / `packageName`, the AdMob ids in gitignored `app/admob.properties` (`appId`, `bannerUnitId`), `local.properties` (SDK path). NWS and MET Norway need no key.

<!-- BEGIN WYK CONVENTIONS v:1 -->
## wyk — planning & handoff over bd

This repo uses **wyk**, a view + handoff layer over **bd (beads)**. "Plan
it in wyk" = **file the plan as bd issues** (deps via `bd dep add`), not
markdown/TodoWrite. File with **`wyk create`** (same flags as `bd create`,
forwarded verbatim) — it also stamps the Claude session so the TUI's
Session column traces work back to a conversation. A PreToolUse hook
blocks raw `bd create` and tells you to switch; that's expected — just
re-run as `wyk create`.

**Owner column** — whose move it is, label-driven (NOT bd's owner/assignee):
- `human` → **HUMAN** (a human must act).
- `agent-handoff` → **AGENT-HANDOFF**: another agent owns it; don't touch,
  a human coordinates. Excluded from `wyk inbox`.
- agent task blocked by a `human`-flagged dep → **HUMAN-BLOCK** (skip it).
- else → **AGENT** (the default; a null owner is never blank — so a task
  that needs a human MUST be handed off, or the human never sees it).

**Hand off to a human**: `wyk handoff <id>` (or `wyk handoff -create "<title>"`)
sets `human` + writes the runbook. Never hand-roll labels; `-a`/`--claim`
are bd's status, not the badge.

**Pick up work**: `wyk inbox` FIRST (items bounced back to you — WORK them),
then `wyk` / `bd ready`. `wyk conventions` prints the full contract.

**Something wrong? Act — don't shrug.** If a wyk/bd command errors, a
convention looks broken, or the workflow rubs wrong, file a bd issue (with
an owner) and fix or hand it off — don't route around it silently.
Friction with wyk is product data; surfacing it is the job.
<!-- END WYK CONVENTIONS -->
