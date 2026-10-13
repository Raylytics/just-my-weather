#!/bin/sh
# The whole JVM gate in one command: unit tests (app + the shared module's
# Android host tests), ktlint, the debug APK, and the instrumented-test APK
# (compiled, not run — running it needs a device). The shared module's iOS
# tests are not here — they need Xcode; run :shared:allTests on a Mac.
# Nothing here touches a device, so it is safe to run with a phone attached.
#
#   scripts/verify.sh          # the gate; run before calling any task done
#   scripts/verify.sh --fix    # ktlintFormat first, then the gate
#
# Prints a one-line PASS/FAIL summary last, with the unit-test tally, so the
# tail of the output is the verdict. Exit status follows Gradle's.
set -u
cd "$(git rev-parse --show-toplevel)" || exit 1

if [ "${1:-}" = "--fix" ]; then
    ./gradlew --quiet :app:ktlintFormat :shared:ktlintFormat || exit 1
fi

# Stale results from an earlier run must not be tallied as this run's: a
# compile failure would otherwise report yesterday's test count as today's.
rm -rf app/build/test-results/testDebugUnitTest shared/build/test-results/testAndroidHostTest

./gradlew --quiet :app:testDebugUnitTest :shared:testAndroidHostTest :app:ktlintCheck :shared:ktlintCheck \
    :app:assembleDebug :app:assembleDebugAndroidTest
status=$?

# Tally the JUnit XML that testDebugUnitTest leaves behind, so the summary
# says how many tests stood behind a PASS rather than just that Gradle exited 0.
tally=$(python3 - <<'PY' 2>/dev/null
import glob, re
t = f = e = 0
for p in (glob.glob('app/build/test-results/testDebugUnitTest/*.xml')
          + glob.glob('shared/build/test-results/testAndroidHostTest/*.xml')):
    m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', open(p).read(4000))
    if m:
        a, b, c = map(int, m.groups()); t += a; f += b; e += c
print(f"{t} unit tests, {f} failures, {e} errors")
PY
)

echo "----------------------------------------------------------------"
if [ "$status" -eq 0 ]; then
    echo "verify: PASS — ${tally:-tests tallied by Gradle}; ktlint clean; debug + androidTest APKs built"
else
    echo "verify: FAIL (gradle exit $status) — ${tally:-no test tally}; see output above"
fi
exit "$status"
