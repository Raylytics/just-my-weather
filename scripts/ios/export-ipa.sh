#!/usr/bin/env bash
# Export an IPA from $BUILD_DIR/JustMyWeather.xcarchive.
#
# Usage:
#   scripts/ios/export-ipa.sh --method app-store-connect  # TestFlight / App Store
#   scripts/ios/export-ipa.sh --method development        # sideload to your team's devices
#   scripts/ios/export-ipa.sh --method ad-hoc             # ad-hoc test distribution
#
# `app-store` is accepted as an alias for `app-store-connect` (open-frame's
# spelling; Xcode 15.3 renamed the method and still accepts the old one with a
# deprecation warning).
#
# Generates a temporary ExportOptions.plist with the requested method
# (the committed iosApp/ExportOptions.plist stays as-is — app-store-connect,
# manual — so we don't churn it on every release).

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

method=""
while [ $# -gt 0 ]; do
  case "$1" in
    --method) shift; method="${1:-}"; [ $# -gt 0 ] && shift ;;
    --method=*) method="${1#--method=}"; shift ;;
    *) die "unknown arg: $1 (usage: --method <app-store-connect|development|ad-hoc>)" ;;
  esac
done

case "$method" in
  app-store) method="app-store-connect" ;;
  development|ad-hoc|app-store-connect) ;;
  "") die "missing --method <app-store-connect|development|ad-hoc>" ;;
  *) die "invalid --method '$method' — must be app-store-connect, development, or ad-hoc" ;;
esac

ARCHIVE_PATH="$BUILD_DIR/JustMyWeather.xcarchive"
EXPORT_PATH="$BUILD_DIR/export"

if [ ! -d "$ARCHIVE_PATH" ]; then
  die "archive not found at $ARCHIVE_PATH — run scripts/ios/archive.sh first"
fi

# Build a temporary ExportOptions plist with the requested method and this
# machine's team. The committed file keeps its REPLACE_WITH_TEAM_ID
# placeholder on purpose (no developer's team id in the repo, and a release
# tree that stays clean); the team comes from Team.xcconfig, which
# setup-team.sh writes and the archive was signed with.
if [ ! -f "$EXPORT_OPTIONS" ]; then die "$EXPORT_OPTIONS not found"; fi
team="$(read_team_xcconfig)"
if [ -z "$team" ] || [ "$team" = "REPLACE_WITH_TEAM_ID" ]; then
  die "no signing team in $TEAM_XCCONFIG. Run: scripts/ios/setup-team.sh"
fi

tmp_options=$(mktemp -t justmyweather-export-options.XXXXXX)
# shellcheck disable=SC2064
trap "rm -f '$tmp_options'" EXIT

# For app-store-connect, force signingStyle=manual and pin the
# provisioning profile by name. Why: with signingStyle=automatic
# xcodebuild prefers Apple's cloud-signing path which an App
# Manager-role ASC API key isn't permitted to drive (it surfaces as
# "Cloud signing permission error" + "No profiles for X were found"
# even when a valid profile exists in
# ~/Library/MobileDevice/Provisioning Profiles/). Manual signing
# tells xcodebuild "use this exact profile from disk, don't talk to
# Apple's signing service" — which matches what we want for
# CI-style uploads. (Learned on open-frame's first TestFlight upload.)
#
# Profile name lives in scripts/ios/common.sh as
# $IOS_APP_STORE_PROFILE_NAME so create-profile.sh and this
# script stay in sync. The profile is created on first release via
# scripts/ios/create-profile.sh.
#
# Deliberate departure from open-frame: open-frame pinned the APP STORE
# profile for ad-hoc exports too, but an ad-hoc export needs an ad-hoc
# profile (it carries a device list) and would fail at export. Until this
# project has one, ad-hoc goes the automatic route with development.
if [ "$method" = "app-store-connect" ]; then
  awk -v method="$method" -v profile="$IOS_APP_STORE_PROFILE_NAME" -v bundle="$APP_ID" -v team="$team" '
    /<key>teamID<\/key>/ { print; getline; sub(/<string>[^<]*<\/string>/, "<string>" team "</string>"); print; next }
    /<key>signingStyle<\/key>/ { print; getline; sub(/<string>[^<]*<\/string>/, "<string>manual</string>"); print; next }
    /<key>method<\/key>/ {
      print; getline; sub(/<string>[^<]*<\/string>/, "<string>" method "</string>"); print
      print "    <key>provisioningProfiles</key>"
      print "    <dict>"
      print "        <key>" bundle "</key>"
      print "        <string>" profile "</string>"
      print "    </dict>"
      next
    }
    { print }
  ' "$EXPORT_OPTIONS" > "$tmp_options"

  # Belt-and-suspenders: the awk substitution above relies on
  # `<key>signingStyle</key>` being present in the committed
  # ExportOptions.plist. If a future cleanup ever drops the key
  # (it defaults to "automatic" implicitly), the `manual` flip would
  # silently no-match and we'd be back to the cloud-signing path
  # this whole branch is meant to avoid. Assert loudly here so the
  # failure surfaces in the script, not 30 seconds later as a
  # mysterious xcodebuild "Cloud signing permission error".
  if ! grep -q "<string>manual</string>" "$tmp_options"; then
    die "failed to set signingStyle=manual in temp ExportOptions \
(check that iosApp/ExportOptions.plist still contains a <key>signingStyle</key> entry)"
  fi
  if ! grep -q "<key>$APP_ID</key>" "$tmp_options"; then
    die "failed to inject <provisioningProfiles> entry for $APP_ID"
  fi
