# ATAK SDK jars (git-ignored, never packaged)

| ATAK target | Put `main.jar` at | Build |
|---|---|---|
| 5.6.0 (tak.gov SDK, default) | `sdk/5.6.0/main.jar` | `./gradlew assembleCivDebug assembleCivRelease` |
| other 5.x (tak.gov SDK) | `sdk/<X.Y.Z>/main.jar` | `./gradlew assembleCivRelease -PatakVersion=X.Y.Z` |
| any 5.x | anywhere | `-PatakSdkJar=/path/main.jar -PatakVersion=X.Y.Z` |

The plugin uses the ATAK 5.x `gov.tak.api.plugin.IPlugin` model (`AbstractPlugin` + `AbstractPluginTool`), as in the
SDK's `PluginTemplateLegacy` sample. The legacy `transapps` / `AbstractPluginLifecycle` model was removed in 5.6.

ATAK 4.x is not supported by this source: `-PatakVersion=4.x` fails. Build tag `v1.0.0-civ4.6.0` (commit `fc397d0`) for ATAK-CIV 4.6.0.

If no SDK jar is found, the build fails. `-PallowStubs` compiles against the stubs to check the code only; the resulting APK does not run.
Never commit anything from this folder (SDK jars, `android_keystore`, etc.); `sdk/*/` and `sdk/*.jar` are git-ignored.
