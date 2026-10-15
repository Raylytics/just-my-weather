#!/usr/bin/env bash
# Upload the IPA at $BUILD_DIR/export/*.ipa to App Store Connect /
# TestFlight, using App Store Connect API key auth (no Apple ID
# password, no 2FA prompts).
#
# Required env / properties:
#   - IOS_ASC_KEY_ID        from team.properties (10-char string)
#   - IOS_ASC_ISSUER_ID     from team.properties (UUID)
#   - IOS_ASC_KEY_PATH      optional, see below
#
# altool searches these conventional paths for AuthKey_<KEY_ID>.p8:
#   ./private_keys
#   ~/private_keys
#   ~/.private_keys
#   ~/.appstoreconnect/private_keys
# If your .p8 lives elsewhere, set ascKeyPath in team.properties.
#
# Usually reached through release-testflight.sh. Run directly, this UPLOADS
# whatever IPA it finds: agents need Evan's go-ahead for THIS upload, in the
# current exchange (CLAUDE.md "Release confirmation gate"), exactly as for
# the orchestrator.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

load_team_properties
require_vars IOS_ASC_KEY_ID IOS_ASC_ISSUER_ID

# Find the IPA. Prefer an explicit arg; otherwise discover under build/ios/export.
IPA_PATH="${1:-}"
if [ -z "$IPA_PATH" ]; then
  IPA_PATH=$(find "$BUILD_DIR/export" -maxdepth 2 -name "*.ipa" -print -quit 2>/dev/null || true)
fi
if [ -z "$IPA_PATH" ] || [ ! -f "$IPA_PATH" ]; then
  die "no IPA found. Pass a path or run scripts/ios/export-ipa.sh --method app-store-connect first."
fi

# If ascKeyPath is set, copy the .p8 into a place altool will find.
# We can't pass a path directly to altool's --apiKey; it always looks
# in conventional dirs. So we ensure ~/.appstoreconnect/private_keys/
# has the right file.
if [ -n "${IOS_ASC_KEY_PATH:-}" ]; then
  expanded_path="${IOS_ASC_KEY_PATH/#\~/$HOME}"
  if [ ! -f "$expanded_path" ]; then
    die "ascKeyPath set to '$IOS_ASC_KEY_PATH' but file not found at '$expanded_path'"
  fi
  target_dir="$HOME/.appstoreconnect/private_keys"
  target_file="$target_dir/AuthKey_${IOS_ASC_KEY_ID}.p8"
  if [ ! -f "$target_file" ] || ! cmp -s "$expanded_path" "$target_file"; then
    mkdir -p "$target_dir"
    cp "$expanded_path" "$target_file"
    chmod 600 "$target_file"
    echo "==> Copied .p8 to $target_file" >&2
  fi
fi

echo "==> Uploading $IPA_PATH to App Store Connect" >&2
echo "    keyId=$IOS_ASC_KEY_ID issuerId=$IOS_ASC_ISSUER_ID" >&2

# altool has a charming habit of exiting 0 even on validation errors
# — printing "UPLOAD FAILED with N errors" and then ending with a
# zero exit code. Capture stdout+stderr, tee for the user, and grep
# the captured copy for the failure marker so the orchestrator
# actually fails when the upload was rejected.
upload_log=$(mktemp -t justmyweather-altool.XXXXXX.log)
# shellcheck disable=SC2064
trap "rm -f '$upload_log'" EXIT

set +e
xcrun altool --upload-app \
  --type ios \
  --file "$IPA_PATH" \
  --apiKey "$IOS_ASC_KEY_ID" \
  --apiIssuer "$IOS_ASC_ISSUER_ID" 2>&1 | tee "$upload_log"
altool_status=${PIPESTATUS[0]}
set -e

if [ "$altool_status" -ne 0 ] || grep -qE "UPLOAD FAILED|Validation failed|Failed to upload" "$upload_log"; then
  echo >&2
  echo "==> Upload REJECTED by App Store Connect — see errors above." >&2
  exit 1
fi

cat >&2 <<EOF

==> Upload accepted by App Store Connect.
    Apple needs ~5-15 min to process the build before it's available
    in TestFlight. Watch for it at:
      https://appstoreconnect.apple.com/apps → Just My Weather → TestFlight
EOF
