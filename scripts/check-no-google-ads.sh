#!/usr/bin/env bash
# google-ads-exit guardrail: no Google ad dependency, import, id or adapter
# may come back into app code. Runs from the pre-commit hook set
# (.pre-commit-config.yaml) and from CI's test job; exits non-zero on any hit.
#
#   scripts/check-no-google-ads.sh              # scan the tracked tree
#   scripts/check-no-google-ads.sh --self-test  # prove the patterns on fixtures
#
# What it scans: every tracked file, minus the places that legitimately
# mention the old stack — docs, release logs, the issue tracker export,
# roborev's config (its guidelines quote the deny list), build output and
# this script. Matches are exact artifacts, not substrings: the allowed
# play-services-ads-identifier module (the MAX SDK needs it) never trips the
# play-services-ads rule. Source: raylytics/docs/ad-migration/RUNBOOK.md,
# "Guardrail and CI".
set -euo pipefail

# One extended regex per line. Keep them anchored to the artifact spelling.
DENY=(
  'react-native-google-mobile-ads'
  'play-services-ads(-lite|-base)?["'"'"':]'
  'user-messaging-platform'
  'firebase-ads'
  'com\.google\.ads\.mediation'
  'applovin\.mediation:google(-ad-manager)?-adapter'
  'AppLovinMediationGoogle(AdManager)?Adapter'
  'Google-Mobile-Ads-SDK'
  'GoogleUserMessagingPlatform'
  'swift-package-manager-google-(mobile-ads|user-messaging-platform)'
  'com\.google\.android\.gms\.ads'
  'import GoogleMobileAds'
  'import UserMessagingPlatform'
  'DELAY_APP_MEASUREMENT_INIT'
  'GADApplicationIdentifier'
  'ca-app-pub-'
)
EXCLUDE='^(docs/|\.beads/|\.roborev/|\.roborev\.toml$|CHANGELOG\.md$|node_modules/|Pods/|build/|iosApp/build/|scripts/check-no-google-ads\.sh$|localization/)'

pattern="$(IFS='|'; echo "${DENY[*]}")"

scan() { # scan <file>...  → prints hits, returns 1 if any
  local hits
  hits="$(grep -nIE -- "$pattern" "$@" 2>/dev/null || true)"
  if [ -n "$hits" ]; then
    printf '%s\n' "$hits"
    return 1
  fi
  return 0
}

if [ "${1:-}" = "--self-test" ]; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  printf 'implementation("com.google.android.gms:play-services-ads-identifier:18.2.0")\n' > "$tmp/allowed.kts"
  printf 'implementation("com.google.android.gms:play-services-ads:25.5.0")\n' > "$tmp/denied.kts"
  printf 'pod "AppLovinMediationGoogleAdapter"\n' > "$tmp/adapter.rb"
  scan "$tmp/allowed.kts" >/dev/null || { echo "self-test FAILED: the ads-identifier module must pass"; exit 1; }
  if scan "$tmp/denied.kts" >/dev/null; then echo "self-test FAILED: play-services-ads must be caught"; exit 1; fi
  if scan "$tmp/adapter.rb" >/dev/null; then echo "self-test FAILED: the Google adapter must be caught"; exit 1; fi
  echo "self-test PASS"
  exit 0
fi

cd "$(git rev-parse --show-toplevel)"
files=()
while IFS= read -r f; do
  [[ "$f" =~ $EXCLUDE ]] && continue
  [ -f "$f" ] && files+=("$f")
done < <(git ls-files)

if scan "${files[@]}"; then
  echo "check-no-google-ads: clean (${#files[@]} files)"
else
  echo "check-no-google-ads: Google ad references found (above). Remove them; see RUNBOOK.md 'Deny list'." >&2
  exit 1
fi
