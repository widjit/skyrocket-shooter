<p align="center">
  <img src="assets/skiarkt_logo_bw.png" alt="SKY ROCKET SHOOTER (SKIARKT) logo" width="320"/>
</p>

# SKY ROCKET SHOOTER (SKIARKT)

**S**hooters **K**nown **I**ntercepter **A**TAK **R**apid **K**nowledge **T**ool

> 🚀 ***Be a Shooter too.** Find your limits, and know your blindspots.*

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![ATAK](https://img.shields.io/badge/ATAK--CIV-5.6.0-green.svg)
![Status](https://img.shields.io/badge/status-debug_pre--release-orange.svg)
![Version](https://img.shields.io/badge/version-1.0.8-lightgrey.svg)

SKIARKT is an ATAK-CIV plugin for **counter-small-UAS (C-sUAS) point defense**. It is used by three
groups:

- **Planners** see what is actually covered before any asset is employed, and build courses of
  action (COAs).
- **Decision makers** make data-driven decisions, with coverage, gaps and engagement options in
  front of them.
- **Shooters** understand the environment they are operating in: what can be seen, where they can
  engage, and where the blind spots are.

The plugin shows terrain-masked sensor coverage, models RF jammer effectiveness, works out safe
intercept altitudes, and shares the whole plan with other ATAK / WinTAK users as Cursor-on-Target (CoT).

> **Planning and visualization tool only.** It is not a fire-control system. Ranges come from public
> datasheets or what the operator enters. See [SECURITY.md](SECURITY.md) before any operational use.

---

## 📣 Status: looking for feedback

**1.0.8 is a debug pre-release** for ATAK developer builds (the developer ATAK-CIV 5.6.0 from the
SDK). It is debug-signed, so **Play Store / release ATAK will not load it**. It has passed its JVM unit
tests but has had very little on-device testing. Expect rough edges.

I'm sharing it now to check I'm heading in the right direction before building the analysis layers.
The most useful feedback right now:

1. **Is the planning workflow right?** Is anything missing, in the wrong order or too many taps to
   place and link sensors, effectors and no-shoot areas?
2. **Which analysis matters most to you?** The [next phase](#-roadmap) has drone detection
   simulation, detection-floor heat maps and intercept heat maps. Which would you use first?
3. **Sensor and effector data:** are the built-in presets and fields the ones you need? Which
   systems or parameters are missing?
4. **Safety rules:** do the "never shoot at or through protected assets / walls" rules match how you
   would plan?
5. **Bugs and crashes:** please attach `adb logcat | grep -i skiarkt` output where possible.

Open an issue at [GitHub Issues](https://github.com/widjit/skyrocket-shooter/issues). Sketches,
screenshots and "this is how we actually do it" notes are all welcome.

Download: [Releases](https://github.com/widjit/skyrocket-shooter/releases) (latest:
`v1.0.8-civ-5.6.0-debug`).

---

## Features (1.0.8)

### Plan, save and share
- Side panel opened from the ATAK toolbar, with sections for each asset type and back navigation.
- Plans are autosaved as JSON (`/sdcard/atak/tools/skiarkt_shooter/*.skiarktplan.json`).
- Every object is shared as CoT, so plans round-trip between ATAK and WinTAK over TAK Server or mesh.
  Older plans and CoT still load when new fields are added.
- Settings: units (ft / m / km), display precision, default colours, transparency, CoT stale time,
  author and classification.
- PNG export of the plan, with an option to include hidden assets.

### No-shoot areas and engagement authority
- **Exclusion zones** (protected assets): polygon, circle or rectangle, each with a height AGL, colours,
  opacity, dashed/solid outline and 3D extrusion.
- **Minimum safe intercept altitude** along a line you draw, using DTED terrain plus a safety buffer.
- **Engagement-authority perimeters:** non-kinetic, kinetic and non-hostile (weapons hold) zones,
  each a buffer (outward or inward) around an exclusion zone, defended asset or drawn boundary.
  An *Authority check* flags effectors whose envelope leaves its authority zone or overlaps a weapons hold.

### Sensors
- Types: **Radar, Camera, EO/IR, IR/Thermal, Laser Designator**, with type-specific fields:
  - Radar: min range (drawn as a dashed "blind" ring) and elevation limits.
  - Imaging sensors: day/night capability.
  - Laser designators: max lasing range, PRF code and beam divergence.
- **Terrain masking:** a DTED line-of-sight sweep shades what terrain hides from each sensor and
  reports the % of coverage with line of sight.
- **Radar presets:** RADA RPS-42, RADA nMHR, Trust SUADS and Blighter B202 Mk 2. Pick the drone class
  and number of panels, and range, beam width, min range and elevation are filled in.
- **Preset catalog in Settings:** edit the built-in presets (with reset to default) or add your own.

### Non-kinetic effectors (RF)
- **Multi-band jammers:** a list of bands (e.g. 433 / 915 MHz, 2.4 / 5.8 GHz, GNSS), each with EIRP,
  threshold and max range, plus drone-class band presets.
- **RF heat map** built from free-space path loss with a terrain penalty, shown in three colours
  (effective / marginal / insufficient). Bands combine as best band (UNION) or weakest band.

### Kinetic and directed-energy effectors
- Effector types with munition, detonation mode, min/max range, azimuth sectors, restricted wedges,
  elevation limits, ammo, status and fragmentation bands.
- **Directed energy (laser):** beam dwell, aperture, optional power (kW). Power density and effective
  range are calculated when power is known, otherwise an operator-entered range is used. A beam-intensity
  gradient replaces the frag rings. This is planning-grade only.

### Working with busy maps
- **Sites:** co-locate several assets (e.g. radar + EO + jammer) under one site name. The site gets a
  ring on the map, there is "Add to site", and moving one member offers to move them all. Assets are
  linked with checkboxes (sensor cues effector, radar hands off to EO, etc.).
- **Visibility:** an eye toggle on every asset, solo, per-type and per-site show/hide, and per-layer
  toggles inside an asset (e.g. hide frag bands but keep the max-range ring). Hiding only changes what
  is drawn: **hidden items still count in every analysis and still block shots.** Visibility is
  local to your device and never sent over CoT.
- Analysis runs in the background and is cached, so the UI stays responsive.

---

## 🗺️ Roadmap

The full roadmap, with the detail and status of every item (R0–R14), is in
**[docs/ROADMAP.md](docs/ROADMAP.md)**. The phases at a glance:

| Phase | Theme | Status | Main items |
|---|---|---|---|
| 1 | Foundation | ✅ Done (1.0.0–1.0.3) | Plugin framework, data model, CoT, settings, DTED, exclusion zones, intercept altitude, sensors, terrain masking, RF heat maps |
| 2 | Assets and planning workflow | ✅ Done (1.0.4–1.0.8) | Kinetic and directed-energy effectors, multi-band RF, zone shapes, engagement authority, sites and linking, visibility, sensor types, preset catalog |
| 3 | **Detection and intercept analysis** | ⏭️ **Next** | Drone detection simulation (R12), detection-floor probe and heat maps for Group 1/2 (R13), kinetic and non-kinetic intercept heat maps (R14), sensor gap analysis, type-aware filters (rest of R2) |
| 4 | Sensors in depth and obstacles | Planned | Optics/IR resolution, zoom, FOV and DRI rings (R11), field-measured walls with "never through it" masking (R8) |
| 5 | Laser-guided engagements | Planned | Laser-guided rocket with designator pairing (R4), laser-masking analysis (R5) |
| 6 | Engagement decision support | Planned | Engagement timeline (detect → decide → defeat), best-effector recommendation, friendly-fire deconfliction, MSA rings, dynamic frag cylinder |
| 7 | Briefing, sharing and release | Planned | PACE plan PDF, full-plan import/export (CoT / KMZ / data package), WinTAK push/pull, TAK.gov-signed release |
| Later | Deferred | — | Threat ingress corridors (editor, corridor gap analysis, per-corridor timelines) |

**Phase 3 in one sentence:** tap a spot and see which sensors would detect a Group 1 or Group 2 drone
at a given altitude, how low it can fly there and stay undetected, and whether your kinetic and
non-kinetic effectors can intercept it. Each answer is also available as a heat map layer that is
off by default.

---

## Try it (developer ATAK-CIV 5.6.0)

> **Version lock:** ATAK only loads a plugin whose `plugin-api` exactly matches the running ATAK
> (`com.atakmap.app@5.6.0.CIV` for this build).

1. Install the **developer ATAK-CIV 5.6.0** APK that comes with the SDK from [tak.gov](https://tak.gov).
   Uninstall any Play Store ATAK first, because they share a package name.
2. Download the APK from [Releases](https://github.com/widjit/skyrocket-shooter/releases) and run
   `adb install -r SKIARKT-1.0.8-civ-5.6.0-debug.apk`.
3. In ATAK go to **☰ → Plugins**. Tap the sync arrows if the list is empty, tick
   **SKY ROCKET SHOOTER (SKIARKT)** and choose **Load**.
4. Open **☰ → SKY ROCKET SHOOTER**. For logs, run `adb logcat | grep -i skiarkt`.

**Play Store / release ATAK** (CIV, MIL, GOV) only loads plugins signed through the TAK.gov
third-party pipeline. That is part of Phase 7; see [Release builds](#release-builds-and-takgov) below.
**WinTAK:** the APK is Android-only. WinTAK sees SKIARKT plans through the shared CoT types.

---

## Building

### Prerequisites
- JDK 17
- Android SDK with `platforms;android-34` and `build-tools;34.0.0`
- `local.properties` containing `sdk.dir=/path/to/Android/Sdk` (git-ignored)
- The ATAK-CIV 5.6.0 SDK `main.jar` copied to `sdk/5.6.0/main.jar` (not committed; see [sdk/README.md](sdk/README.md))
- A debug keystore at `keystores/debug.keystore` for developer builds (never committed; see [keystores/README.md](keystores/README.md))

### Commands
```bash
./gradlew assembleCivDebug                       # developer build -> app/build/outputs/apk/civ/debug/app-civ-debug.apk
./gradlew testCivDebugUnitTest                   # JVM unit tests
./gradlew assembleCivRelease                     # signed with your release key, or UNSIGNED if none configured
./gradlew assembleCivRelease -PatakVersion=5.5.0 # another 5.x host; needs sdk/5.5.0/main.jar
```
The `civ` / `mil` / `gov` flavours match the TAK plugin template and set `plugin-api` to `com.atakmap.app@<ver>.CIV|MIL|GOV`.

### ATAK SDK sources
| Priority | Source | How |
|---|---|---|
| 1 | ATAK-CIV SDK jar | `-PatakSdkJar=<path>`, else `sdk/<atakVersion>/main.jar` (default `sdk/5.6.0/main.jar`) |
| 2 | TAK Maven AAR | `-PatakSdkMaven=true -PtakRepoUrl=... -PtakRepoUser=... -PtakRepoPassword=...` |
| 3 | Bundled `:atak-sdk-stubs` | Only with `-PallowStubs`. API signatures only, enough to compile without tak.gov access. Always used for unit tests |

Build anything you install in the field against the real SDK (option 1 or 2), not the stubs.

### Release builds and TAK.gov
The build **refuses to sign a release with the debug keystore**. Use your own release key:
```bash
scripts/make_release_keystore.sh "CN=Your Org, O=Your Org, C=US"   # writes keystores/skiarkt-release.jks + keystore.properties (git-ignored)
./gradlew assembleCivRelease
```
CI can use `SKIARKT_KEYSTORE`, `SKIARKT_KEYSTORE_PASSWORD`, `SKIARKT_KEY_ALIAS` and `SKIARKT_KEY_PASSWORD` instead.
If no key is configured, the release APK is left unsigned, which is the form the TAK.gov pipeline needs.
For release ATAK, run `scripts/package_for_takgov.sh` and submit the zip to the TAK.gov **Third-Party
Plugin** pipeline (ATAK 5.6.0 / CIV). Gov / Mil deployments also need your organisation's accreditation
process; see [SECURITY.md](SECURITY.md).

### Plugin model (ATAK 5.x `IPlugin`)
- `app/src/main/assets/plugin.xml` declares one extension: `gov.tak.api.plugin.IPlugin` → `com.skiarkt.shooter.plugin.PluginLifecycle`.
- `PluginLifecycle` extends `com.atak.plugins.impl.AbstractPlugin` and registers the toolbar tool and the map component.
- An ATAK-CIV 4.6.0 build (legacy plugin model) is kept at tag **`v1.0.0-civ4.6.0`**. This branch is 5.x only.

---

## CoT schema
| Object | CoT type |
|---|---|
| Plan header | `b-m-p-skiarkt-plan` |
| Exclusion zone | `b-m-p-skiarkt-exczone` |
| Sensor | `b-m-p-skiarkt-sensor` |
| Non-kinetic effector | `b-m-p-skiarkt-nke` |
| Kinetic effector | `b-m-p-skiarkt-kinetic` |
| Engagement-authority zone | `b-m-p-skiarkt-authzone` |
| Defended asset | `b-m-p-skiarkt-defasset` |
| Threat corridor | `b-m-p-skiarkt-corridor` |

- Common attributes: `how="h-g-i-g-o"`, stale time 30 days by default (configurable), label in
  `<contact callsign>`, free text in `<remarks>`.
- SKIARKT-specific attributes are in a custom detail element. New attributes are optional, so older
  peers still parse newer events.
- Polygons use standard `<link point="lat,lon[,hae]"/>` elements plus `fillColor` / `strokeColor` /
  `strokeWeight` / `height`, so stock TAK viewers can draw them.

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
├── atak-sdk-stubs/              compile-only ATAK API mirror (unit tests)
├── assets/                      branding (icon, logos)
├── docs/                        ROADMAP, HANDOFF, screenshots
├── keystores/                   local signing keys (git-ignored)
└── sdk/                         drop the ATAK SDK here (git-ignored)
```

---

## Community
- Feedback, issues and feature requests: [GitHub Issues](https://github.com/widjit/skyrocket-shooter/issues)
- TAK Product Center, SDK and plugin signing: [tak.gov](https://tak.gov)

## License
Licensed under the [Apache License 2.0](LICENSE). Copyright 2026 SKIARKT Contributors.

ATAK, WinTAK and the TAK SDK belong to their respective owners and are not covered by this license.
