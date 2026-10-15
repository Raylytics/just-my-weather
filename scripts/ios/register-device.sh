#!/usr/bin/env bash
# Register a device UDID with the Apple Developer team via the App
# Store Connect API.
#
# Usage:
#   scripts/ios/register-device.sh <udid> [name]
#
# Why this script exists: a fresh team needs at least one registered
# device before automatic Development signing can generate a
# provisioning profile (see archive.sh). Manually
# adding via developer.apple.com/account works too, but the ASC API
# is faster and works without leaving the terminal.
#
# What it does:
#   1. Loads ASC API key info from iosApp/team.properties.
#   2. Generates a short-lived ES256-signed JWT for the API.
#   3. POSTs to https://api.appstoreconnect.apple.com/v1/devices.
#   4. Prints the new device's id + status, or surfaces the API error.
#
# To find a connected iOS device's UDID without Xcode:
#   - libimobiledevice (`brew install libimobiledevice`) → `idevice_id -l`
#   - Finder → click the device → click the name/storage line until UDID shows
#   - iPad/iPhone → Settings → General → About → tap "Device" / serial repeatedly

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

if [ $# -lt 1 ]; then
  die "usage: $(basename "$0") <udid> [name]"
fi

UDID="$1"
NAME="${2:-Registered Device $(date +%Y-%m-%d)}"

load_team_properties
require_vars IOS_ASC_KEY_ID IOS_ASC_ISSUER_ID IOS_ASC_KEY_PATH

KEY_PATH="${IOS_ASC_KEY_PATH/#\~/$HOME}"
if [ ! -f "$KEY_PATH" ]; then
  die "ASC private key not found at $KEY_PATH"
fi

command -v node >/dev/null 2>&1 || die "node not found — it signs the ASC API JWT (brew install node)"

echo "==> Registering $UDID ($NAME)" >&2

# Use Node for the JWT + HTTPS call — the .p8 is an EC private key
# in PKCS#8, ASC requires ES256, and Node's crypto handles both
# without external deps. Bash + openssl works too but is finicky
# (openssl emits a DER signature; JWT wants raw r||s). Node is already
# a dependency of the Play scripts (scripts/android/common.sh), so this
# adds nothing new to install.
# shellcheck disable=SC2016 # single quotes keep the JS's ${...} away from bash, on purpose
KEY_PATH="$KEY_PATH" UDID="$UDID" NAME="$NAME" \
  IOS_ASC_KEY_ID="$IOS_ASC_KEY_ID" \
  IOS_ASC_ISSUER_ID="$IOS_ASC_ISSUER_ID" \
  node -e '
    const crypto = require("crypto");
    const fs = require("fs");
    const https = require("https");

    const keyId    = process.env.IOS_ASC_KEY_ID;
    const issuerId = process.env.IOS_ASC_ISSUER_ID;
    const keyPath  = process.env.KEY_PATH;
    const udid     = process.env.UDID;
    const name     = process.env.NAME;

    const privateKey = fs.readFileSync(keyPath);
    const now = Math.floor(Date.now() / 1000);
    const header = Buffer.from(JSON.stringify({alg:"ES256",kid:keyId,typ:"JWT"})).toString("base64url");
    const payload = Buffer.from(JSON.stringify({iss:issuerId,exp:now+1200,aud:"appstoreconnect-v1"})).toString("base64url");
    const signingInput = `${header}.${payload}`;
    const sig = crypto.createSign("SHA256")
      .update(signingInput)
      .sign({key: privateKey, dsaEncoding: "ieee-p1363"});
    const jwt = `${signingInput}.${sig.toString("base64url")}`;

    const body = JSON.stringify({
      data: {
        type: "devices",
        attributes: {name, udid, platform: "IOS"},
      },
    });

    const req = https.request({
      hostname: "api.appstoreconnect.apple.com",
      path: "/v1/devices",
      method: "POST",
      headers: {
        "Authorization": `Bearer ${jwt}`,
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(body),
      },
    }, res => {
      let data = "";
      res.on("data", chunk => { data += chunk; });
      res.on("end", () => {
        try {
          const j = JSON.parse(data);
          if (res.statusCode === 201) {
            console.log(`registered: id=${j.data.id} status=${j.data.attributes.status} udid=${j.data.attributes.udid}`);
            process.exit(0);
          } else if (res.statusCode === 409 || (j.errors && j.errors.some(e => /already exists|conflict/i.test(e.detail || "")))) {
            // Already registered — treat as success.
            console.log(`already registered (HTTP ${res.statusCode}) — that satisfies the team-has-devices requirement`);
            process.exit(0);
          } else {
            console.error(`HTTP ${res.statusCode}`);
            console.error(JSON.stringify(j, null, 2));
            process.exit(1);
          }
        } catch (e) {
          console.error(`HTTP ${res.statusCode} (non-JSON response)`);
          console.error(data);
          process.exit(1);
        }
      });
    });
    req.on("error", err => { console.error(err); process.exit(1); });
    req.write(body);
    req.end();
  '
