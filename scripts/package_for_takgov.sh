#!/usr/bin/env bash
# Builds the source zip to upload to the TAK.gov Third-Party Plugin pipeline, which
# compiles against the official SDK for the chosen ATAK version and returns an APK signed
# for Play Store / release ATAK. Contains only committed files: no SDK, no keys, no build output.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-../btlrckt-src-$(git rev-parse --short HEAD).zip}"
git diff --quiet || echo "WARNING: uncommitted changes are NOT included in the zip"
git archive --format=zip --prefix=btlrckt/ -o "$OUT" HEAD
if unzip -l "$OUT" | grep -Eq '\.(jks|keystore)$|keystore\.properties|sdk/.*\.(jar|aar)$'; then
  echo "ERROR: secrets/SDK found in archive"; rm -f "$OUT"; exit 1
fi
echo "Wrote $OUT"; unzip -l "$OUT" | tail -1
