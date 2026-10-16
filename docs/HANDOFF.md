# SKIARKT — Project Handoff

Read this first when picking up the project in a new workspace. It captures context that lives
outside the code.

## Product
- **SKY ROCKET SHOOTER (SKIARKT)**, an ATAK-CIV 5.6.0 plugin for planning point defense against small drones.
- Package / applicationId: `com.skiarkt.shooter`. CoT type prefix: `b-m-p-skiarkt-`.
- Repo: `widjit/skyrocket-shooter` (private).

## Standing rules from the owner
- No chess references anywhere (the project was originally called ROOK; that name is retired).
- Every acronym letter must stand for a meaningful word.
- "SHOOTER" and "bottle rocket" are the core branding anchors.
- Never sign release builds with the debug keystore. Release signing uses the owner's external
  credentials (`scripts/make_release_keystore.sh`, `scripts/package_for_takgov.sh`).
- Engagement logic must never shoot through or at protected assets; account for their exclusion height.
- Obstruction walls are hard barriers: shooting in front of them is allowed, through them never (ROADMAP R8).
- Never merge PRs automatically; the owner reviews them.

## Current state (as of 2026-10-17)
- Latest build: **1.0.5 / versionCode 6**, GitHub pre-release `v1.0.5-civ-5.6.0-debug` (debug-signed APK attached).
- Work branch: `feature/phase4-kinetic-effectors-v1.0.4`, open as **PR #1**, not merged.
  `main` is still at 1.0.3 until PR #1 is merged.
- Unit tests: 73 passing (`testCivDebugUnitTest`).
- Done: exclusion zones (polygon), sensors, RF jamming heat map, kinetic effectors (Phase 4 MVP
  including Directed Energy with a speed-of-light default velocity), CoT sharing, autosave, PNG export.
- Next: `docs/ROADMAP.md` (R0 multi-asset sites first, Phase 5 core items, R7, R8).

## Key decisions
- **Java, not Kotlin.** There is no Kotlin toolchain, and the ATAK SDK jar has Kotlin 2.2 metadata.
  Kotlin metadata `e:` lines in build logs are harmless.
- **ATAK 5.x IPlugin model only.** ATAK 4.x is archived at tag `v1.0.0-civ4.6.0`.
- **Multi-sensor sites** (ROADMAP R0): keep each sensor/effector a separate map item, group them by
  a `siteUid`, and link them with checkbox pickers. A combined "site panel" was considered and rejected.
- Effective ranges are **radii** from the weapon center, not diameters.
- Fragmentation is drawn as static rings for now; the cylinder along the threat vector is Phase 5.

## Gotchas
- `app/src/main/assets/plugin.xml`: `<plugin>` must have **no attributes**, or ATAK silently loads
  0 plugins. Enforced by `PluginDescriptorTest`.
- Product flavors civ/gov/mil: use `:app:assembleCivDebug`; `assembleDebug`-style task names are ambiguous.
- Bump `pluginVersionCode` in the root `build.gradle` for every build installed on a device.
- A full gradle build can exceed short shell timeouts; run it in the background.
- Every version that's pushed also gets a GitHub pre-release with its APK attached.

## Setting up a fresh workspace
These files are git-ignored and must be supplied separately:
1. **ATAK-CIV 5.6.0 SDK**: extract `main.jar` from the SDK zip to `sdk/5.6.0/main.jar` (see `sdk/README.md`).
   Unit tests run without it (they use `atak-sdk-stubs`); APK builds need it.
2. **Debug keystore**: `keystores/debug.keystore`. Reuse the existing one so new builds install as
   updates over 1.0.x on test devices. A new keystore means uninstalling the plugin on every device first.
3. `local.properties`: `sdk.dir=<Android SDK path>`.

Build and test:
```
./gradlew :app:assembleCivDebug :app:test
```
The APK is written to `app/build/outputs/apk/civ/debug/app-civ-debug.apk`; release copies are named
`SKIARKT-<version>-civ-5.6.0-debug.apk`.