else
  # The committed file says manual (for the release path); manual with no
  # pinned profile cannot export development/ad-hoc, so flip it to automatic.
  awk -v method="$method" -v team="$team" '
    /<key>teamID<\/key>/ { print; getline; sub(/<string>[^<]*<\/string>/, "<string>" team "</string>"); print; next }
    /<key>signingStyle<\/key>/ { print; getline; sub(/<string>[^<]*<\/string>/, "<string>automatic</string>"); print; next }
    /<key>method<\/key>/ { print; getline; sub(/<string>[^<]*<\/string>/, "<string>" method "</string>"); print; next }
    { print }
  ' "$EXPORT_OPTIONS" > "$tmp_options"
fi

# Verify the team and method substitutions landed in either branch.
if ! grep -q "<string>$team</string>" "$tmp_options"; then
  die "failed to set <teamID> = $team in temporary export options"
fi
if ! grep -q "<string>$method</string>" "$tmp_options"; then
  die "failed to set <method> = $method in temporary export options"
fi

echo "==> Cleaning $EXPORT_PATH" >&2
rm -rf "$EXPORT_PATH"
mkdir -p "$EXPORT_PATH"

echo "==> Exporting IPA (method=$method)" >&2

# Build optional flag set per signing mode:
#   - development / ad-hoc: keep -allowProvisioningUpdates + ASC API
#     auth so xcodebuild can fetch / generate a profile on demand for
#     fresh setups (matches the archive.sh behavior).
#   - app-store-connect: we use signingStyle=manual with an explicit
#     provisioningProfiles pin (see awk block above), so xcodebuild
#     reads the profile straight from disk and shouldn't touch Apple's
#     signing service. Drop -allowProvisioningUpdates in that case —
#     passing it can re-enable cloud signing in some xcodebuild
#     versions, which is the exact path that fails for an App
#     Manager-role ASC key. ASC auth flags are also dropped since
#     manual signing has no use for them.
extra_args=()
if [ "$method" != "app-store-connect" ]; then
  extra_args+=(-allowProvisioningUpdates)
  asc_auth_args
  extra_args+=(${ASC_AUTH_ARGS[@]+"${ASC_AUTH_ARGS[@]}"})
fi

xcodebuild -exportArchive \
  -archivePath "$ARCHIVE_PATH" \
  -exportPath "$EXPORT_PATH" \
  -exportOptionsPlist "$tmp_options" \
  ${extra_args[@]+"${extra_args[@]}"} \
  | grep -E "^(===|Export Succeeded|error:|\*\* )" || true

# Find the produced IPA — Xcode names it after the product.
IPA_PATH=$(find "$EXPORT_PATH" -maxdepth 2 -name "*.ipa" -print -quit)
if [ -z "$IPA_PATH" ]; then
  die "export failed — no .ipa produced under $EXPORT_PATH. \
Re-run xcodebuild -exportArchive without the grep filter to see full output."
fi

echo "==> IPA ready: $IPA_PATH" >&2
echo "$IPA_PATH"
