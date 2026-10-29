#!/usr/bin/env bash
# Orchestrator: cut a TestFlight build in one command.
#
#   scripts/ios/release-testflight.sh                   # rehearsal: gates + archive + export, NO upload
#   scripts/ios/release-testflight.sh --confirm-upload  # the real cut: bump + archive + export + UPLOAD
#
# Pipeline:
#   0. release-drift.sh        — what shared/ code iOS hasn't shipped (advisory)
#   1. refuse a dirty tree     — the build must be a committed state
#   2. AdMob ids               — refuse Google's SAMPLE publisher (see below)
#   3. scripts/verify.sh       — the JVM gate; plus :shared:allTests (the
#                                shared module's iOS tests, which need a Mac)
#   4. sync-version.sh --check — VERSION, versionName, MARKETING_VERSION agree
#   5. bump-build-number.sh    — CURRENT_PROJECT_VERSION + 1 (Apple monotonic-int rule)
#   6. archive.sh              — xcodegen + xcodebuild archive → build/ios/JustMyWeather.xcarchive
#   7. export-ipa.sh           — xcodebuild -exportArchive → build/ios/export/*.ipa
#   8. upload-testflight.sh    — xcrun altool → App Store Connect
#
# Without --confirm-upload, steps 5 and 8 are skipped: the run proves the
# build archives and exports (and leaves the tree clean), then stops. The
# rehearsal's IPA carries the CURRENT build number, which App Store Connect
# has already seen if it was uploaded before — it is not for uploading.
#
# Agents: CLAUDE.md "Release confirmation gate" — uploading ANY build, the
# TestFlight internal group included, needs Evan's explicit go-ahead for
# THAT upload, in the current exchange. --confirm-upload is how the human's
# go-ahead is spelled; never pass it on your own initiative.
#
# Afterwards: commit the iosApp/project.yml bump and log the cut in
# docs/RELEASES.md (this script prints the line to add; it does not edit
# the log, because the log's prose is a human's call).
#
# Aborts on any non-zero exit. Prints a clear final URL on success.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

confirm_upload=0
for arg in "$@"; do
  case "$arg" in
    --confirm-upload) confirm_upload=1 ;;
    *) die "release-testflight.sh only accepts --confirm-upload. For other export methods run export-ipa.sh directly." ;;
  esac
done

cd "$PROJECT_ROOT"

if [ "$confirm_upload" = 1 ]; then
  cat >&2 <<'BANNER'

  ###########################################################################
  #  THIS RUN UPLOADS A BUILD TO APP STORE CONNECT / TESTFLIGHT.            #
  #                                                                         #
  #  It needs the human's explicit go-ahead for THIS specific upload        #
  #  (CLAUDE.md "Release confirmation gate"). A go-ahead for an earlier     #
  #  upload does not carry forward. If you are an agent and Evan has not    #
  #  said "upload this build" in the current exchange: Ctrl-C now.          #
  ###########################################################################

BANNER
else
  cat >&2 <<'BANNER'

  ==> REHEARSAL (no --confirm-upload): gates, archive and export only.
      Nothing is bumped and nothing is uploaded.

BANNER
fi

# 0. Drift report. Advisory by design (plan decision 8: the platforms ship on
#    their own cadence), so its output informs and never blocks — unlike
#    open-frame's gate, which refused to cut without RELEASE_DRIFT_ACK.
bash "$PROJECT_ROOT/scripts/release-drift.sh" >&2 || true
echo >&2

# 1. A cut must be a known commit: the archive records nothing about
#    uncommitted edits, so a dirty tree makes the build unreproducible.
#    Tracked files only (as release-internal.sh does): untracked scratch
#    doesn't reach the build.
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  die "working tree has uncommitted changes; commit (or stash) first so the cut is a known commit."
fi

# 2. Sample ads must never ship. Release.xcconfig falls back to Google's
#    SAMPLE ids and only the gitignored AdMob.xcconfig replaces them, so a
#    machine without that file — or with the example's placeholders, or with
#    the sample ids pasted in — archives a build that serves test ads and
#    earns nothing. The Android twin of this rule is in app/build.gradle.kts
#    and scripts/android/release-internal.sh. Unlike Android there is no
#    --sample-ads escape hatch: TestFlight builds are promoted to the App
#    Store as-is, so a sample-ads build has nowhere safe to go.
ADMOB_XCCONFIG="$CONFIG_DIR/AdMob.xcconfig"
if [ ! -f "$ADMOB_XCCONFIG" ]; then
  die "$ADMOB_XCCONFIG is missing, so this build would serve Google's SAMPLE ads. Copy AdMob.xcconfig.example and fill in the real ids."
