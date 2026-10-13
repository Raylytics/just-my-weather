#!/usr/bin/env bash
# What shared code hasn't iOS shipped yet? An ADVISORY report.
#
#   scripts/release-drift.sh
#
# Drift is allowed (plan decision 8): Android and iOS cut on their own
# cadence, so shared/ commits that reached Play but not TestFlight are
# normal. This script only makes that gap visible before a cut, so shipping
# without a fix is a decision rather than an accident. It never blocks: it
# exits 0 whatever it finds — including when it cannot find a baseline —
# and exits 2 only when misused (bad arguments, not run inside the repo).
# (open-frame's version was a gate with an ACK variable; this one is not.)
#
# The baseline is the commit of the last iOS build, recorded by hand in
# docs/PARITY.md after each TestFlight cut (release-testflight.sh prints the
# lines to add):
#
#   ## Last iOS build
#   shared-commit: <sha>
#
# Reports:
#   - commits under shared/ since that sha (what iOS hasn't shipped)
#   - commits under app/ and iosApp/ since then, for context
#   - VERSION vs Android versionName vs iOS MARKETING_VERSION

set -uo pipefail

if [ $# -gt 0 ]; then
  echo "usage: $(basename "$0")   (takes no arguments)" >&2
  exit 2
fi
ROOT=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null) || {
  echo "error: not inside the just-my-weather git repo" >&2
  exit 2
}
cd "$ROOT" || exit 2

PARITY="docs/PARITY.md"

echo "==> Release drift (advisory — drift is allowed, plan decision 8)"

# ---- Versions --------------------------------------------------------------
# One release name for both apps (scripts/sync-version.sh). A mismatch here
# is worth a warning even though the build counters are independent.
v_file=$(head -1 VERSION 2>/dev/null | tr -d '[:space:]')
v_android=$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/p' app/build.gradle.kts 2>/dev/null | head -1)
v_ios=$(sed -nE 's/^[[:space:]]*MARKETING_VERSION:[[:space:]]*"?([^"#[:space:]]*)"?.*/\1/p' iosApp/project.yml 2>/dev/null | head -1)
echo
echo "versions: VERSION=${v_file:-<missing>}  Android versionName=${v_android:-<missing>}  iOS MARKETING_VERSION=${v_ios:-<missing>}"
if [ -z "$v_file" ] || [ "$v_file" != "$v_android" ] || [ "$v_file" != "$v_ios" ]; then
  echo "warn: versions disagree — run scripts/sync-version.sh (or fix VERSION first)"
fi

# Prints "<label>: N commit(s)" then the one-line log, for one path.
report() {
  local label="$1" base="$2" path="$3" log count
  log=$(git log --oneline "$base..HEAD" -- "$path")
  if [ -z "$log" ]; then
    echo "$label: 0 commits"
  else
    count=$(printf '%s\n' "$log" | wc -l | tr -d ' ')
    echo "$label: $count commit(s)"
    printf '%s\n' "$log" | sed 's/^/  /'
  fi
}

# ---- Baseline ---------------------------------------------------------------
# Each "can't tell" case says what's missing and stops at exit 0: no baseline
# is a normal state before the first iOS build, not an error.
echo
if [ ! -f "$PARITY" ]; then
  echo "no $PARITY — no iOS build recorded yet, so there is no baseline to diff."
  echo "After the first TestFlight cut, add to $PARITY:"
  echo "  ## Last iOS build"
  echo "  shared-commit: <sha>"
  exit 0
fi

# The first `shared-commit:` line inside the "## Last iOS build" section
# (which ends at the next "## " heading).
sha=$(awk '
  /^## / { in_section = ($0 ~ /^## Last iOS build[[:space:]]*$/); next }
  in_section && /^[[:space:]]*shared-commit:/ {
    sub(/^[[:space:]]*shared-commit:[[:space:]]*/, ""); sub(/[[:space:]].*$/, ""); print; exit
  }
' "$PARITY")

if ! grep -qE '^## Last iOS build[[:space:]]*$' "$PARITY"; then
  echo "$PARITY has no '## Last iOS build' section — no baseline to diff."
  exit 0
fi
if [ -z "$sha" ]; then
  echo "$PARITY's '## Last iOS build' section has no 'shared-commit: <sha>' line — no baseline to diff."
  exit 0
fi
if ! git rev-parse --verify --quiet "$sha^{commit}" >/dev/null; then
  echo "shared-commit '$sha' in $PARITY is not a commit in this repo (typo, or not fetched?) — no baseline to diff."
  exit 0
fi

echo "last iOS build: shared-commit $(git rev-parse --short "$sha") ($(git log -1 --format='%cs %s' "$sha"))"
echo
report "shared/ — not yet shipped on iOS" "$sha" shared/
echo
echo "for context, since the same commit:"
report "app/ (Android-only)" "$sha" app/
report "iosApp/ (iOS-only)" "$sha" iosApp/

exit 0
