# Code Review & Cleanup Pass — 1.0.8 (Oct 2026)

Scope: the whole `app/src/main/java/com/btlrckt/bottlerocket` tree (~14.5k lines) plus resources.
Goal: remove duplication and wasted work **without changing behaviour** for users.

Off limits on purpose: `plugin.xml` (the `<plugin>` tag stays attribute-free), the manifest
plugin-api, PluginLifecycle wiring, signing, Parcel field order, CoT types/attributes, JSON
field names and enum constant names (all persisted or sent over the wire). The safety logic
was not touched: never shoot at or through protected assets (including exclusion height), OK
to shoot before an obstruction wall but never through it, hidden items still count in analysis.

## Results

| Check | Before (main @ 40f954d) | After |
|---|---|---|
| Unit tests (`testCivDebugUnitTest`) | 119 passing | **126 passing**, 0 failures (7 new) |
| `assembleCivDebug` | OK | OK |
| On-device test | — | **Not done** (unit tests use stub SDK classes) |

## What changed

### 1. Enum boilerplate → `util/EnumUtils` (commit `8cf4b73`)
- `KineticEffectorType`, `MunitionType`, `DetonationMode` and `EffectorStatus` each had the
  same `next()` and lenient `fromName()` code.
- Both now live in `EnumUtils.next(E)` and `EnumUtils.fromName(Class, String, E)`.
- Each enum keeps its public methods as one-line delegates, so callers and persisted names
  are unchanged. Matching is the same as before: trimmed and case-insensitive.
- `SensorType` and `DayNight` were **left as they are**. They match names exactly
  (case-sensitive `valueOf`), and switching them to the lenient lookup would quietly
  change behaviour.
- Test: `EnumUtilsTest` (wrap-around, null/unknown handling, every constant round-trips
  through `name()`).

### 2. Shared UI helpers (commit `3e7a565`)
- **`ui/SimpleSeek`**: one SeekBar listener with no-op start/stop handlers. It replaces
  copies nested in `PointItemPanel` and `ExclusionZonePanel` and the full anonymous
  listeners in `AuthorityPanel` and `SettingsPanel`.
- **`ui/Swatches.drawable(...)`**: one colour-swatch drawable instead of three identical
  `swatchDrawable` bodies in `PointItemPanel`, `ExclusionZonePanel` and `AuthorityPanel`.
  Same corner radius, borders and highlight colour.
- **`util/Numbers.parseLenient`**: one parser for numbers users type in. It trims input,
  accepts `,` as the decimal separator, and returns NaN for blank, malformed or infinite
  input. `PointItemPanel.parseNumber`, `SensorPresetsUi.parse` and `SettingsPanel.parse`
  now call it.
  - *One small difference:* the preset and settings fields used to accept the literal text
    `Infinity` (or values that overflow, e.g. `1e400`) as a number. They now get NaN, which
    those fields already treat as "invalid". Item panels already behaved this way.
- Test: `NumbersTest`.

### 3. Faster authority checks (commit `cc43923`)
- `AuthorityEvaluator.checkEffector` samples each effector's engagement envelope on a polar
  grid. For every sample and every zone it called `inside()`, which re-ran `resolveBase`
  (a plan lookup by uid), the centroid, and the projection of every polygon vertex.
- New `PolygonBuffer.Prepared` does that work **once per zone per check**. Each sample then
  only projects the sample point and loops over the cached vertices.
- The maths is exactly the same: same frame, same vertex projection, same segment-distance
  order, same `pointInPolygon` call. `PolygonBufferPreparedTest` checks 2,000 random
  points × 5 buffers (inward and outward) on a concave polygon and requires
  **bit-identical** signed distances and identical `contains` results.
- The existing `AuthorityTest` suite still passes unchanged.
- Expected gain *(estimate, not profiled)*: the work per sample goes from O(zones × vertices)
  projections plus lookups to O(zones × vertices) segment tests only. For typical zones with
  4–20 vertices that is roughly 2–5× less work in this loop.

### 4. Unused resources (commit `87a1d5b`)
- Removed `nk_marker_color` and `ke_type_directed_energy` from `strings.xml`.
- How this was checked: every string was searched for as `R.string.X` / `@string/X` across
  Java, `res/`, the manifest and `assets/`. These two were the only ones not used anywhere,
  and the code has no `getIdentifier` lookups.
- An earlier automated pass reported about 49 unused strings. That was wrong: most of them
  are used from layout XML.
- No unused imports were found.

### 5. Roadmap (commit `10f8703`)
- Added **R15. KMZ / KML export** to Phase 7 in `docs/ROADMAP.md` and `README.md`.

## Findings reviewed and deliberately not changed

| Finding | Why it was left alone |
|---|---|
| `SensorPreset` (enum) and `SensorPresetDef` repeat about 8 helper methods | Merging them affects preset JSON and UI preset code paths. The only real difference is a `max(0, minRange)` clamp, which never triggers for the built-in presets. This is worth doing together with the next preset feature, with dedicated tests. |
| `OverlayManager.renderItem` reads `sweepParams()` / `heatmapParams()` for each item | These are a few in-memory settings reads per item, so the cost is negligible. Caching them would add a stale-settings risk for very little gain. |
| `SiteGroups.otherMembers` "calls `pointItems()` twice" | Wrong report: it calls it once. |
| `InterceptAltitudeCalculator` "`inside` per sample" | It already projects the zones once (local `Geo` cache). Nothing to do. |
| `LosSweep` / `RfHeatmap` / `CoverageGeometry` per-cell trig | Moving the calculations around (e.g. per-ray sin/cos) would change floating-point results in the last bits, and these feed what is drawn on the map and the analysis. They already run off the UI thread and are cached by `CoverageEngine`. Profile on a device before touching them. |
| "Eye-button listener leaks" | Not a leak: the views are rebuilt and garbage-collected along with their listeners. |
| Large panels (`PointItemPanel` 1k lines, `ExclusionZonePanel`, `AuthorityPanel`) | A shared form/dialog builder would remove a lot of code, but the risk is high without on-device UI tests. See the follow-ups below. |

## Recommended follow-ups (priority order)
1. **Profile on a device** (Android Studio CPU profiler) with a busy plan: LOS sweeps,
   heat maps, many zones. Only optimise the hot spots the profiler confirms.
2. **Merge the sensor-preset code** so the `SensorPreset` enum delegates to
   `SensorPresetDef`, with JSON round-trip tests.
3. **Shared form builder** for the three big panels: labelled fields, distance fields with
   unit pre-fill, swatch rows, and delete-confirmation dialogs.
4. **Instrumented UI smoke tests** (Espresso, or a manual checklist in `docs/HANDOFF.md`) so
   that UI refactors like item 3 can be verified.
5. Move `ExclusionZoneCotSerializer.parseDouble/parseInt` onto shared util helpers when the
   CoT code is next touched (left alone now because CoT parsing is wire-format-sensitive).