fi
if grep -q "ca-app-pub-3940256099942544" "$ADMOB_XCCONFIG"; then
  die "$ADMOB_XCCONFIG contains Google's sample publisher (ca-app-pub-3940256099942544). Put the real AdMob ids in it."
fi
for key in ADMOB_APP_ID ADMOB_BANNER_UNIT_ID; do
  value="$(sed -nE "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*(.*)\$/\1/p" "$ADMOB_XCCONFIG" | head -1)"
  case "$value" in
    "") die "$key is missing from $ADMOB_XCCONFIG — Release would fall back to the SAMPLE id" ;;
    *XXXX*|*NNNN*) die "$key in $ADMOB_XCCONFIG is still the example placeholder" ;;
  esac
done

# 3. The gates. verify.sh is the JVM gate (Android + the shared module's
#    host tests); :shared:allTests adds the shared module's iOS tests, which
#    verify.sh can't run because they need Xcode — and this is a Mac.
scripts/verify.sh
./gradlew --console=plain :shared:allTests

# 4. One release name for both apps. --check, not a write: a write here
#    would dirty the tree we just proved clean, and a mismatch means VERSION
#    was edited without syncing — a human decides which number is right.
bash "$PROJECT_ROOT/scripts/sync-version.sh" --check

VERSION_NAME="$(head -1 "$PROJECT_ROOT/VERSION" | tr -d '[:space:]')"

if [ "$confirm_upload" = 0 ]; then
  bash "$SCRIPT_DIR/archive.sh"
  IPA_PATH=$(bash "$SCRIPT_DIR/export-ipa.sh" --method app-store-connect | tail -n 1)
  cat >&2 <<EOF

==> Rehearsal complete: $IPA_PATH
    archives and exports for the App Store. Nothing was uploaded and the
    build number was not bumped. For the real cut (after Evan's go-ahead
    for this upload):
      scripts/ios/release-testflight.sh --confirm-upload
EOF
  exit 0
fi

# 5. Bump. From here the tree is dirty (project.yml). A failure must say how
#    to get back to a state the clean-tree guard above will accept.
NEW_BUILD=$(bash "$SCRIPT_DIR/bump-build-number.sh" | tail -n 1)
echo >&2
echo "==> Build number bumped to $NEW_BUILD (version $VERSION_NAME)" >&2
echo >&2

uploaded=0
on_fail() {
  [ "$uploaded" = 1 ] && return
  cat >&2 <<MSG

!! release-testflight.sh failed after bumping the build number to $NEW_BUILD.
   Look at the output above:
   - If the upload step started and App Store Connect accepted the build
     (or reported it as a duplicate), the number is BURNED: commit the bump,
     then re-run (a fresh bump).
   - Otherwise nothing reached Apple: git checkout iosApp/project.yml to
     undo the bump, fix the cause, and re-run.
MSG
}
trap 'on_fail' EXIT

# 6–8.
bash "$SCRIPT_DIR/archive.sh"
IPA_PATH=$(bash "$SCRIPT_DIR/export-ipa.sh" --method app-store-connect | tail -n 1)
bash "$SCRIPT_DIR/upload-testflight.sh" "$IPA_PATH"
uploaded=1
trap - EXIT

cat >&2 <<EOF

==> Release pipeline complete.
    Build $NEW_BUILD ($VERSION_NAME) uploaded. Once Apple finishes processing
    (~5-15 min), it's available to TestFlight testers.

    Now:
      git commit iosApp/project.yml -m "chore(release): iOS build $NEW_BUILD to TestFlight"
    and add the cut to docs/RELEASES.md, e.g. under the $VERSION_NAME heading:
      iOS build $NEW_BUILD, TestFlight $(date +%Y-%m-%d)
    and record it as the last iOS build in docs/PARITY.md:
      ## Last iOS build
      shared-commit: $(git rev-parse --short HEAD)
EOF
