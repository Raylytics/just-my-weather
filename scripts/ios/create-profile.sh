#!/usr/bin/env bash
# Create an iOS App Store provisioning profile for the iOS bundle id
# via the App Store Connect API, then drop the .mobileprovision file
# into ~/Library/MobileDevice/Provisioning Profiles/ so xcodebuild
# picks it up.
#
# When you need this:
#   - First TestFlight upload after Apple Developer enrollment, when
#     no provisioning profile exists yet for the bundle id.
#   - After regenerating the team's Distribution certificate (the
#     existing profile still references the old cert; export will
#     fail with "Provisioning profile … doesn't include signing
#     certificate").
#   - After deleting the profile manually in the developer portal.
#
# Why we're not using `xcodebuild -allowProvisioningUpdates` for this:
# `-allowProvisioningUpdates` triggers Apple's cloud-signing path,
# which our App Store Connect API key (App Manager role) isn't
# permitted to drive. Symptom: "Cloud signing permission error" +
# "No profiles for X were found" even after a profile is sitting on
# disk. This script side-steps that by creating the profile directly
# via the public ASC profiles API and writing the file ourselves.
#
# Usage:
#   bash scripts/ios/create-profile.sh           # uses bundle id from common.sh
#   bash scripts/ios/create-profile.sh <bundleId> [profile-name]
#
# Exits 0 if the profile was created OR if it already existed (just
# re-installs the file from Apple in that case so a deleted local
# cache is restored).

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

BUNDLE_ID="${1:-$APP_ID}"
PROFILE_NAME="${2:-$IOS_APP_STORE_PROFILE_NAME}"

load_team_properties
require_vars IOS_ASC_KEY_ID IOS_ASC_ISSUER_ID IOS_ASC_KEY_PATH

KEY_PATH="${IOS_ASC_KEY_PATH/#\~/$HOME}"
if [ ! -f "$KEY_PATH" ]; then
  die "ASC private key not found at $KEY_PATH"
fi

command -v node >/dev/null 2>&1 || die "node not found — it signs the ASC API JWT (brew install node)"

echo "==> Creating App Store profile '$PROFILE_NAME' for $BUNDLE_ID" >&2

