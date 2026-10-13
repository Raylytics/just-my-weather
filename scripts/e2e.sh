#!/usr/bin/env bash
# Run the Maestro suite (.maestro/) on ONE explicit device, either platform.
#
#   scripts/e2e.sh android <emulator-serial>     # e.g. emulator-5554
#   scripts/e2e.sh ios     <simulator-udid>      # from `xcrun simctl list devices booted`
#
# The device id is required, never guessed. CLAUDE.md "Always target a
# device explicitly": with several devices attached Maestro silently picks
# one — this project has already run a suite against another session's
# emulator that way — and it ignores ANDROID_SERIAL entirely. So the id is
# an argument, and it is passed through as `maestro --device <id>`.
#
# Emulators and simulators only. Every flow starts with `clearState: true`,
# which wipes the app's data (alert rules, view config, the opt-ins), and the
# Android path also runs connectedDebugAndroidTest, which UNINSTALLS the app
# when it finishes. Neither belongs on a real phone (CLAUDE.md "Never run
# connectedAndroidTest against the developer's phone").
#
# This script boots nothing and kills nothing: bring the device up first (on
# Android, by CLAUDE.md's three-call procedure, which also tells you which
# emulator is yours) and shut it down after.
#
#   android: delegates to .github/scripts/ui-flows.sh <serial>, the exact
#            path CI's ui job runs — instrumented tests, install, the flows
#            with one retry, then the ANR/crash logcat check. Build the APK
#            first: ./gradlew :app:assembleDebug
#   ios:     installs nothing. The app must already be on the simulator
#            (the commands are printed if it isn't); then the flows run.
#
# The app id is the same on both platforms and is injected as APP_ID
# (`-e APP_ID=...`) so a flow can say `appId: ${APP_ID}`.

set -euo pipefail

APP_ID="io.raylytics.justmyweather"

usage() {
  cat >&2 <<EOF
usage: $(basename "$0") android <emulator-serial>
       $(basename "$0") ios <simulator-udid>

A device id is required: Maestro picks a device silently when none is
named, and that has already run a suite on the wrong one (CLAUDE.md
"Always target a device explicitly").
  android: adb devices            (an emulator-NNNN serial you own)
  ios:     xcrun simctl list devices booted
EOF
  exit 2
}

[ $# -eq 2 ] || usage
PLATFORM="$1"
DEVICE="$2"
[ -n "$DEVICE" ] || usage

# Assignment form: inside `cd "$(...)"` a failed substitution would leave a
# silent `cd ""` and the run would carry on from the wrong directory.
ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"

command -v maestro >/dev/null 2>&1 || {
  echo "error: maestro not found — curl -Ls https://get.maestro.mobile.dev | bash" >&2
  exit 1
}

case "$PLATFORM" in
  android)
    # Emulator serials only — a phone's serial is a bare hex string. See the
    # header: clearState and the instrumented tests' uninstall wipe data.
    case "$DEVICE" in
      emulator-*) ;;
      *)
        echo "error: '$DEVICE' is not an emulator serial. The suite wipes the app's data; it never runs on a phone." >&2
        exit 1
        ;;
    esac
    exec bash "$ROOT/.github/scripts/ui-flows.sh" "$DEVICE"
    ;;

  ios)
    command -v xcrun >/dev/null 2>&1 || { echo "error: xcrun not found — install Xcode" >&2; exit 1; }

    # Must be a BOOTED SIMULATOR. A physical iPhone's UDID won't appear in
    # simctl's list, which is the point: the flows' clearState would wipe a
    # real install. Captured, then matched — not `simctl | grep -q`, whose
    # early exit can SIGPIPE the producer and read as "not found" under
    # pipefail (.github/scripts/ui-flows.sh has the long version).
    booted=$(xcrun simctl list devices booted 2>/dev/null || true)
    case "$booted" in
      *"($DEVICE)"*) ;;
      *)
        echo "error: $DEVICE is not a booted simulator. Booted ones:" >&2
        printf '%s\n' "$booted" | grep -E '\(Booted\)' >&2 || echo "  (none)" >&2
        exit 1
        ;;
    esac

    # Installed? get_app_container is read-only and fails when it isn't.
    if ! xcrun simctl get_app_container "$DEVICE" "$APP_ID" >/dev/null 2>&1; then
      cat >&2 <<EOF
error: $APP_ID is not installed on simulator $DEVICE. This script installs
nothing on iOS; build and install it first:

  (cd iosApp && xcodegen generate)
  xcodebuild build -project iosApp/JustMyWeather.xcodeproj -scheme JustMyWeather \\
    -configuration Debug -destination "id=$DEVICE" -derivedDataPath build/ios/DerivedData
  xcrun simctl install $DEVICE build/ios/DerivedData/Build/Products/Debug-iphonesimulator/JustMyWeather.app
EOF
      exit 1
    fi

    echo "device: $DEVICE (iOS simulator)"
    exec maestro --device "$DEVICE" test -e APP_ID="$APP_ID" .maestro/
    ;;

  *)
    usage
    ;;
esac
