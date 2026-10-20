# BTL RCKT (formerly SKIARKT) — Project Handoff

Read this first when picking up the project in a new workspace. It captures context that lives
outside the code.

## Product
- **BOTTLE ROCKET (BTL RCKT)** (Base Trial Layout Required Coverage Knowledge Tool; formerly SKY ROCKET SHOOTER / SKIARKT), an ATAK-CIV 5.6.0 plugin for planning point defense against small drones. Only the display name changed; the internal IDs below still say "skiarkt" for compatibility.
- Package / applicationId: `com.skiarkt.shooter`. CoT type prefix: `b-m-p-skiarkt-`.
- Repo: `widjit/skyrocket-shooter` (private).

## Standing rules from the owner
- No chess references anywhere (the project was originally called ROOK; that name is retired).
- Every acronym letter must stand for a meaningful word.
- "Bottle rocket" is the core brand; "Shooter" lives on in the tagline ("Be a Shooter too") and the Shooters user group.
- Never sign release builds with the debug keystore. Release signing uses the owner's external
  credentials (`scripts/make_release_keystore.sh`, `scripts/package_for_takgov.sh`).
- Engagement logic must never shoot through or at protected assets; account for their exclusion height.
- Obstruction walls are hard barriers: shooting in front of them is allowed, through them never (ROADMAP R8).
- Never merge PRs automatically; the owner reviews them.

## Current state (as of 2026-10-18)
- Latest build: **1.0.7 / versionCode 8**, GitHub pre-release `v1.0.7-civ-5.6.0-debug` (debug-signed APK attached).
- 1.0.7 adds ROADMAP R0 (sites + checkbox linking), R1 (multi-band RF), R3 (circle/rectangle
  exclusion zones), R7 (directed-energy fields + laser model) and R9 (per-asset visibility).
  Not verified on a device yet.
- Work branch: `feature/phase4-kinetic-effectors-v1.0.4`, open as **PR #1**, not merged.
  `main` is still at 1.0.3 until PR #1 is merged.
- Unit tests: 96 passing (`testCivDebugUnitTest`).
- Done: exclusion zones (polygon), sensors, RF jamming heat map, kinetic effectors (Phase 4 MVP
  including Directed Energy with a speed-of-light default velocity), engagement-authority perimeters
  (ROADMAP R6: `EngagementAuthorityZone`, `PolygonBuffer`, `AuthorityEvaluator`, `AuthorityPanel`,
  `AuthorityZoneOverlay`), CoT sharing, autosave, PNG export.
- Owner priorities (2026-10-18): threat corridors are deferred; R7 laser power (kW) must stay
  optional (power density only when entered, otherwise qualitative effective range).
- Next: `docs/ROADMAP.md` (R2 sensor types, R4/R5 LGR + masking, R8 walls, R9b per-layer
  toggles, remaining Phase 5 core items). R6 still
  needs PACE PDF and timeline integration once those exist.

## Key decisions
- **Java, not Kotlin.** There is no Kotlin toolchain, and the ATAK SDK jar has Kotlin 2.2 metadata.
  Kotlin metadata `e:` lines in build logs are harmless.
- **ATAK 5.x IPlugin model only.** ATAK 4.x is archived at tag `v1.0.0-civ4.6.0`.
- **Multi-sensor sites** (ROADMAP R0): keep each sensor/effector a separate map item, group them by
  a `siteUid`, and link them with checkbox pickers. A combined "site panel" was considered and rejected.
- Effective ranges are **radii** from the weapon center, not diameters.
- Fragmentation is drawn as static rings for now; the cylinder along the threat vector is Phase 5.
- Authority zones store a signed buffer from a base polygon. Linked zones (`baseUid`) follow the live
  base and keep a `points` snapshot for CoT recipients. Containment uses exact signed distance;
  the drawn ring is an approximation on very concave shapes. Non-hostile zones always win.

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
