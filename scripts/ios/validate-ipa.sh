#!/usr/bin/env bash
# Ask App Store Connect whether it would accept an IPA, without uploading
# it: `altool --validate-app` runs the same checks the upload runs
# (bundle Info.plist rules, SDK framework plists, signing), so a rejection
# surfaces here, before a build number is spent on it. Added 2026-11-04
# after build 4 was rejected at upload for the Appodeal frameworks' plists
# (scripts/ios/fix-framework-plists.sh). Nothing is uploaded; safe for an
# agent to run; the rehearsal runs it after the export.
#
#   scripts/ios/validate-ipa.sh [path/to/App.ipa]
#
# Always strict. The rehearsal archives with the next, unused build number
# (archive.sh JMW_ARCHIVE_BUILD_NUMBER), so App Store Connect runs every
# check instead of stopping at a build number it has already seen
# (roborev 6954, 7010).
#
# Credentials as for upload-testflight.sh: iosApp/team.properties
# (ascKeyId, ascIssuerId, ascKeyPath), env vars override.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"
load_team_properties
require_vars IOS_ASC_KEY_ID IOS_ASC_ISSUER_ID

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
  echo >&2
  echo "==> App Store Connect would REJECT this IPA — see errors above. Nothing was uploaded." >&2
  exit 1
fi
echo "==> App Store Connect validation passed (nothing uploaded)." >&2
