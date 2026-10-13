#!/usr/bin/env bash
# Thin forwarder: the version sync serves both platforms, so it lives at
# scripts/sync-version.sh (VERSION → Android versionName + iOS
# MARKETING_VERSION). Kept here so open-frame's muscle memory
# (scripts/ios/sync-version.sh) still works. Arguments pass through.
set -euo pipefail
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/sync-version.sh" "$@"
