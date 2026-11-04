#!/usr/bin/env bash
# google-ads-exit guardrail: nothing that needs the suspended AdMob account
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
# play-services-ads-identifier module never trips the play-services-ads rule,
# and its com.google.android.gms.ads.identifier package never trips the
# gms.ads rule.
#
# Two tiers (runbook v4, 2026-11-04). The deny list is everything that needs
# the AdMob account or Google ad demand: Google Mobile Ads, AdMob and Google
# Ad Manager adapters (AppLovin's and Appodeal's, plus Notsy, which depends on
# them), Meta and Yandex adapters (own-account networks we do not hold), app
# ids and ca-app-pub strings. The UMP tier is Google's User Messaging
# Platform: Appodeal's SDK pulls it in transitively, so a lockfile
# (Podfile.lock, Package.resolved, Gradle lockfiles) may name it, but no
# source file, Podfile, project.yml or Gradle build file may declare, import
# or initialize it. The self-test runs before every scan, so a broken
# pattern fails loudly instead of passing an empty scan. Source:
# raylytics/docs/ad-migration/RUNBOOK.md, "Guardrail and CI".
set -euo pipefail

# One extended regex per line. Keep them anchored to the artifact spelling.
DENY=(
  'react-native-google-mobile-ads'
  'play-services-ads(-lite|-base)?["'"'"':]'
  'firebase-ads'
  'com\.google\.ads\.mediation'
  'applovin\.mediation:google(-ad-manager)?-adapter'
  'AppLovinMediationGoogle(AdManager)?Adapter'
  'Appodeal-Swift-Package-(Google|Meta|Facebook|Yandex)'
  'Appodeal(Google|GoogleAdMob|Meta|Facebook|Yandex)Adapter'
  'APD(GoogleAdMob|Meta|Facebook|Yandex)Adapter'
  'BDMNotsyAdapter'
  'appodeal\.ads\.sdk\.networks:(admob|google|meta|facebook|yandex|notsy)'
  'Google-Mobile-Ads-SDK'
  'swift-package-manager-google-mobile-ads'
  'com\.google\.android\.gms\.ads'
  'import GoogleMobileAds'
  '<GoogleMobileAds/'
  'DELAY_APP_MEASUREMENT_INIT'
  'GADApplicationIdentifier'
  'ca-app-pub-'
)
# Google's User Messaging Platform: denied everywhere a lockfile is not.
UMP=(
  'user-messaging-platform'
  'GoogleUserMessagingPlatform'
  'swift-package-manager-google-user-messaging-platform'
  'com\.google\.android\.ump'
  'import UserMessagingPlatform'
  '<UserMessagingPlatform/'
)
EXCLUDE='^(docs/|\.beads/|\.roborev/|\.roborev\.toml$|CHANGELOG\.md$|node_modules/|Pods/|build/|iosApp/build/|scripts/check-no-google-ads\.sh$|localization/)'
LOCKFILE='(^|/)(Podfile\.lock|Package\.resolved|[^/]*\.lockfile)$'

deny_pattern="$(IFS='|'; echo "${DENY[*]}")"
ump_pattern="$(IFS='|'; echo "${UMP[*]}")"
full_pattern="$deny_pattern|$ump_pattern"
# The one allowed thing the gms.ads rule would otherwise catch.
ALLOWED='com\.google\.android\.gms\.ads\.identifier'

