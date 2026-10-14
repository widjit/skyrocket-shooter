# Signing keystores

Keystores are **never committed** (`keystores/*` is in `.gitignore`). Generate a local debug keystore before building:

```bash
keytool -genkeypair -v \
  -keystore keystores/debug.keystore \
  -storepass android -keypass android \
  -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

The build uses `keystores/debug.keystore` (store/key password `android`, alias `androiddebugkey`) by default.
Override with entries in `local.properties`:

```properties
takDebugKeyFile=keystores/debug.keystore
takDebugKeyFilePassword=android
takDebugKeyAlias=androiddebugkey
takDebugKeyPassword=android
takReleaseKeyFile=/path/to/release.jks
takReleaseKeyFilePassword=...
takReleaseKeyAlias=...
takReleaseKeyPassword=...
```

Release builds for Gov/Mil ATAK must be signed through the TAK Product Center (https://tak.gov) - see `SECURITY.md`.
