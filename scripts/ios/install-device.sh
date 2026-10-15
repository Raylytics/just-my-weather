#!/usr/bin/env bash
# Build Debug for a connected iPhone and install it there.
#
# Usage:
#   scripts/ios/install-device.sh <device-udid>
#   scripts/ios/install-device.sh            # lists devices, then stops
#
# The device must be named. CLAUDE.md "Always target a device explicitly":
# picking "the first connected device" is how a build lands on the wrong
# phone. Run with no argument to see what's attached.
#
# Unlike open-frame (React Native: Metro served the JS, `react-native run-ios`
# did the build), this app has no bundler — the shared Kotlin framework is
# compiled into the app by the target's pre-build Gradle phase — so this is a
# plain xcodebuild + `xcrun devicectl device install app`.
#
# Installing over an existing copy keeps the app's data (alert rules, view
# config), like `adb install -r`. Deleting the app on the phone does not.
#
# Needs: Xcode 15+ (devicectl), a signing team (scripts/ios/setup-team.sh),
# and the device registered with the team (scripts/ios/register-device.sh).

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

cd "$PROJECT_ROOT"

if ! command -v xcrun >/dev/null 2>&1; then
  die "xcrun not found — install Xcode + command-line tools"
fi
if ! xcrun devicectl --version >/dev/null 2>&1; then
  die "xcrun devicectl not available — it ships with Xcode 15 or newer"
fi

DEVICE="${1:-}"
if [ -z "$DEVICE" ]; then
  echo "==> Connected devices (pass the UDID / Identifier of the one to use):" >&2
  xcrun devicectl list devices >&2 || true
  die "usage: $(basename "$0") <device-udid>"
fi
[ $# -le 1 ] || die "usage: $(basename "$0") <device-udid> (one device only)"

# 1. Verify that device is actually connected. devicectl's list is the
#    modern (Xcode 15+) source; matching the id the caller gave guards
#    against a typo that xcodebuild would report far less clearly. The
#    leading boundary keeps "unavailable" / "disconnected" from matching.
#    Captured first, not piped into `grep -q`: under pipefail an early grep
#    exit SIGPIPEs devicectl and the pipeline reads as "not found"
#    (.github/scripts/ui-flows.sh has the long version of that lesson).
echo "==> Looking for $DEVICE…" >&2
devices_out=$(xcrun devicectl list devices 2>/dev/null || true)
device_line=$(printf '%s\n' "$devices_out" | grep -F -- "$DEVICE" || true)
if ! printf '%s\n' "$device_line" | grep -E "(^|[[:space:]])(connected|available)" >/dev/null; then
  cat >&2 <<EOF
error: $DEVICE is not listed as connected.

Checklist:
  1. Cable connected (or Wi-Fi pair set up in Xcode → Devices and Simulators).
  2. Phone unlocked.
  3. Phone has trusted this computer (look for the "Trust This Computer?"
     prompt; tap Trust + enter passcode).
  4. iOS 16+: Settings → Privacy & Security → Developer Mode → On
     (then reboot the phone).

Run \`xcrun devicectl list devices\` to debug.
EOF
  exit 1
fi

# 2. Generate the project and check signing before a long build.
xcodegen_generate
require_team_configured
if [ -f "$TEAM_PROPERTIES" ] || [ -n "${IOS_TEAM_ID:-}" ]; then
  load_team_properties
fi
asc_auth_args

# 3. Build. A fixed -derivedDataPath so the .app's location is known rather
#    than hunted for in ~/Library/Developer/Xcode/DerivedData.
#    -allowProvisioningUpdates: automatic Development signing, which creates
#    the development profile for a newly registered device on demand.
DERIVED="$BUILD_DIR/DerivedData"
APP_PATH="$DERIVED/Build/Products/Debug-iphoneos/JustMyWeather.app"
echo "==> Building Debug for $DEVICE" >&2
rm -rf "$APP_PATH"
xcodebuild build \
  -project "$XCODEPROJ" \
  -scheme "$SCHEME" \
  -configuration Debug \
  -destination "id=$DEVICE" \
  -derivedDataPath "$DERIVED" \
  -allowProvisioningUpdates \
  ${ASC_AUTH_ARGS[@]+"${ASC_AUTH_ARGS[@]}"} \
  | grep -E "^(===|error:|\*\* )" || true

if [ ! -d "$APP_PATH" ]; then
  die "build failed — $APP_PATH was not produced. \
Run xcodebuild without the grep filter to see full output."
fi

# 4. Install, then launch so a successful run ends with the app on screen.
echo "==> Installing on $DEVICE" >&2
xcrun devicectl device install app --device "$DEVICE" "$APP_PATH"
echo "==> Launching $APP_ID" >&2
xcrun devicectl device process launch --device "$DEVICE" --terminate-existing "$APP_ID" \
  || echo "note: installed, but the launch failed (phone locked?) — open it by hand" >&2
