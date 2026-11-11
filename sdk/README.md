# ATAK SDK files (git-ignored, never packaged)

The ATAK SDK is resolved **only** through TAK.gov's `atak-gradle-takdev` Gradle plugin, as in the ATAK-CIV 5.6.0
SDK `plugintemplate`. That is the layout the TAK.gov Third-Party Plugin pipeline builds.

| Mode | Setup | Build |
|---|---|---|
| Remote (artifacts.tak.gov) | `-Ptakrepo.url=https://artifacts.tak.gov/artifactory/maven -Ptakrepo.user=<user> -Ptakrepo.password=<pass>` (or the same keys in `local.properties`) | `./gradlew -Ptakrepo.force=true ... assembleCivRelease` |
| Offline (SDK zip) | copy `atak-gradle-takdev.jar`, `main.jar` and `android_keystore` from the SDK zip into `sdk/5.6.0/` | `./gradlew assembleCivDebug assembleCivRelease` |
| Other 5.x host | as above, in `sdk/<X.Y.Z>/` | add `-PatakVersion=X.Y.Z` |

Overrides: `-Psdk.path=<dir>` (folder with `main.jar` / `android_keystore`) and `-Ptakdev.plugin=<jar>`.
Remote mode uses `com.atakmap.gradle:atak-gradle-takdev:3.+` (the 5.6.0 template's version).
Offline builds warn that there is no ATAK `mapping.txt`; remote and pipeline builds get it from the TAK repository.

The plugin uses the ATAK 5.x `gov.tak.api.plugin.IPlugin` model (`AbstractPlugin` + `AbstractPluginTool`), as in the
SDK's `PluginTemplateLegacy` sample. The legacy `transapps` / `AbstractPluginLifecycle` model was removed in 5.6.

ATAK 4.x is not supported by this source: `-PatakVersion=4.x` fails. Build tag `v1.0.0-civ4.6.0` (commit `fc397d0`) for ATAK-CIV 4.6.0.

With no SDK source, the build fails. `-PallowStubs` compiles against the stubs to check the code only; the resulting APK does not run.
Never commit anything from this folder (SDK jars, `android_keystore`, etc.); `sdk/*/` is git-ignored.
The SDK's `android_keystore` is a development key: the release build refuses it, like the debug keystore.