# Same JWT + HTTPS pattern as register-device.sh — see the comment
# block there for why Node over bash+openssl.
# shellcheck disable=SC2016 # single quotes keep the JS's ${...} away from bash, on purpose
KEY_PATH="$KEY_PATH" \
  BUNDLE_ID="$BUNDLE_ID" \
  PROFILE_NAME="$PROFILE_NAME" \
  IOS_ASC_KEY_ID="$IOS_ASC_KEY_ID" \
  IOS_ASC_ISSUER_ID="$IOS_ASC_ISSUER_ID" \
  node -e '
    const crypto = require("crypto");
    const fs = require("fs");
    const https = require("https");
    const path = require("path");

    const keyId = process.env.IOS_ASC_KEY_ID;
    const issuerId = process.env.IOS_ASC_ISSUER_ID;
    const keyPath = process.env.KEY_PATH;
    const bundleId = process.env.BUNDLE_ID;
    const profileName = process.env.PROFILE_NAME;
    const privateKey = fs.readFileSync(keyPath);

    function jwt() {
      const now = Math.floor(Date.now() / 1000);
      const header = Buffer.from(JSON.stringify({alg:"ES256",kid:keyId,typ:"JWT"})).toString("base64url");
      const payload = Buffer.from(JSON.stringify({iss:issuerId,exp:now+1200,aud:"appstoreconnect-v1"})).toString("base64url");
      const signingInput = `${header}.${payload}`;
      const sig = crypto.createSign("SHA256")
        .update(signingInput)
        .sign({key: privateKey, dsaEncoding: "ieee-p1363"});
      return `${signingInput}.${sig.toString("base64url")}`;
    }

    function req(method, path, body) {
      return new Promise((resolve, reject) => {
        const r = https.request({
          hostname: "api.appstoreconnect.apple.com",
          path,
          method,
          headers: {
            "Authorization": `Bearer ${jwt()}`,
            "Content-Type": "application/json",
          },
        }, res => {
          let d = "";
          res.on("data", c => d += c);
          res.on("end", () => resolve({status: res.statusCode, body: d}));
        });
        r.on("error", reject);
        if (body) r.write(JSON.stringify(body));
        r.end();
      });
    }

    function installLocally(uuid, contentBase64) {
      // Defensive: a malformed API response (transient null,
      // unexpected schema change, profile in a weird state) could
      // give us empty/undefined values. Without these checks, an
      // empty UUID lands at "Provisioning Profiles/.mobileprovision"
      // (a hidden file, easy to miss) and a missing/empty
      // profileContent writes 0 bytes — both surface much later as
      // confusing exportArchive errors. Real profiles are 7-10 KB.
      if (!uuid || typeof uuid !== "string") {
        throw new Error(`refusing to install: missing/invalid uuid (${JSON.stringify(uuid)})`);
      }
      if (!contentBase64 || typeof contentBase64 !== "string") {
        throw new Error(`refusing to install: missing/invalid profileContent for uuid=${uuid}`);
      }
      const buf = Buffer.from(contentBase64, "base64");
      if (buf.length < 1024) {
        throw new Error(`refusing to install: profileContent for uuid=${uuid} is only ${buf.length} bytes (expected >= 1024)`);
      }
      const dir = path.join(process.env.HOME, "Library/MobileDevice/Provisioning Profiles");
      fs.mkdirSync(dir, {recursive: true});
      const dest = path.join(dir, `${uuid}.mobileprovision`);
      fs.writeFileSync(dest, buf);
      console.log(`installed: ${dest} (${buf.length} bytes)`);
    }

    (async () => {
      // 1. Resolve bundleId resource id
      const bRes = await req("GET", `/v1/bundleIds?filter%5Bidentifier%5D=${encodeURIComponent(bundleId)}`);
      if (bRes.status !== 200) { console.error("bundle list failed", bRes.status, bRes.body); process.exit(1); }
      const bData = JSON.parse(bRes.body).data;
      if (!bData.length) { console.error(`no bundleId resource for ${bundleId} — register it at developer.apple.com first`); process.exit(1); }
      const bundleResId = bData[0].id;

      // 2. Resolve Apple Distribution cert. Prefer "DISTRIBUTION"
      // (current 2020+ format) over "IOS_DISTRIBUTION" (legacy);
      // within the preferred type, pick the LATEST-expiring cert so
      // we never pin a profile to a soon-to-expire cert when the
      // team has overlapping rotations.
      const cRes = await req("GET", "/v1/certificates?filter%5BcertificateType%5D=DISTRIBUTION,IOS_DISTRIBUTION");
      if (cRes.status !== 200) { console.error("cert list failed", cRes.status, cRes.body); process.exit(1); }
      const cData = JSON.parse(cRes.body).data;
      const byExpiry = (a, b) => new Date(b.attributes.expirationDate) - new Date(a.attributes.expirationDate);
      const distCerts = cData.filter(c => c.attributes.certificateType === "DISTRIBUTION").sort(byExpiry);
      const legacyCerts = cData.filter(c => c.attributes.certificateType === "IOS_DISTRIBUTION").sort(byExpiry);
      const cert = distCerts[0] || legacyCerts[0];
      if (!cert) { console.error("no Distribution cert on the team — install one via Xcode → Settings → Accounts → Manage Certificates → + Apple Distribution"); process.exit(1); }
      const allDist = [...distCerts, ...legacyCerts];
      if (allDist.length > 1) {
        console.log(`note: ${allDist.length} Distribution cert(s) on team; picking latest-expiring:`);
        for (const c of allDist) {
          const tag = c.id === cert.id ? " ← chosen" : "";
          console.log(`    ${c.id} ${c.attributes.certificateType} expires=${c.attributes.expirationDate}${tag}`);
        }
      }
      console.log(`cert: ${cert.id} (${cert.attributes.name})`);

      // 3. Look for an existing profile with this name, so we behave well on re-runs.
      const lRes = await req("GET", `/v1/profiles?filter%5Bname%5D=${encodeURIComponent(profileName)}`);
      if (lRes.status !== 200) { console.error("profile list failed", lRes.status, lRes.body); process.exit(1); }
      const existing = JSON.parse(lRes.body).data.find(
        p => p.attributes.name === profileName && p.attributes.profileType === "IOS_APP_STORE"
      );

      if (existing) {
        const state = existing.attributes.profileState; // typically "ACTIVE" or "INVALID"
        // After a Distribution cert rotation, the portal-side profile
        // is marked INVALID — the bytes still exist but reference the
        // old cert, so reinstalling them locally would silently
        // re-introduce the bug this script is meant to fix. Delete
        // and fall through to the create branch so the new profile
        // is signed against the freshly chosen cert.
        if (state !== "ACTIVE") {
          console.log(`profile id=${existing.id} state=${state} — not reusable; deleting and recreating`);
          const del = await req("DELETE", `/v1/profiles/${existing.id}`);
          if (del.status >= 400) {
            console.error(`failed to delete invalid profile (HTTP ${del.status}); delete it manually at developer.apple.com → Profiles, then re-run.`);
            console.error(del.body);
            process.exit(1);
          }
          // Fall through to create.
        } else {
          // Active and reusable. The list response trims
          // profileContent; refetch with a direct GET so we have the
          // bytes to drop into the local cache. This restores a
          // deleted ~/Library/.../*.mobileprovision without forcing a
          // recreate (and without dirtying expirationDate).
          const detail = await req("GET", `/v1/profiles/${existing.id}`);
          if (detail.status !== 200) { console.error("profile detail failed", detail.status, detail.body); process.exit(1); }
          const j = JSON.parse(detail.body).data;
          console.log(`profile already active id=${j.id} expires=${j.attributes.expirationDate}`);
          installLocally(j.attributes.uuid, j.attributes.profileContent);
          process.exit(0);
        }
      }

      // 4. Create.
      const create = await req("POST", "/v1/profiles", {
        data: {
          type: "profiles",
          attributes: {name: profileName, profileType: "IOS_APP_STORE"},
          relationships: {
            bundleId: {data: {type: "bundleIds", id: bundleResId}},
            certificates: {data: [{type: "certificates", id: cert.id}]},
          },
        },
      });
      if (create.status !== 201) { console.error("create failed", create.status, create.body); process.exit(1); }
      const created = JSON.parse(create.body).data;
      console.log(`created id=${created.id} expires=${created.attributes.expirationDate}`);
      installLocally(created.attributes.uuid, created.attributes.profileContent);
    })();
  '
