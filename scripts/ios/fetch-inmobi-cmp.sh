#!/usr/bin/env bash
# Fetch the InMobi CMP iOS SDK (the consent form behind the banner) into
# iosApp/Vendor/InMobiCMP.xcframework, which project.yml links and embeds.
#
#   scripts/ios/fetch-inmobi-cmp.sh          # download if missing, verify, unpack
#   scripts/ios/fetch-inmobi-cmp.sh --check  # exit 1 if the framework is not in place
#
# Why a script and not a package: InMobi publishes the SDK as a CocoaPod and
# as a zip from its portal, with no Swift Package, and this project is
# SwiftPM-only under XcodeGen. The pod's own source zip is the same file the
# portal serves (identical SHA-256, checked 2026-11-04), so it is pinned
# here by version and digest and never committed: iosApp/Vendor is
# gitignored. The JustMyWeather target runs this before compiling (a
# pre-build script in project.yml), so a fresh clone builds with no manual
# step; the digest check means a changed download fails the build rather
# than linking something unknown. Source: raylytics/docs/ad-migration/
# RUNBOOK.md, closed decisions (H1).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

INMOBI_CMP_VERSION="2.4.2"
INMOBI_CMP_URL="https://choice.inmobi.com/choice-mobile-ios/InMobiCMP-iOS-v${INMOBI_CMP_VERSION}.zip"
INMOBI_CMP_SHA256="f974e6997ce1cb4fd4b9151aac3563e2742c18aa0bbc850a91dc54ff6db3f75b"

VENDOR_DIR="$PROJECT_ROOT/iosApp/Vendor"
FRAMEWORK="$VENDOR_DIR/InMobiCMP.xcframework"
STAMP="$VENDOR_DIR/InMobiCMP.version"

die() { echo "fetch-inmobi-cmp: $1" >&2; exit 1; }

in_place() {
  [ -d "$FRAMEWORK" ] && [ -f "$STAMP" ] && [ "$(cat "$STAMP")" = "$INMOBI_CMP_VERSION $INMOBI_CMP_SHA256" ]
}

if [ "${1:-}" = "--check" ]; then
  in_place && exit 0
  echo "fetch-inmobi-cmp: InMobiCMP.xcframework $INMOBI_CMP_VERSION is not in iosApp/Vendor; run scripts/ios/fetch-inmobi-cmp.sh" >&2
  exit 1
fi

if in_place; then
  echo "fetch-inmobi-cmp: InMobiCMP.xcframework $INMOBI_CMP_VERSION already in place" >&2
  exit 0
fi

command -v curl >/dev/null || die "curl not found"
command -v unzip >/dev/null || die "unzip not found"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
echo "fetch-inmobi-cmp: downloading InMobi CMP $INMOBI_CMP_VERSION" >&2
curl -sSL --fail --max-time 300 -o "$tmp/sdk.zip" "$INMOBI_CMP_URL" || die "download failed: $INMOBI_CMP_URL"
actual="$(shasum -a 256 "$tmp/sdk.zip" | cut -d' ' -f1)"
[ "$actual" = "$INMOBI_CMP_SHA256" ] || die "SHA-256 mismatch for $INMOBI_CMP_URL: expected $INMOBI_CMP_SHA256, got $actual. The pinned version may have been republished; verify with InMobi before changing the digest."
unzip -q "$tmp/sdk.zip" -d "$tmp/unpacked" || die "unzip failed"
[ -d "$tmp/unpacked/InMobiCMP.xcframework" ] || die "the zip did not contain InMobiCMP.xcframework"
# .DS_Store files and the __MACOSX folder ride along in InMobi's zip.
find "$tmp/unpacked" -name '.DS_Store' -delete
mkdir -p "$VENDOR_DIR"
rm -rf "$FRAMEWORK"
mv "$tmp/unpacked/InMobiCMP.xcframework" "$FRAMEWORK"
printf '%s %s\n' "$INMOBI_CMP_VERSION" "$INMOBI_CMP_SHA256" > "$STAMP"
echo "fetch-inmobi-cmp: InMobiCMP.xcframework $INMOBI_CMP_VERSION unpacked into iosApp/Vendor" >&2
