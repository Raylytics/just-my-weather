#!/usr/bin/env bash
# Mirror the repo-root VERSION file (one semver line) into:
#   - Android versionName  (app/build.gradle.kts: `versionName = "X"`)
#   - iOS MARKETING_VERSION (iosApp/project.yml: `MARKETING_VERSION: X`)
#
# One release name for both apps: VERSION is the single source of truth.
# Edit it, run this, and both native configs match. The BUILD counters
# (Android versionCode, iOS CURRENT_PROJECT_VERSION) are deliberately NOT
# synced — each store has its own monotonic rule and its own upload history
# (plan decision 8: independent build counters).
#
# CLAUDE.md "Versioning": a released versionName is FROZEN. Bump VERSION
# before cutting a build whose behaviour changed, never after.
#
#   scripts/sync-version.sh          # write VERSION into both files, then verify
#   scripts/sync-version.sh --check  # verify only; exit 1 if either disagrees
#
# Idempotent. Fails loudly if either value disagrees with VERSION after the
# rewrite (sanity check). Ported from open-frame's scripts/ios/sync-version.sh,
# which read package.json; it lives at scripts/ rather than scripts/ios/
# because it serves both platforms (scripts/ios/sync-version.sh forwards here).
#
# The .xcodeproj is generated from project.yml, so after a write run
# `cd iosApp && xcodegen generate` (archive.sh does) before building.

set -euo pipefail

die() {
  echo "error: $1" >&2
  exit 1
}

# Assignment form: inside `cd "$(...)"` a failed substitution would leave a
# silent `cd ""` and every path below would be wrong.
ROOT=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)
VERSION_FILE="$ROOT/VERSION"
BUILD_GRADLE="$ROOT/app/build.gradle.kts"
PROJECT_YML="$ROOT/iosApp/project.yml"

check_only=0
case "${1:-}" in
  "") ;;
  --check) check_only=1 ;;
  *) die "usage: $(basename "$0") [--check]" ;;
esac
[ $# -le 1 ] || die "usage: $(basename "$0") [--check]"

for f in "$VERSION_FILE" "$BUILD_GRADLE" "$PROJECT_YML"; do
  [ -f "$f" ] || die "$f not found"
done

# First line, whitespace stripped. Plain MAJOR.MINOR.PATCH only: both stores
# accept it, and a suffix (-rc1, +build) is valid semver that App Store
# Connect rejects for CFBundleShortVersionString.
VERSION=$(head -1 "$VERSION_FILE" | tr -d '[:space:]')
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  die "VERSION '$VERSION' is not MAJOR.MINOR.PATCH"
fi

read_android() {
  sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/p' "$BUILD_GRADLE"
}
read_ios() {
  sed -nE 's/^[[:space:]]*MARKETING_VERSION:[[:space:]]*"?([^"#[:space:]]*)"?.*/\1/p' "$PROJECT_YML"
}

# BSD/GNU sed wrapper. Writes only when the content would change, so a no-op
# run doesn't touch mtimes (Gradle and Xcode both notice those).
sed_inplace_if_changed() {
  local expr="$1" file="$2" tmp
  tmp=$(mktemp)
  sed -E "$expr" "$file" > "$tmp"
  if cmp -s "$tmp" "$file"; then
    rm -f "$tmp"
  else
    cat "$tmp" > "$file"   # cat, not mv: keeps the file's mode and inode
    rm -f "$tmp"
  fi
}

echo "==> VERSION = $VERSION (source of truth)" >&2

if [ "$check_only" = 0 ]; then
  # Exactly one line each is expected; the verification below counts them so
  # a second (say, flavor) versionName can't be half-updated silently.
  sed_inplace_if_changed "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*)\"[^\"]*\"/\1\"$VERSION\"/" "$BUILD_GRADLE"
  sed_inplace_if_changed "s/^([[:space:]]*MARKETING_VERSION:[[:space:]]*)\"?[^\"#[:space:]]*\"?/\1$VERSION/" "$PROJECT_YML"
fi

# Verify: every occurrence equals VERSION, and there is at least one.
fail=0
verify() {
  local label="$1" values="$2" v n=0 bad=0
  if [ -z "$values" ]; then
    echo "!! $label: no version line found" >&2; fail=1; return
  fi
  while IFS= read -r v; do
    n=$((n + 1))
    if [ "$v" != "$VERSION" ]; then
      echo "!! $label: '$v' != VERSION '$VERSION'" >&2; bad=1
    fi
  done <<< "$values"
  if [ "$bad" = 1 ]; then fail=1; else echo "    $label: $n line(s) = $VERSION" >&2; fi
}
verify "Android versionName (app/build.gradle.kts)" "$(read_android)"
verify "iOS MARKETING_VERSION (iosApp/project.yml)" "$(read_ios)"

if [ "$fail" = 1 ]; then
  if [ "$check_only" = 1 ]; then
    die "versions out of sync with VERSION — run scripts/sync-version.sh"
  fi
  die "versions still disagree after the rewrite — fix the line(s) above by hand"
fi
echo "==> Both apps at version $VERSION" >&2
