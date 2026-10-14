<p align="center">
  <img src="assets/skiarkt_logo_bw.png" alt="SKY ROCKET SHOOTER (SKIARKT) logo" width="320"/>
</p>

# SKY ROCKET SHOOTER (SKIARKT)

**S**hooters **K**nown **I**ntercepter **A**TAK **R**apid **K**nowledge **T**ool

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![ATAK](https://img.shields.io/badge/ATAK-4.8-green.svg)
![Status](https://img.shields.io/badge/phases-1--3_complete-orange.svg)

SKIARKT is an ATAK 4.8 plugin for planning counter-small-UAS (C-sUAS) point defense. Operators use it to lay out
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

---

## Building

### Prerequisites
- JDK 17
- Android SDK with `platforms;android-34` and `build-tools;34.0.0`
- `local.properties` containing `sdk.dir=/path/to/Android/Sdk` (git-ignored)
- A debug keystore at `keystores/debug.keystore`. Keystores are never committed; see [keystores/README.md](keystores/README.md).

### Commands
```bash
./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew assembleRelease        # release signing config from local.properties
```

### ATAK SDK
The ATAK SDK is proprietary and is **not** included in this repository. Gradle looks for an SDK in this order:

| Priority | Source | How |
|---|---|---|
| 1 | ATAK-CIV 4.8 SDK jar | Copy `main.jar` to `sdk/main.jar` (download from [tak.gov](https://tak.gov)) |
| 2 | TAK Maven AAR | `-PatakSdkMaven=true -PtakRepoUrl=... -PtakRepoUser=... -PtakRepoPassword=...` |
| 3 | Bundled `:atak-sdk-stubs` | Default. Contains only the API signatures, so the project compiles and tests run without tak.gov access |

Build field or release APKs against the real SDK (option 1 or 2), not the stubs.

---

## Sideloading onto ATAK 4.8
1. Install ATAK 4.8 on the device. The plugin's `plugin-api` must match the ATAK version.
2. Install the APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. In ATAK, open **Settings → Tool Preferences → Plugins** (or the **Plugins** tool). Find **SKY ROCKET SHOOTER (SKIARKT)** and tap **Load**.
4. Tap the SKIARKT toolbar icon to open the side panel.
5. Logs: `adb logcat | grep -i skiarkt`

The debug signing key only works on developer / sideload-friendly ATAK builds.

**WinTAK:** this APK runs on ATAK (Android) only. WinTAK works with SKIARKT through the shared CoT types below.

---

## Gov / Mil deployment
Play Store ATAK-CIV and ATAK-MIL / GOV builds load **only** plugins signed through the TAK Product Center third-party
signing pipeline. To deploy SKIARKT on those builds:
1. Request plugin signing at [tak.gov](https://tak.gov).
2. Build against the official ATAK 4.8 SDK, not the stubs.
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
│       ├── plugin/              PluginLifecycle, toolbar tool
│       ├── model/  data/  cot/  settings/  util/
│       ├── analysis/            intercept altitude, LOS sweep, RF propagation / heat map, coverage engine
│       ├── overlay/             map rendering
│       ├── tools/               draw + placement map tools
│       └── ui/                  side-panel sections
├── atak-sdk-stubs/              compile-only ATAK 4.8 API mirror
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
