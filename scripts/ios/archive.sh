#!/usr/bin/env bash
# Build a Release archive at $BUILD_DIR/JustMyWeather.xcarchive
# (build/ios/JustMyWeather.xcarchive).
#
# Cleans the archive dir first to avoid stale leftovers (Xcode will
# happily re-use a partial archive and produce confusing IPAs).
#
# Pre-flight: regenerate the Xcode project from iosApp/project.yml (it is
# not committed), then check the signing team is set — run
# scripts/ios/setup-team.sh first if you haven't.
#
# The shared Kotlin framework is not built here: the target's pre-build
# script phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`,
# so a Release archive compiles the Release framework itself. That makes the
# first archive slow (Kotlin/Native release linking), not stuck.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

cd "$PROJECT_ROOT"

ARCHIVE_PATH="$BUILD_DIR/JustMyWeather.xcarchive"

xcodegen_generate

# Sanity: the team must reach the JustMyWeather target. open-frame grepped the
# pbxproj for it; here the pbxproj is generated, so check the inputs that feed
# it (Team.xcconfig, the includes, and nothing in project.yml shadowing them).
require_team_configured

# Clean the archive dir.
echo "==> Cleaning $ARCHIVE_PATH" >&2
rm -rf "$ARCHIVE_PATH"
mkdir -p "$BUILD_DIR"

# Archive. -allowProvisioningUpdates lets Xcode auto-fetch / generate
# the right provisioning profile for the bundle ID + team — without
# it, a fresh App ID like io.raylytics.justmyweather fails with "No
# profiles for 'X' were found" because the local Xcode hasn't seen
# this bundle ID before. Equivalent to ticking "Automatically manage
# signing" in the Xcode UI. (The archive is development-signed; the
# App Store signature is applied at export, with MANUAL signing — see
# export-ipa.sh for why the two steps differ.)
#
# The -authenticationKey* flags pass the App Store Connect API key
# creds so xcodebuild can call ASC's API directly to register the
# new bundle ID's Distribution certificate and provisioning profile,
# instead of failing because the local keychain doesn't have one
# yet. Same key info is used downstream by altool for upload — see
# upload-testflight.sh.
#
# team.properties is optional here: the team itself comes from
# Team.xcconfig, and the ASC key only smooths provisioning.
if [ -f "$TEAM_PROPERTIES" ] || [ -n "${IOS_TEAM_ID:-}" ]; then
  load_team_properties
fi

echo "==> Archiving (Release configuration)" >&2
asc_auth_args

# -project, never -workspace: there is no workspace (no CocoaPods; the Swift
# packages resolve through the project itself).
xcodebuild archive \
  -project "$XCODEPROJ" \
  -scheme "$SCHEME" \
  -configuration Release \
  -archivePath "$ARCHIVE_PATH" \
  -destination "generic/platform=iOS" \
  -allowProvisioningUpdates \
  ${ASC_AUTH_ARGS[@]+"${ASC_AUTH_ARGS[@]}"} \
  | grep -E "^(===|Archive Succeeded|error:|\*\* )" || true

# The grep above hides xcodebuild's exit status (and `|| true` keeps a
# no-match grep from killing the script), so the archive directory is the
# verdict.
if [ ! -d "$ARCHIVE_PATH" ]; then
  die "archive failed — $ARCHIVE_PATH was not created. \
Run xcodebuild archive without the grep filter to see full output."
fi

echo "==> Archive ready: $ARCHIVE_PATH" >&2
