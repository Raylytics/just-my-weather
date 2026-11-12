#!/usr/bin/env bash
# Repair the Info.plist of embedded frameworks that App Store Connect
# rejects, then re-sign them. Appodeal 4.4.0 and its adapters ship with
# (seen 2026-11-04, build 4 rejected at upload; Appodeal-Swift-Package-IAB
# issue #4 and the InMobi/UnityAds adapter repos report the same):
#
#   - CFBundleShortVersionString with four parts ("13.6.4.0"); Apple allows
#     at most three (ITMS-90060).
#   - CFBundleSupportedPlatforms listing both iPhoneOS and iPhoneSimulator
#     in the device slice; Apple wants one (ITMS-91177).
#   - MinimumOSVersion 12.0 while the binary is linked for 15.1; App Store
#     Connect accepts the upload and then fails processing (ITMS-90208).
#
#   scripts/ios/fix-framework-plists.sh <path/to/App.app>
#
# Runs on the archived app (archive.sh calls it), so the rehearsal and the
# cut both get it and the export re-signs the result. Every framework is
# checked, only the broken ones are touched, and each change is printed.
# Idempotent. Drop this when the pinned Appodeal packages ship clean
# plists (check the three repos' issues before bumping the pins).
set -euo pipefail
app="${1:?usage: fix-framework-plists.sh <App.app>}"
[ -d "$app/Frameworks" ] || { echo "fix-framework-plists: no Frameworks in $app" >&2; exit 0; }

# The identity the app was signed with; the frameworks get the same one.
identity=$(codesign -dvv "$app" 2>&1 | sed -n 's/^Authority=//p' | head -n 1)
[ -n "$identity" ] || { echo "fix-framework-plists: $app is not signed; cannot re-sign frameworks" >&2; exit 1; }

# The platform this archive is for: the app's own value, one entry.
platform=$(plutil -extract CFBundleSupportedPlatforms.0 raw -o - "$app/Info.plist")

fixed=0
for fw in "$app"/Frameworks/*.framework; do
  name=$(basename "$fw" .framework)
  plist="$fw/Info.plist"
  [ -f "$plist" ] || continue
  changes=()

  version=$(plutil -extract CFBundleShortVersionString raw -o - "$plist" 2>/dev/null || true)
  if [ "$(printf '%s' "$version" | tr -cd '.' | wc -c | tr -d ' ')" -ge 3 ]; then
    short=$(printf '%s' "$version" | cut -d. -f1-3)
    plutil -replace CFBundleShortVersionString -string "$short" "$plist"
    changes+=("CFBundleShortVersionString $version -> $short")
  fi

  platforms=$(plutil -extract CFBundleSupportedPlatforms json -o - "$plist" 2>/dev/null || echo '[]')
  if [ "$(printf '%s' "$platforms" | tr -cd ',' | wc -c | tr -d ' ')" -ge 1 ]; then
    plutil -replace CFBundleSupportedPlatforms -json "[\"$platform\"]" "$plist"
    changes+=("CFBundleSupportedPlatforms $platforms -> [$platform]")
  fi

  # "12" and "12.0" are the same version: compare major.minor as numbers.
  declared=$(plutil -extract MinimumOSVersion raw -o - "$plist" 2>/dev/null || true)
  linked=$(xcrun vtool -show-build "$fw/$name" 2>/dev/null | awk '/minos/ {print $2; exit}' || true)
  if [ -n "$declared" ] && [ -n "$linked" ] && \
     awk -F. -v d="$declared" -v l="$linked" 'BEGIN { split(d, a, "."); split(l, b, "."); exit !((b[1] + 0 > a[1] + 0) || (b[1] + 0 == a[1] + 0 && b[2] + 0 > a[2] + 0)) }'; then
    plutil -replace MinimumOSVersion -string "$linked" "$plist"
    changes+=("MinimumOSVersion $declared -> $linked (linked minimum)")
  fi

  [ "${#changes[@]}" -gt 0 ] || continue
  codesign --force --sign "$identity" --preserve-metadata=identifier,entitlements,flags --timestamp=none "$fw" 2>&1 \
    | grep -v 'replacing existing signature' || true
  fixed=$((fixed + 1))
  for c in "${changes[@]}"; do echo "    $name: $c" >&2; done
done
# The app's seal lists each nested framework by code hash, so a re-signed
# framework needs the app re-signed too (same identity, entitlements kept).
if [ "$fixed" -gt 0 ]; then
  codesign --force --sign "$identity" --preserve-metadata=identifier,entitlements,flags --timestamp=none "$app" 2>&1 \
    | grep -v 'replacing existing signature' || true
  codesign --verify --deep --strict "$app"
fi
echo "==> fix-framework-plists: $fixed framework(s) repaired and re-signed" >&2
