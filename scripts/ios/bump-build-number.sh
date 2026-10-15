#!/usr/bin/env bash
# Increment iOS CURRENT_PROJECT_VERSION (CFBundleVersion) by 1 in
# iosApp/project.yml.
#
# Apple requires this to monotonically increase on every TestFlight /
# App Store upload. It is iOS's OWN counter, independent of Android's
# versionCode (plan decision 8: independent build counters) — the two
# stores never see each other's numbers, and tying them together would
# force a burned number on one platform every time the other re-uploads.
#
# Every run bumps once — re-running picks the latest value from the
# file and adds 1, so idempotency is "two runs == two bumps", which
# is the right behavior for a "release script that called this".
#
# project.yml, not the pbxproj: the Xcode project is generated from it and
# not committed. Callers that build afterwards run `xcodegen generate`
# (archive.sh does) so the new number reaches the project.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

if [ ! -f "$PROJECT_YML" ]; then die "$PROJECT_YML not found"; fi

# Read every CURRENT_PROJECT_VERSION line. Today there is one (project-level
# settings.base), but a target or config could grow its own. Using the max
# means we never go *backwards* on any of them, which would make a
# previously-uploaded build number reachable again. Apple's monotonicity is
# per-binary, so a regression there is silent until the duplicate-rejection
# at upload time, which is the worst place to find out.
#
# `mapfile` would be the obvious tool but it's bash 4+, and macOS
# ships /bin/bash 3.2; iterate manually so this works on a fresh Mac.
current_values=()
while IFS= read -r line; do
  current_values+=("$line")
done < <(sed -nE 's/^[[:space:]]*CURRENT_PROJECT_VERSION:[[:space:]]*"?([^"#[:space:]]*)"?.*/\1/p' "$PROJECT_YML")

if [ ${#current_values[@]} -eq 0 ]; then
  die "could not find CURRENT_PROJECT_VERSION in $PROJECT_YML"
fi

current=0
for v in "${current_values[@]}"; do
  if ! [[ "$v" =~ ^[0-9]+$ ]]; then
    die "CURRENT_PROJECT_VERSION = '$v' is not a plain integer; bailing rather than guessing"
  fi
  if [ "$v" -gt "$current" ]; then
    current=$v
  fi
done

# Loud warning if the values diverged before this bump — we still
# proceed (taking the max is the safe move) but make it visible.
diverged=0
for v in "${current_values[@]}"; do
  if [ "$v" != "$current" ]; then
    diverged=1
  fi
done
if [ "$diverged" = 1 ]; then
  echo "warn: CURRENT_PROJECT_VERSION values disagree in project.yml (${current_values[*]}); using max=$current" >&2
fi

next=$((current + 1))
echo "==> CURRENT_PROJECT_VERSION: $current -> $next" >&2

# Keeps the indentation and any trailing `# comment`; normalises quoting away
# (an unquoted integer is what YAML and XcodeGen both expect).
sedi -E "s/^([[:space:]]*CURRENT_PROJECT_VERSION:[[:space:]]*)\"?[0-9]+\"?/\1$next/" "$PROJECT_YML"

# Verify every copy got the new value.
new_count=$(grep -cE "^[[:space:]]*CURRENT_PROJECT_VERSION:[[:space:]]*$next([[:space:]]|$)" "$PROJECT_YML" || true)
if [ "$new_count" -ne "${#current_values[@]}" ]; then
  die "expected ${#current_values[@]} CURRENT_PROJECT_VERSION: $next line(s) after bump, found $new_count"
fi
echo "    project.yml: $new_count line(s) set" >&2

# Print just the number on stdout — useful for `BUILD=$(scripts/ios/bump-build-number.sh)`
echo "$next"
