#!/usr/bin/env bash
# Ask App Store Connect whether it would accept an IPA, without uploading
# it: `altool --validate-app` runs the same checks the upload runs
# (bundle Info.plist rules, SDK framework plists, signing), so a rejection
# surfaces here, before a build number is spent on it. Added 2026-11-04
# after build 4 was rejected at upload for the Appodeal frameworks' plists
# (scripts/ios/fix-framework-plists.sh). Nothing is uploaded; safe for an
# agent to run; the rehearsal runs it after the export.
#
#   scripts/ios/validate-ipa.sh [--allow-used-build-number] [path/to/App.ipa]
#
# --allow-used-build-number is for the rehearsal (release-testflight.sh with
# no flag): its IPA carries the build number of the last cut, which App
# Store Connect has already seen, so the validator reports a redundant
# binary (ITMS-90189). With the flag that one error, alone, is a warning;
# any other error still fails. The real cut validates without it, just
# after the bump, where a used number is a genuine error (roborev 6954).
#
# Credentials as for upload-testflight.sh: iosApp/team.properties
# (ascKeyId, ascIssuerId, ascKeyPath), env vars override.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"
load_team_properties
require_vars IOS_ASC_KEY_ID IOS_ASC_ISSUER_ID

allow_used_build=0
if [ "${1:-}" = "--allow-used-build-number" ]; then
  allow_used_build=1
  shift
fi
IPA_PATH="${1:-}"
if [ -z "$IPA_PATH" ]; then
  IPA_PATH=$(find "$BUILD_DIR/export" -maxdepth 2 -name "*.ipa" -print -quit 2>/dev/null || true)
fi
if [ -z "$IPA_PATH" ] || [ ! -f "$IPA_PATH" ]; then
  die "no IPA found. Pass a path or run scripts/ios/export-ipa.sh --method app-store-connect first."
fi

stage_asc_key

echo "==> Validating $IPA_PATH with App Store Connect (no upload)" >&2
log=$(mktemp -t justmyweather-altool-validate.XXXXXX.log)
# shellcheck disable=SC2064
trap "rm -f '$log'" EXIT
set +e
xcrun altool --validate-app \
  --type ios \
  --file "$IPA_PATH" \
  --apiKey "$IOS_ASC_KEY_ID" \
  --apiIssuer "$IOS_ASC_ISSUER_ID" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
set -e
# altool can exit 0 with errors in the text (as the upload does).
if [ "$status" -ne 0 ] || grep -qiE "VALIDATION FAILED|error:|Failed to validate" "$log"; then
  # The rehearsal's only acceptable error: the build number is already
  # uploaded. Every error line must be that one; anything else fails.
  redundant='ITMS-90189|Redundant Binary|already uploaded a build'
  errors=$(grep -iE "ERROR ITMS|error:" "$log" || true)
  others=$(printf '%s\n' "$errors" | grep -viE "$redundant" | grep -v '^$' || true)
  if [ "$allow_used_build" = 1 ] && [ -n "$errors" ] && [ -z "$others" ]; then
    echo >&2
    echo "==> WARNING: this build number is already on App Store Connect (expected in a rehearsal);" >&2
    echo "    no other validation error. The real cut bumps the number and validates strictly." >&2
    exit 0
  fi
  echo >&2
  echo "==> App Store Connect would REJECT this IPA — see errors above. Nothing was uploaded." >&2
  exit 1
fi
echo "==> App Store Connect validation passed (nothing uploaded)." >&2
