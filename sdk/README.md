Place the official ATAK 4.8 SDK `main.jar` (from the ATAK-CIV SDK zip on tak.gov / GitHub releases)
in this directory as `sdk/main.jar`. When present, the build compiles against it instead of the
bundled `:atak-sdk-stubs` module. The jar is git-ignored and is never packaged into the plugin APK.
