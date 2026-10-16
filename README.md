<p align="center">
  <img src="assets/skiarkt_logo_bw.png" alt="SKY ROCKET SHOOTER (SKIARKT) logo" width="320"/>
</p>

# SKY ROCKET SHOOTER (SKIARKT)

**S**hooters **K**nown **I**ntercepter **A**TAK **R**apid **K**nowledge **T**ool

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![ATAK](https://img.shields.io/badge/ATAK--CIV-5.6.0-green.svg)
![Status](https://img.shields.io/badge/phases-1--3_complete-orange.svg)

SKIARKT is an ATAK-CIV plugin for planning counter-small-UAS (C-sUAS) point defense. Operators use it to lay out
exclusion zones, sensors and effectors on the ATAK map. The plugin shows terrain-masked sensor coverage, models RF
jammer effectiveness, works out safe intercept altitudes, and shares the whole plan with other ATAK / WinTAK users
as Cursor-on-Target (CoT).

> Planning and visualization tool only. See [SECURITY.md](SECURITY.md) before any operational use.

---

## Features

### ✅ Phase 1: Framework
- Plugin lifecycle, toolbar button and a side panel that lets you move between sections and go back.
- Data model and a thread-safe plan repository. Plans are saved as JSON at `/sdcard/atak/tools/skiarkt_shooter/*.skiarktplan.json`.
- CoT schema and serializer for every item type, so plans round-trip between ATAK and WinTAK over TAK Server or mesh.
- Settings: units (ft / m / km), display precision, default colors, transparency, CoT stale time, author and classification.
- DTED helper built on ATAK `ElevationManager`. It gives point elevation, AGL→HAE conversion, terrain profiles, and line of sight corrected with the 4/3-Earth model.

### ✅ Phase 2: Exclusion zones and intercept altitude
- Draw zones by tapping the map. Pan and zoom keep working while you draw, and you can undo or close the polygon at any point.
- Each zone has a height AGL, fill and stroke colors, opacity, a dashed or solid outline, and can be extruded in 3D.
- **Minimum safe intercept altitude** along a threat corridor or along an ingress line you draw. The calculation uses DTED and adds a safety buffer.

### ✅ Phase 3: Sensors and non-kinetic effectors
- **Sensors** (Radar / Camera / EO-IR): placed by tapping the map. Settings are range, beam width (360° = omni), azimuth, height AGL, color and opacity.
- **Terrain masking:** a DTED line-of-sight sweep from each sensor marks what terrain hides. Hidden areas are drawn dark, and the editor shows the % of coverage that has line of sight.
- **RF jammer heat maps:** built from free-space path loss, with a terrain penalty where DTED blocks line of sight. The map has three colors (effective / marginal / insufficient). Threshold and frequency can be set per jammer or globally, and opacity can be set per jammer or for all heat maps.
- Analysis runs in the background and results are cached, so the UI stays responsive.

### 🗺️ Roadmap
| Phase | Scope |
|---|---|
| 4 | Threat ingress corridors and sensor gap analysis |
| 5 | Engagement timeline (detect → decide → defeat), best-effector designation, friendly-fire deconfliction, MSA |
| 6 | PACE plan builder, decision-maker briefing export (PDF / KMZ / data package), WinTAK → ATAK pre-planned push |

Planned feature additions (multi-frequency RF effectors, sensor types, exclusion-zone shapes, laser-guided rockets with designator pairing and laser-masking analysis, engagement-authority perimeters) are detailed in [docs/ROADMAP.md](docs/ROADMAP.md).

---

## Building

### Prerequisites
- JDK 17
- Android SDK with `platforms;android-34` and `build-tools;34.0.0`
- `local.properties` containing `sdk.dir=/path/to/Android/Sdk` (git-ignored)
- A debug keystore at `keystores/debug.keystore`. Keystores are never committed; see [keystores/README.md](keystores/README.md).

### Commands
```bash
./gradlew assembleCivDebug                       # -> app/build/outputs/apk/civ/debug/app-civ-debug.apk (debug key)
./gradlew testCivDebugUnitTest                   # JVM unit tests
./gradlew assembleCivRelease                     # your release key, or UNSIGNED if none configured
./gradlew assembleCivRelease -PatakVersion=5.5.0 # another 5.x host; needs sdk/5.5.0/main.jar
```
Flavours `civ` / `mil` / `gov` match the TAK plugin template and set `plugin-api` to `com.atakmap.app@<ver>.CIV|MIL|GOV`.

### Release signing (no debug key)
```bash
scripts/make_release_keystore.sh "CN=Your Org, O=Your Org, C=US"   # writes keystores/skiarkt-release.jks + keystore.properties (both git-ignored)
./gradlew assembleCivRelease                                        # -> app-civ-release.apk signed with YOUR key
```
CI can use the env vars `SKIARKT_KEYSTORE`, `SKIARKT_KEYSTORE_PASSWORD`, `SKIARKT_KEY_ALIAS`, and `SKIARKT_KEY_PASSWORD` instead.
The build refuses to sign a release with `debug.keystore`. If no key is configured, the release APK is left unsigned, which is the form the TAK.gov pipeline needs.
```

### ATAK SDK
The ATAK SDK is proprietary and is **not** included in this repository. Gradle looks for an SDK in this order:

| Priority | Source | How |
|---|---|---|
| 1 | ATAK-CIV SDK jar | `-PatakSdkJar=<path>`, else `sdk/<atakVersion>/main.jar` (default `sdk/5.6.0/main.jar`). ATAK-CIV 5.x SDKs come from [tak.gov](https://tak.gov) |
| 2 | TAK Maven AAR | `-PatakSdkMaven=true -PtakRepoUrl=... -PtakRepoUser=... -PtakRepoPassword=...` |
| 3 | Bundled `:atak-sdk-stubs` | Only with `-PallowStubs`. Contains only the API signatures (mirroring ATAK-CIV 5.6), so the code compiles without tak.gov access. Always used as the unit-test classpath |

Build field or release APKs against the real SDK (option 1 or 2), not the stubs.

### Plugin model (ATAK 5.x `IPlugin`)
ATAK-CIV 5.6 removed the legacy `transapps` Lifecycle/ToolDescriptor model (`AbstractPluginLifecycle`). SKIARKT follows the SDK's `PluginTemplateLegacy` sample:
- `app/src/main/assets/plugin.xml` declares exactly one extension: `gov.tak.api.plugin.IPlugin` → `com.skiarkt.shooter.plugin.PluginLifecycle`.
- `PluginLifecycle` extends `com.atak.plugins.impl.AbstractPlugin`. ATAK calls its `(IServiceController)` constructor; it obtains the plugin `Context` through `PluginContextProvider` and passes `AbstractPlugin` the toolbar tool and the map component.
- `PointDefenseTool` extends `AbstractPluginTool` and implements `gov.tak.api.util.Disposable`.
- `PointDefenseMapComponent` (a `DropDownMapComponent`) applies `R.style.ATAKPluginTheme` before inflating any UI.

### ATAK 4.x
This branch targets ATAK 5.x only; `-PatakVersion=4.x` fails with a pointer here. The ATAK-CIV 4.6.0 build (legacy plugin model, verified in the 4.6.0.5 emulator) is at commit `fc397d0`, tagged **`v1.0.0-civ4.6.0`**: `git checkout v1.0.0-civ4.6.0`.

---

## Running on ATAK-CIV

> **Version lock:** ATAK only loads a plugin whose `plugin-api` exactly matches the running ATAK (for example `com.atakmap.app@5.6.0.CIV`).
> The default build targets **5.6.0** (`com.atakmap.app@5.6.0.CIV`). To target another 5.x version, build against that version's SDK `main.jar` and pass `-PatakVersion=X.Y.Z`.

### Option A: Developer ATAK-CIV 5.6.0 (from the SDK)
1. Download **ATAK-CIV SDK 5.6.0** from [tak.gov](https://tak.gov) and copy its `main.jar` to `sdk/5.6.0/main.jar`. Install the developer ATAK 5.6.0 APK that tak.gov provides with the SDK (uninstall any Play Store ATAK first, since it uses the same package name).
2. Run `./gradlew assembleCivDebug` and then `adb install -r app/build/outputs/apk/civ/debug/app-civ-debug.apk`.
3. In ATAK, go to **☰ → Plugins**. If the list is empty, tap the sync arrows. Tick **SKY ROCKET SHOOTER (SKIARKT)** and choose **Load**.
4. Open **☰ → SKY ROCKET SHOOTER** to open the side panel. Logs: `adb logcat | grep -i skiarkt`

Screenshots in [docs/screenshots](docs/screenshots) come from the earlier ATAK-CIV 4.6.0.5 emulator test (tag `v1.0.0-civ4.6.0`).

### Option B: Play Store ATAK-CIV 5.x (e.g. 5.6)
Release ATAK (Play Store CIV, MIL, GOV) loads only plugins that meet **both** of these conditions:
1. **The version matches.** The plugin was built with `plugin-api = com.atakmap.app@5.6.0.CIV`. Check the exact number under ATAK **☰ → Settings → About**.
2. **TAK.gov signed it.** A plugin signed with your own key installs, but release ATAK refuses to load it. That signing check is not something the plugin can change.

Steps:
1. Create an account at [tak.gov](https://tak.gov) and download **ATAK-CIV SDK 5.6.0**. Copy its `main.jar` to `sdk/5.6.0/main.jar`.
2. Run `./gradlew assembleCivRelease` (5.6.0 is the default; for another 5.x release add `-PatakVersion=X.Y.Z` with that SDK's jar). The plugin uses the 5.x `gov.tak.api.plugin.IPlugin` model.
3. Test on the **developer ATAK 5.6 APK** that comes with the SDK. It accepts any signature, including your release key.
4. Run `scripts/package_for_takgov.sh` and upload the zip to the TAK.gov **Third-Party Plugin** pipeline. Choose ATAK 5.6.0 / CIV.
   The pipeline builds the plugin against the official SDK and returns an APK signed for release ATAK. Install it with `adb install` or through Mission Package / TAK Server.

**WinTAK:** this APK runs on ATAK (Android) only. WinTAK works with SKIARKT through the shared CoT types below.

---

## Gov / Mil deployment
Play Store ATAK-CIV and ATAK-MIL / GOV builds load **only** plugins signed through the TAK Product Center third-party
signing pipeline. To deploy SKIARKT on those builds:
1. Request plugin signing at [tak.gov](https://tak.gov).
2. Build against the official ATAK SDK that matches the target version, not the stubs.
3. Submit the release APK and follow your organization's accreditation process.

See [SECURITY.md](SECURITY.md).

---

## CoT schema
| Object | CoT type |
|---|---|
| Plan header | `b-m-p-skiarkt-plan` |
| Exclusion zone | `b-m-p-skiarkt-exczone` |
| Sensor | `b-m-p-skiarkt-sensor` |
| Non-kinetic effector | `b-m-p-skiarkt-nke` |
| Kinetic effector | `b-m-p-skiarkt-ke` |
| Defended asset | `b-m-p-skiarkt-defasset` |
| Threat corridor | `b-m-p-skiarkt-corridor` |

- Common CoT attributes: `how="h-g-i-g-o"`, stale time 30 days by default (configurable), label in `<contact callsign>`, free text in `<remarks>`.
- Type-specific attributes go in `<__skiarkt v="1" kind=".." planUid=".." …/>`.
- Polygon vertices use the standard `<link point="lat,lon[,hae]"/>` elements. Standard `fillColor` / `strokeColor` / `strokeWeight` / `height` elements are also written so stock TAK viewers can display the items.

---

## Project layout
```
├── app/                         plugin APK module
│   └── src/main/java/com/skiarkt/shooter/
│       ├── plugin/              PluginLifecycle (IPlugin / AbstractPlugin), toolbar tool
│       ├── model/  data/  cot/  settings/  util/
│       ├── analysis/            intercept altitude, LOS sweep, RF propagation / heat map, coverage engine
│       ├── overlay/             map rendering
│       ├── tools/               draw + placement map tools
│       └── ui/                  side-panel sections
├── atak-sdk-stubs/              compile-only ATAK API mirror
├── assets/                      branding (icon, logos)
├── keystores/                   local signing keys (git-ignored)
└── sdk/                         drop the ATAK SDK here (git-ignored)
```

---

## Community
- Issues and feature requests: [GitHub Issues](https://github.com/widjit/skyrocket-shooter/issues)
- TAK Product Center, SDK and plugin signing: [tak.gov](https://tak.gov)
- TAK community: [ATAK-CIV on GitHub](https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV)

## License
Licensed under the [Apache License 2.0](LICENSE). Copyright 2026 SKIARKT Contributors.

ATAK, WinTAK and the TAK SDK belong to their respective owners and are not covered by this license.