scan() { # scan <pattern> <file>...  → prints hits, returns 1 if any, 2 on a grep error
  local pattern="$1"; shift
  [ "$#" -gt 0 ] || return 0
  local hits rc=0
  hits="$(grep -nIE -- "$pattern" "$@")" || rc=$?
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

# scan_files <file>... → lockfiles get the deny tier only, everything else both.
scan_files() {
  local f locks=() others=() rc=0 r
  for f in "$@"; do
    if [[ "$f" =~ $LOCKFILE ]]; then locks+=("$f"); else others+=("$f"); fi
  done
  r=0; scan "$full_pattern" "${others[@]+"${others[@]}"}" || r=$?
  [ "$r" -gt "$rc" ] && rc=$r
  r=0; scan "$deny_pattern" "${locks[@]+"${locks[@]}"}" || r=$?
  [ "$r" -gt "$rc" ] && rc=$r
  return "$rc"
}

self_test() {
  local tmp
  tmp="$(mktemp -d)"
  printf 'implementation("com.google.android.gms:play-services-ads-identifier:18.2.0")\n' > "$tmp/allowed.kts"
  printf 'import com.google.android.gms.ads.identifier.AdvertisingIdClient\n-keep class com.google.android.gms.ads.identifier.** { *; }\n' > "$tmp/allowed-pkg.txt"
  printf 'implementation("com.google.android.gms:play-services-ads:25.5.0")\n' > "$tmp/denied.kts"
  printf 'pod "AppLovinMediationGoogleAdapter"\n' > "$tmp/adapter.rb"
  printf 'https://github.com/appodeal/Appodeal-Swift-Package-Google\n' > "$tmp/appodeal-google.yml"
  printf 'pod "BDMNotsyAdapter"\n' > "$tmp/notsy.rb"
  printf 'import com.google.android.gms.ads.identifier.X; import com.google.android.gms.ads.AdView\n' > "$tmp/mixed.kt"
  printf '#import <GoogleMobileAds/GoogleMobileAds.h>\n' > "$tmp/bridge.m"
  printf '  - GoogleUserMessagingPlatform (3.1.0)\n' > "$tmp/Podfile.lock"
  printf '"location" : "https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git"\n' > "$tmp/Package.resolved"
  printf '  - Google-Mobile-Ads-SDK (12.0.0)\n' > "$tmp/ads.Podfile.lock"
  printf "pod 'GoogleUserMessagingPlatform'\n" > "$tmp/Podfile"
  printf 'import UserMessagingPlatform\n' > "$tmp/Consent.swift"
  local ok=1
  scan_files "$tmp/allowed.kts" >/dev/null || { echo "self-test FAILED: the ads-identifier module must pass" >&2; ok=0; }
  scan_files "$tmp/allowed-pkg.txt" >/dev/null || { echo "self-test FAILED: the ads.identifier package must pass" >&2; ok=0; }
  if scan_files "$tmp/denied.kts" >/dev/null; then echo "self-test FAILED: play-services-ads must be caught" >&2; ok=0; fi
  if scan_files "$tmp/adapter.rb" >/dev/null; then echo "self-test FAILED: the Google adapter must be caught" >&2; ok=0; fi
  if scan_files "$tmp/appodeal-google.yml" >/dev/null; then echo "self-test FAILED: Appodeal's Google package must be caught" >&2; ok=0; fi
  if scan_files "$tmp/notsy.rb" >/dev/null; then echo "self-test FAILED: Notsy must be caught" >&2; ok=0; fi
  if scan_files "$tmp/mixed.kt" >/dev/null; then echo "self-test FAILED: a denied token beside the allowed package must be caught" >&2; ok=0; fi
  if scan_files "$tmp/bridge.m" >/dev/null; then echo "self-test FAILED: the Objective-C header import must be caught" >&2; ok=0; fi
  scan_files "$tmp/Podfile.lock" >/dev/null || { echo "self-test FAILED: UMP in Podfile.lock must pass" >&2; ok=0; }
  scan_files "$tmp/Package.resolved" >/dev/null || { echo "self-test FAILED: the UMP package in Package.resolved must pass" >&2; ok=0; }
  if scan_files "$tmp/ads.Podfile.lock" >/dev/null; then echo "self-test FAILED: Google Mobile Ads in a lockfile must still be caught" >&2; ok=0; fi
  if scan_files "$tmp/Podfile" >/dev/null; then echo "self-test FAILED: UMP declared in a Podfile must be caught" >&2; ok=0; fi
  if scan_files "$tmp/Consent.swift" >/dev/null; then echo "self-test FAILED: a UMP import must be caught" >&2; ok=0; fi
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

rc=0
scan_files "${files[@]}" || rc=$?
case "$rc" in
  0) echo "check-no-google-ads: clean (${#files[@]} files)" ;;
  1) echo "check-no-google-ads: AdMob-dependent or UMP references found (above). Remove them; see RUNBOOK.md 'Deny list'." >&2; exit 1 ;;
  *) exit "$rc" ;;
esac
