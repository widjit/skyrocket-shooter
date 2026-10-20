#!/usr/bin/env bash
# Creates YOUR private release signing key for BOTTLE ROCKET (BTL RCKT) and a
# git-ignored keystore.properties pointing at it. Back the keystore up: if it is lost,
# installed copies can never be updated with the same signature.
#   usage: scripts/make_release_keystore.sh ["CN=Your Org, O=Your Org, C=US"]
set -euo pipefail
cd "$(dirname "$0")/.."
DNAME="${1:-CN=BTL RCKT Release, O=BTL RCKT, C=US}"
KS=keystores/btlrckt-release.jks
ALIAS=btlrckt
[ -f "$KS" ] && { echo "Refusing to overwrite existing $KS"; exit 1; }
mkdir -p keystores
read -r -s -p "Keystore password (min 6 chars): " PW; echo
keytool -genkeypair -v -keystore "$KS" -storetype PKCS12 -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "$DNAME" -storepass "$PW" -keypass "$PW"
cat > keystore.properties <<P
storeFile=$KS
storePassword=$PW
keyAlias=$ALIAS
keyPassword=$PW
P
chmod 600 keystore.properties "$KS"
echo "Created $KS and keystore.properties (both git-ignored)."
keytool -list -v -keystore "$KS" -storepass "$PW" | grep -E "SHA256:|Owner:"
