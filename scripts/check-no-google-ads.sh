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
# play-services-ads rule, and its com.google.android.gms.ads.identifier
# package never trips the gms.ads rule. The self-test runs before every scan,
# so a broken pattern fails loudly instead of passing an empty scan. Source:
# raylytics/docs/ad-migration/RUNBOOK.md, "Guardrail and CI".
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
# The one allowed thing the gms.ads rule would otherwise catch.
ALLOWED='com\.google\.android\.gms\.ads\.identifier'

scan() { # scan <file>...  → prints hits, returns 1 if any, 2 on a grep error
  local hits rc
  set +e
  hits="$(grep -nIE -- "$pattern" "$@")"
  rc=$?
  set -e
  if [ "$rc" -gt 1 ]; then
    echo "check-no-google-ads: grep failed (exit $rc); a deny pattern is probably malformed" >&2
    return 2
  fi
  # A line that only matched through the allowed identifier package is not a
  # hit; one that also carries a denied token still is.
  local kept="" line
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    if printf '%s\n' "$line" | grep -qE -- "$ALLOWED"; then
      printf '%s\n' "$line" | sed -E "s/$ALLOWED//g" | grep -qE -- "$pattern" || continue
    fi
    kept="$kept$line"$'\n'
  done <<< "$hits"
  if [ -n "$kept" ]; then
    printf '%s' "$kept"
    return 1
  fi
  return 0
}

self_test() {
  local tmp
  tmp="$(mktemp -d)"
  printf 'implementation("com.google.android.gms:play-services-ads-identifier:18.2.0")\n' > "$tmp/allowed.kts"
  printf 'import com.google.android.gms.ads.identifier.AdvertisingIdClient\n-keep class com.google.android.gms.ads.identifier.** { *; }\n' > "$tmp/allowed-pkg.txt"
  printf 'implementation("com.google.android.gms:play-services-ads:25.5.0")\n' > "$tmp/denied.kts"
  printf 'pod "AppLovinMediationGoogleAdapter"\n' > "$tmp/adapter.rb"
  printf 'import com.google.android.gms.ads.identifier.X; import com.google.android.gms.ads.AdView\n' > "$tmp/mixed.kt"
  local ok=1
  scan "$tmp/allowed.kts" >/dev/null || { echo "self-test FAILED: the ads-identifier module must pass" >&2; ok=0; }
  scan "$tmp/allowed-pkg.txt" >/dev/null || { echo "self-test FAILED: the ads.identifier package must pass" >&2; ok=0; }
  if scan "$tmp/denied.kts" >/dev/null; then echo "self-test FAILED: play-services-ads must be caught" >&2; ok=0; fi
  if scan "$tmp/adapter.rb" >/dev/null; then echo "self-test FAILED: the Google adapter must be caught" >&2; ok=0; fi
  if scan "$tmp/mixed.kt" >/dev/null; then echo "self-test FAILED: a denied token beside the allowed package must be caught" >&2; ok=0; fi
  rm -rf "$tmp"
  [ "$ok" = 1 ]
}

if [ "${1:-}" = "--self-test" ]; then
  self_test && echo "self-test PASS"
  exit $?
fi

# Every real scan proves its own patterns first.
self_test || { echo "check-no-google-ads: self-test failed; refusing to report a clean scan" >&2; exit 2; }

cd "$(git rev-parse --show-toplevel)"
files=()
while IFS= read -r f; do
  [[ "$f" =~ $EXCLUDE ]] && continue
  [ -f "$f" ] && files+=("$f")
done < <(git ls-files)

set +e
scan "${files[@]}"
rc=$?
set -e
case "$rc" in
  0) echo "check-no-google-ads: clean (${#files[@]} files)" ;;
  1) echo "check-no-google-ads: Google ad references found (above). Remove them; see RUNBOOK.md 'Deny list'." >&2; exit 1 ;;
  *) exit "$rc" ;;
esac
