# BOTTLE ROCKET (BTL RCKT) — Roadmap

Current build: **1.0.10** (ATAK-CIV 5.6.0, debug pre-release, shared for feedback). Feature summary
and install steps are in the [README](../README.md).

Items below are planned, not implemented, unless marked **Implemented**. Everything new is saved and shared as CoT, like the
existing objects. All distances follow the global unit setting (ft / m / km).

## Phases

The R-items are grouped into phases. Phases 1–2 are done. **Phase 3 is next**, and it is the main
thing I want feedback on.

| Phase | Theme | Status | Items |
|---|---|---|---|
| 1 | Foundation | ✅ Done (1.0.0–1.0.3) | Plugin framework, data model, CoT schema, settings, DTED helper, polygon exclusion zones, minimum safe intercept altitude, sensors with terrain masking, RF jammer heat maps |
| 2 | Assets and planning workflow | ✅ Done (1.0.4–1.0.8) | Kinetic effectors, directed energy (R7), multi-band RF (R1), zone shapes (R3), engagement authority (R6), sites and linking (R0), visibility (R9), sensor types and fields (R2, partial), preset catalog (R10) |
| 3 | **Detection and intercept analysis** | ⏭️ Next | Drone detection simulation (R12), detection-floor probe and heat maps (R13), kinetic / non-kinetic intercept heat maps (R14), 3-D altitude coverage layers (R16), sensor gap analysis, type-aware filters (rest of R2: night / IR-only coverage) |
| 4 | Sensors in depth and obstacles | Planned | Optics/IR definition and DRI rings (R11), field-measured walls and "never through it" masking (R8), terrain quality indicator (R17) |
| 5 | Laser-guided engagements | Planned | Laser-guided rocket with designator pairing (R4), laser-masking analysis (R5) |
| 6 | Engagement decision support | Planned | Engagement timeline (detect → decide → defeat), best-effector recommendation, friendly-fire deconfliction, MSA rings, dynamic frag cylinder along the threat vector |
| 7 | Briefing, sharing and release | Planned | PACE plan PDF, KMZ / KML export (R15), full-plan import/export (CoT / data package), WinTAK push/pull, decision-maker briefing export, TAK.gov-signed release build |
| Later | Deferred | — | Threat ingress corridors: corridor editor, corridor-based gap analysis, per-corridor timelines |

**Why this order:** Phase 3 turns the plan you have already laid out into answers: what can see a
drone here, how low can it fly undetected, and can I engage it? Phase 4 makes those answers more
accurate (optics detail, walls). Phases 5–6 build on them for engagement decisions, and Phase 7
packages everything for briefing and for release ATAK.

**Feedback wanted:** is this the right order? What is missing? Which Phase 3 item would you use first?
Please use [GitHub Issues](https://github.com/widjit/skyrocket-shooter/issues).

---

## R0. Multi-sensor / multi-effector sites — separate assets, linked and co-located  — **Implemented in 1.0.7**

**Status (1.0.7):** `PointItem.siteUid` (site name doubles as uid, case-insensitive, trimmed) on
sensors, NK and kinetic effectors, carried in Parcel, plan JSON and CoT (`siteUid` attribute).
`SiteGroups` builds sites; the overlay draws a site ring (furthest member + 15 m, min 25 m). The
edit form has a *Site* picker (None / existing / New site…). Changing a member's position offers
to move the other members by the same bearing and distance. Lists show an *Add to site* button
(new item pre-filled at the site's mean position, which equals the shared position for
co-located members) and a site summary with a per-site hide/show. Cross-type checkbox pickers
replace the raw UID text fields: kinetic → sensors and designators (sensors + kinetic), NK →
sensors, sensor → sensors (new `linkedSensorUids` on sensors and NK effectors). Unknown UIDs are
kept as checked "Unknown asset" entries so nothing is silently dropped.

Real-world sites typically have two or more sensors (RADAR, EO camera, IR/thermal) and one or more
effectors (SHORAD, jammer, laser) at the same point. Each type belongs to a different planning
workflow so a single combined panel does not scale. The chosen model keeps everything as separate
independent assets that are precisely co-located and cross-linked.

### R0a. `siteUid` — named site groups

Add a nullable `String siteUid` field to `PointItem` (the shared base class for sensors,
kinetic effectors, and non-kinetic effectors).

- Items sharing the same `siteUid` form a **site group**.
- A subtle site-group ring is drawn around all members on the map so the relationship is visually
  obvious at a glance.
- Editing the lat/lon of any member prompts *"Move all N site members?"* — one tap keeps every
  co-located asset precisely at the same coordinates without having to move each one manually.
- Adding a new asset lets the user pick an existing site from a dropdown or create a new one.
- Removing an asset from a site (clearing its `siteUid`) removes only that item; co-located
  neighbours are unaffected.

CoT: `siteUid` is serialised as an attribute on every asset's CoT detail element. Recipients can
reconstruct site groups from the matching UIDs.

### R0b. Cross-type checkbox linking

Each asset panel shows a scrollable checklist of every other asset in the plan (by label, not
raw UID), grouped by type: sensors, kinetic effectors, non-kinetic effectors. The user ticks the
assets that this asset depends on or feeds into.

Supported link directions:

| From | To | Purpose |
|---|---|---|
| Kinetic effector | Sensor | Cue: sensor drives engagement decision |
| Kinetic effector | Sensor (laser designator) | Designation: LGR requires a laser code match |
| Non-kinetic effector | Sensor | Cue: jammer directed by sensor coverage |
| Sensor | Sensor | Handoff: e.g. radar cues IR for confirmation |

These replace the current raw UID text fields (`linkedSensorUids`, `linkedLaserDesignatorUids`)
with a human-readable picker. The underlying data model and CoT schema are unchanged.

### R0c. Sensor panel — multiple sensor types at one asset

*Separates from R0a/b but ships together.* The sensor list screen needs to make it easy to place
multiple sensors at the same site. The current flow works but offers no shortcut. Two additions:

- **"Add to site"** button on the sensor list: pre-fills lat/lon and siteUid from an existing
  asset so a second sensor snaps exactly to the same position without any manual coordinate entry.
- **Site summary** in the plan list header shows how many assets share each siteUid and which
  types are present (e.g. "Alpha-1: 3 sensors, 1 kinetic, 1 NK").

### Dependencies and rollout order
`siteUid` on the model (R0a) ships first — it is additive, backward-compatible, and unblocks the
rest. The checkbox picker (R0b) and site-summary header (R0c) ship together in the same sprint.

---

## R1. Multi-frequency non-kinetic (RF) effectors  — **Implemented in 1.0.7**

**Status (1.0.7):** `RfBand` list on `NonKineticEffector` (label, MHz, EIRP, threshold, max range,
enabled) with a band editor, drone-class presets and a combined heat map in **UNION** (best band
wins) or **WEAKEST** (worst enabled band) mode. With no bands the legacy single-band fields are
used unchanged. Bands travel in Parcel, JSON and CoT (`bands`, `heatmapMode`). Per-band heat-map
toggling is done via each band's *enabled* checkbox (no separate per-band layer).

- A jammer has a **list of bands**, not one frequency (e.g. 433 MHz, 915 MHz, 2.4 GHz,
  5.8 GHz, GNSS L1/L2). Each band has its own ERP, jamming threshold, and max range.
- Heat map per band (toggle each) or combined (union / weakest band).
- Drone-class presets list which bands a class uses, so the map shows where that class is
  actually denied.
- Builds on the existing terrain-aware RF model. Most of the work is the data model, UI, and CoT schema.

## R2. Sensor types  — **Partially implemented in 1.0.8**

**Status (1.0.8):** done: IR_THERMAL and LASER_DESIGNATOR types; min-range field with a dashed
"Blind" ring on the map; elevation min/max; type-specific fields in the sensor edit form (radar:
min range + elevation; EO/IR: day/night; designator: max lasing range, PRF code, beam divergence);
the preset picker fills min range and elevation. Remaining: using elevation limits, day/night and
designator fields in gap analysis / MSA filters, per-type icons, radar min RCS.

- Types: **RADAR**, **Optical (EO camera)**, **IR / thermal**, **Laser designator**.
- Each type has its own icon and type-specific fields. Common fields: range, beam width / FOV, AGL,
  azimuth sector. Type-specific examples: radar min detectable RCS; EO/IR day/night capability;
  designator max lasing range, laser code, beam divergence.
- Gap analysis and MSA rings can filter by sensor type (e.g. "IR-only coverage at night").
- Laser designators are prerequisites for R4/R5.

## R3. Exclusion-zone shapes  — **Implemented in 1.0.7**

**Status (1.0.7):** `ExclusionZoneShape` POLYGON / CIRCLE / RECTANGLE (center, size, rotation).
Circles and rectangles are converted to rings for the overlay, the intercept-altitude calculator
and the heat map, so height AGL still applies. Shape travels in Parcel, JSON, CoT and XML.

- **Circle** (center + radius), **rectangle / square** (two corners, or center + size + rotation),
  and **polygon** (existing).
- All shapes keep height AGL, colors, and opacity, and work in the intercept-altitude calculator
  and the engagement heat map.

## R4. Laser-guided rocket (LGR) kinetic effector
- Effector type **LASER_GUIDED_ROCKET** (already in the type enum). Seeker FOV / acquisition range
  and laser code are already on the model. This item completes the *not mission-capable* logic and
  the engagement timeline integration.
- **Must be paired with one or more laser designators** (R2). The effector is shown as
  *not mission-capable* (and excluded from best-effector recommendations) when no paired
  designator is placed, in range, or code-matched.
- Fragmentation along the munition route reuses the existing kinetic model.

## R5. Laser masking analysis (designator not co-located with launcher)
For each candidate target point / ingress corridor, the analysis checks:
1. **Designator → target LOS** (DTED terrain; later, obstacles and exclusion-zone roofs).
2. **Seeker → laser spot LOS** along the rocket's flight path from the launcher.
3. **Geometry constraints**: the angle between the designator line and the rocket's approach must
   fall inside a user-set *attack cone*. The rocket must also stay out of a user-set *designator
   safety fan* around the designator line. Angles are user-configurable, not hard-coded.
4. Max lasing range and seeker acquisition range.

Output: an overlay of where a target can be **lased and engaged**, where it is **lased but
masked from the seeker**, and where it **can't be lased at all**. It also lists the best
designator position(s) for each launcher. Results feed into the engagement timeline.

## R6. Engagement-authority perimeters (buffers)  — **Implemented in 1.0.6**

**Status (1.0.6):** new *Engagement Authority* section in the side panel.
- Zones are created from an exclusion zone, a defended asset, or a drawn boundary. Each has a type,
  label, signed buffer (positive = outward, negative = inward, ±100 km max), color, fill opacity,
  dashed outline and remarks. Linked zones follow later edits to their base; the base outline is
  also stored as a snapshot so a zone shared over CoT (`b-m-p-btlrckt-authzone`) still works on a
  device that lacks the base item.
- Containment is exact (signed distance to the base perimeter vs. buffer). The map ring is a
  round-joined offset outline; on highly concave shapes the drawn ring is approximate.
- Rule engine: a point authorizes an effector class only when inside a zone of that class and
  outside every non-hostile zone (non-hostile always wins).
- *Authority check* in the panel samples every kinetic / NK effector's engagement envelope (respects
  min range and restricted azimuths) and flags: no authority zone, envelope outside authority,
  envelope overlapping weapons hold, effector inside weapons hold, linked zone of the wrong class.
  A kinetic effector's *engagement authority perimeter UID* can name one zone to restrict it to.
- Zones are drawn on the map and in the PNG export.

**Still to do:** PACE PDF output (PACE not built yet), engagement-timeline / best-effector
integration (timeline not built yet), R3 shapes (circle / rectangle / line) as bases.

Original requirement:

Buffer zones offset from a base perimeter (a defended asset, an exclusion zone, or a drawn boundary).
Each zone has its own distance, color, and label:
- **Non-kinetic engagement authority**: area where RF/NK effectors are authorized.
- **Kinetic engagement authority**: area where kinetic effectors (including LGRs) are authorized.
- **Non-hostile authority**: area where tracks are treated as non-hostile and no engagement is
  authorized (weapons hold).

Rules:
- Buffers are distances outward (or inward) from the base shape. Any R3 shape can be a base.
- Engagement timeline, deconfliction, and best-effector logic only recommend an effector when the
  threat position is inside that effector class's authority zone and outside non-hostile zones.
  Violations are flagged.
- Shown on the map and in the PACE PDF.

## R7. Directed Energy — frag / field suppression fields  — **Implemented in 1.0.7**

**Status (1.0.7):** with Directed Energy selected the panel hides the ballistic and ammo fields
and shows beam dwell, aperture, optional laser power (kW), optional qualitative effective range,
suppression-zone radius and damage-fluence threshold. `LaserPerformance` computes power density
and fluence at range (diffraction + jitter spot, clear-air extinction) and derives the effective
range when power is entered; otherwise the qualitative range is used, then max range. Never
blocks. The overlay draws a beam-intensity gradient, the effective-range ring and the suppression
outline instead of frag rings. **Planning-grade only:** no turbulence, thermal blooming or target
aspect; constants are fixed (1.07 µm, M² 1.5, 5 µrad jitter, 0.2/km).

Phase 4 added Directed Energy as a kinetic type with speed-of-light velocity default. Fields that
don't apply to beam weapons (munition type, detonation mode, frag radius, ammo count / reload time)
are still shown in the panel but mean nothing for a laser. Planned work:
- Hide / grey out inapplicable fields when Directed Energy is selected.
- Add laser-specific fields: beam dwell time, aperture diameter, and an effective suppression zone
  (replaces frag corridor).
- **Laser power (kW) is optional.** Operators often won't know it. When power (and aperture) is
  entered, compute power density at range (W/cm²) and derive the effective range from it. When it
  is blank, use the operator-entered qualitative effective range instead — never block on it.
- Overlay: replace concentric frag rings with a beam-intensity gradient along the threat vector
  (complements the Phase 6 dynamic-frag-cylinder item for projectiles).

## R8. Field-measured obstacle walls (laser range finder + basic trigonometry)  *(long-term)*

Site survey tool: measure a tree line, building, or berm with a laser range finder (LRF), turn the
readings into an object height, and draw it on the map as a **wall**. Effectors can shoot *in front
of* a wall but **never through it**.

### R8a. Height from LRF readings
The observer position is the ATAK self-marker (GPS) or a point tapped on the map. Observer eye
height `e` (default 1.7 m, editable) is added in modes 1 and 2 (mode 3 measures base and top
directly, so it doesn't need it). Three input modes:

| Mode | User enters | Height of object |
|---|---|---|
| Distance + distance | Horizontal ground distance to the base `D`, slant distance to the top `S` | `h = e + √(S² − D²)` |
| Distance + angle | Horizontal ground distance `D`, inclination angle to the top `θ` | `h = e + D · tan θ` |
| Two-shot (uneven ground) | Slant distance + angle to the base (`S₁`, `θ₁`) and to the top (`S₂`, `θ₂`) | `h = S₂ · sin θ₂ − S₁ · sin θ₁` |

- Mode 1 matches the "ground distance, then distance to the top" workflow. Mode 3 is for sloped
  ground where the base is above or below the observer.
- Input checks: `S` must be larger than `D`; angles must be between −89° and +89°.
- Show the result with a ± band from the LRF's stated accuracy (e.g. ±1 m), so the planner
  knows how much to trust it.
- Optional: drop the wall endpoint from the reading itself (observer + compass bearing + ground
  distance) instead of tapping the map.

### R8b. Drawing the wall
- Draw a straight line (2 points) or a polyline (many points) on the map.
- Each point has its own height, from an R8a measurement or typed in, so a tree line that rises
  or dips is modelled point by point. The height between points is interpolated along the line.
- Fields: label, kind (tree line / building / berm / other), height per point, safety margin
  above the top (default 2 m), colour.
- Shared as CoT (type `b-m-p-btlrckt-wall`) and saved with the plan like every other object.

### R8c. "Never through it" engagement rule
For each effector, a wall casts a **masking shadow** on the far side:
- From the muzzle height `hₘ` over the wall top (`h_wall` + margin, at ground distance `d_w`), a
  target at ground distance `r > d_w` behind the wall can only be engaged when its altitude is
  above the clearance line: `alt_min(r) = hₘ + (h_wall + margin − hₘ) · r / d_w`.
- Targets in front of the wall are unaffected (the "shoot before it" case).
- The fragmentation radius is added to the margin, so the frag corridor clears the wall too.
- Map overlay: a red "no-shoot below X" shadow behind each wall for each effector, and the
  clearance altitude on tap.
- Engagement timeline and best-effector logic skip any shot whose line crosses a wall below its
  top. Violations are flagged.
- This uses the same clearance check as protected-asset exclusion heights (never shoot through or
  at a protected asset), so walls and exclusion zones share one masking engine.
- Optional: the same walls block sensor line of sight (e.g. radar masked by a tree line), feeding
  sensor gap analysis.

## R9. Per-asset visibility toggles  — **Implemented** (R9a, R9c in 1.0.7; R9b in 1.0.8)

**Status (1.0.7):** eye button on every row (sensors, NK, kinetic, exclusion zones, authority
zones); tap toggles, long-press solos / restores. Per-type Show all / Hide all, per-site toggle,
greyed rows with "(hidden)", hidden counts in list and plan header, and a *Show everything* button
in the main menu. `VisibilityStore` saves to `visibility.btlrckt.json` beside the autosave; never
in CoT or the plan JSON. Only the overlay consults it, so analysis still counts hidden items.
PNG export asks whether to include hidden assets in the schematic and notes the hidden count in
the header. The live map capture always shows what is currently drawn. PACE PDF does not exist
yet.

**Status (1.0.8):** R9b done: per-layer "Layers" checkboxes in the Sensor, Kinetic and NK edit
forms (e.g. hide a sensor's min-range ring or a kinetic effector's frag bands). `VisibilityStore`
uses compound keys (`uid#layer:id`); layer keys are dropped with their asset and cleared by
*Show everything*.

Busy plans get cluttered: overlapping coverage rings, heat maps, and frag bands from many assets.
The planner can show or hide each asset's map drawing individually.

### R9a. Controls
- **Eye icon on every row** in each asset list (exclusion zones, sensors, non-kinetic effectors,
  kinetic effectors, and later walls and buffers). Tap to show or hide that one asset.
- **Per-type "show all / hide all"** at the top of each list.
- **Per-site toggle** (with R0): hide or show every asset that shares a `siteUid` in one tap.
- **Solo**: long-press the eye to show only that asset and hide everything else; long-press
  again to restore the previous visibility.
- **Show everything** button in the main panel, so nothing stays hidden by accident.
- Hidden assets stay in the list, greyed out, with a "hidden" tag, and still open for editing.
- The panel header shows a count, e.g. "3 hidden", whenever anything is hidden.

### R9b. Optional finer control  — **Implemented in 1.0.8**
- Per-layer toggles inside one asset, e.g. show a kinetic effector's max-range ring but hide its
  frag bands, or show a sensor marker but hide its coverage.

### R9c. Rules
- **Hiding only changes what's drawn. Hidden assets still count in every analysis.** A hidden
  exclusion zone, protected asset, or wall still blocks shots, and hidden sensors and effectors
  still count in coverage, gap analysis, the engagement timeline, and best-effector logic. This
  keeps the "never shoot at or through protected assets or walls" rules from being switched off
  by accident.
- Visibility is a **personal view setting on this device**. It is saved with the local autosave
  and restored after restart, but it is **not sent over CoT**: hiding an asset on your map never
  hides it on a teammate's map.
- PNG export and the PACE PDF draw what's visible, with an option to include all assets. The
  legend notes when assets are hidden.

## R10. Sensor preset catalog  — **Implemented in 1.0.8**
(v1.0.8) Sensor preset catalog in Settings — edit built-in presets, add/delete custom ones, persist
JSON, merged picker in SensorPanel.
- Settings → *Sensor presets*: one row per preset with Edit, Reset (built-in, enabled when edited)
  or Delete (custom), and an Add button.
- Saved in `sensor_presets.btlrckt.json` beside the autosave: overrides keyed by built-in id plus
  the custom list. Built-ins are never deleted, only reset.
- The sensor panel's preset picker shows built-ins (with overrides applied) followed by customs.

## R11. Optics/IR definition and PID rings  *(pending)*
Optics/IR definition and PID rings: per-sensor resolution, zoom and HFOV for day and IR/thermal
channels; Detect/Recognise/Identify (DRI) range rings for Group 1 and Group 2 UAS that clip to the
terrain/building mask so blind spots are preserved.
- Builds on R2 (EO / IR_THERMAL types and the day/night field).

## R12. Drone detection simulation  *(pending)*
Answers the question: *"If a Group 1 or Group 2 drone is at this point and altitude, which of my
sensors can detect it?"*

**Inputs**
- A point on the map: long-press, drop a marker or type in coordinates (MGRS / lat-lon).
- Drone altitude, entered as AGL or MSL. The default is AGL; MSL is converted using the elevation
  data under the point.
- Drone class: **Group 1** (< 20 lb, < 1,200 ft AGL, < 100 kt) or **Group 2** (21–55 lb,
  < 3,500 ft AGL, < 250 kt). Out-of-envelope altitudes get a warning but are still evaluated.

**Checks for every sensor in the plan**
1. **Range:** slant range from the sensor to the drone. Must be ≥ min range (the radar blind ring,
   R2) and ≤ the class range. For presets the class range comes from the preset's target-class
   ranges (R10), e.g. a micro/Group 1 versus a small/Group 2 entry. Otherwise it is the sensor's
   own range.
2. **Azimuth:** the bearing falls inside the sensor's azimuth / beam width.
3. **Elevation:** the look-up angle falls inside the sensor's elevation min/max (R2).
4. **Line of sight:** terrain and building masking along the ray, using the same mask as the
   coverage overlay, evaluated at the drone's height.
5. **Type rules:** imaging sensors (camera / EO-IR / IR-thermal) respect day/night (with a time-of-day
   toggle). Laser designators are listed as "can designate" if within max lasing range, not as a
   detector. For RF detectors the drone is assumed to be emitting, with a toggle to mark it as RF-silent.
   Once R11 is in, imaging sensors also report Detect / Recognise / Identify.
6. **Hidden assets** (R9) are still evaluated, because visibility is view-only.

**Output**
- A results list for each sensor: ✅ DETECTS / ❌ NO, with the first reason it fails (out of range,
  inside blind ring, outside azimuth, outside elevation, terrain-masked, building-masked,
  day-only at night). It also shows slant range and margin.
- On the map: a drone marker at the point, plus lines to each sensor: solid green if it detects,
  dashed red if blocked (marking the masking point where known).
- A summary line, e.g. "Detected by 2 of 5 sensors (RPS-42 #1, EO/IR #2)", with a warning when
  **zero** sensors detect it.
- Optional follow-ons: drag the drone marker to update live; sweep altitude to report the lowest
  detectable altitude at that point; an "Effectors able to engage" section reusing the existing
  shot-safety rules (never through or at protected assets, never through obstruction walls).

**Notes**
- The calculation is local only. Nothing is broadcast over CoT unless the user shares the
  simulation marker.
- Range values are nominal datasheet figures; RCS, clutter and weather are not modelled in the
  first version.
- Unit-test the per-sensor check (geometry + reasons) as a pure model function, independent of the
  map UI.

## R13. Minimum detection altitude — point probe and heat maps  *(pending)*
The reverse of R12: instead of asking "can a drone at this height be seen?", it asks *"how high does a
drone have to be at this spot before something detects it?"*

### R13a. Point probe
- Tap a location (probe mode) to get the **lowest altitude AGL at which at least one sensor detects a
  drone there**, reported separately for **Group 1** and **Group 2**.
- The probe also shows which sensor detects first, the altitude band that is detected (some spots
  have a ceiling as well as a floor, e.g. above a radar's elevation limit or inside a blind ring), and
  "not detected at any altitude up to the class ceiling" when that applies.
- The calculation searches altitude from 0 AGL up to the class ceiling (Group 1 1,200 ft, Group 2
  3,500 ft) in user-set steps (default 10 m), running R12's per-sensor check for every sensor at
  each height.

### R13b. Detection-floor heat map
- A separate analysis layer: a grid over the plan area (or the current view), with each cell
  coloured by its minimum detection altitude. Low (detected near the ground) is green, high is
  amber/red, and never detected is hatched or black.
- **Two maps: Group 1 and Group 2.** Each uses that class's sensor ranges (R10 preset target
  classes) and altitude ceiling.
- **All sensors are combined:** the cell value is the lowest floor across every sensor, including
  min-range blind rings, azimuth/elevation limits, terrain/building masking and day/night (with a
  day/night toggle).
- A legend with altitude bands, an adjustable grid resolution, and a "Recompute" button. Results
  are cached until a sensor, the terrain data or the settings change.

## R14. Intercept heat maps — kinetic and non-kinetic  *(pending)*
The same idea as R13b, but for engaging the drone rather than detecting it: *"how high must a drone be
at this spot before I can intercept it?"*
- **Kinetic layer:** the minimum (and maximum) altitude at which at least one kinetic effector can
  legally engage. It takes into account effector min/max range and altitude, azimuth sectors and
  restricted wedges, elevation limits, and line of sight. It follows the existing shot-safety rules:
  never at or through protected assets (including their exclusion height), and shooting short of an
  obstruction wall is allowed but never through it.
- **Non-kinetic layer:** the minimum altitude at which at least one RF/non-kinetic effector is
  effective. It takes into account the effector's effective range per frequency band (R1), its
  azimuth and line of sight. Directed energy (R7) follows its own type rules.
- **Group 1 and Group 2 versions of each layer**, so there are four intercept maps in total.
- An optional **"detect-and-engage" view**: cells where a drone is detected (R13b) AND can be
  engaged, which highlights spots that are seen but cannot be engaged, and engageable spots with no
  detection.
- A point probe like R13a reports the intercept floor/ceiling and which effector(s) can engage.

### Layer management (R13 + R14)
- Each heat map is its own **toggleable analysis layer**: Detection G1, Detection G2,
  Kinetic G1, Kinetic G2, Non-kinetic G1 and Non-kinetic G2 (plus optional detect-and-engage).
  They are **all off by default** so the map stays uncluttered.
- Toggles live in one *Analysis layers* panel (and in the R9 visibility controls). Turning one on
  shows only that layer, unless the user deliberately stacks them. Opacity is adjustable per layer.
- The layers are view-only and local, like R9: nothing is sent over CoT, and hiding a layer never
  changes the analysis. They are included in PNG/PDF export only when visible.
- Calculation runs in the background with progress and cancel, using a coarse grid first and then
  refining.

## R15. KMZ / KML export  *(pending)*
Export the plan, or the visible part of it, as **KMZ** (zipped KML with icons) so it can be opened
in Google Earth, ArcGIS, QGIS and other GIS tools, and handed to people without ATAK.
- **Contents:** exclusion zones (with height as extruded polygons), engagement-authority zones,
  sensors and effectors as placemarks (with their key fields in the description balloon), sensor
  coverage and terrain-mask polygons, kinetic range rings / frag bands, and min-range blind rings.
- **Analysis layers:** RF heat maps and, once built, the R13/R14 heat maps are exported as ground
  overlays (PNG + lat/lon box) inside the KMZ.
- **Folder structure:** grouped by type and site (R0), and it follows R9 visibility. There is an option
  to include hidden assets, as with PNG export.
- **Styling:** follows plugin colours and opacity. Altitude modes: `clampToGround` for 2D,
  `relativeToGround` for zone heights.
- **Classification and author** from Settings go into the document name/description, as in PNG export.
- **Plain KML** (no images) is available as an option for tools that don't accept KMZ.
- Export only. Importing KML/KMZ back into a plan is part of the Phase 7 full-plan import/export work.

---

## R16. 3-D altitude coverage layers  *(pending)*
Visualise how sensor detection coverage and kinetic / non-kinetic engagement envelopes change with
target altitude, using an altitude slider on the ATAK map that works without the plugin panel open.

### Asset selection
Before activating the layers the user picks which sensors and effectors contribute to the slice: a
scrollable list of plan assets with per-type select-all / select-none toggles and a Show-all button,
matching the cross-type picker used elsewhere. Items not selected are excluded from the altitude
computation for that session; the underlying plan data is unchanged.

### Altitude slider
- A thin widget pinned to one edge of the ATAK map (a `View` added directly to `MapView`, not
  inside the `DropDownReceiver` panel). It persists whether the plugin panel is closed, partially
  open, or fully open.
- Activated by a dedicated toolbar button when at least one analysis layer (R13 / R14) exists for
  the selected assets.
- The widget shows: a vertical `SeekBar`, the current altitude label in metres AGL, and a small ×
  to dismiss; it takes a thin strip (~48 dp wide) along one edge without obstructing ATAK's
  toolbar, compass, or GPS bar.
- Each tick = **10 m AGL**. The user sets the ceiling in Settings (default 200 m AGL; range 50–500 m).
- As the user drags, the active analysis layers (detection heat map, intercept heat map, or both)
  redraw to show coverage at that altitude slice. All other map content (zones, placemarks, walls)
  is unchanged.

### Altitude slices — data model
- The same grid / altitude engine used by R13 / R14 is extended to pre-compute layers at each 10 m
  step up to the configured ceiling for the selected assets, and cache them.
- Computation runs in the background with a progress indicator; the slider is disabled until the
  first layer is ready. The user can cancel and change the asset selection or ceiling.
- Layers are view-only and local to the device; they are not shared via CoT.
- The engine respects all existing masking rules: exclusion-zone heights, protected-asset exclusion
  heights, terrain masks, and wall occlusion (R8c) once R8 lands.

### Dependencies
R16 requires R13 (detection heat maps) and / or R14 (intercept heat maps) — the slider has nothing
to display until at least one of those is built. Reuses the R13 / R14 grid / altitude engine,
the R2 elevation-angle fields, and the existing LOS mask; gains R8 wall occlusion when R8 lands.
The floating-widget infrastructure (View attachment, toolbar button) is independent and can be
scaffolded as part of Phase 4 / Phase 5 prep.

---

## R17. Terrain quality indicator  *(pending)*
Surface a small, unobtrusive warning when the terrain data for the current analysis appears to be
missing or internally inconsistent, so the operator knows when analysis results may be less certain.
The indicator is **off by default** and enabled in Settings.

### What it checks
- **Coverage gaps:** any analysis sweep (sensor mask, RF heat map, R16 altitude layer) that
  accumulates missing elevation samples above a configurable threshold (default 5 %) triggers the
  indicator. The `missingSamples` count in `LosSweep.Result` and `TerrainProfile.isComplete()` in
  `InterceptAltitudeCalculator` are already tracked; this feature wires them to the UI.
- **Elevation jumps:** consecutive terrain-profile samples that differ by more than a configurable
  amount (default 100 m per 30 m horizontal) are flagged as likely artefacts — noisy data or a
  mismatched tile boundary.
- **Resolution mismatch:** if the elevation query resolution is finer than the data's reported
  post spacing (available from ATAK's `ElevationManager`), the indicator notes reduced confidence.

### Indicator
- A small icon in a map corner (corner and icon size configurable; default bottom-left beneath the
  coordinate display). Traffic-light colour: green (complete and consistent), amber (minor gaps or
  one suspect jump), red (significant gaps or multiple inconsistencies).
- Tapping it opens a one-line summary: percentage of missing samples, any flagged jumps, and the
  reported resolution, with a *Details* link to the terrain-quality Settings section.

### Settings
- **Show terrain quality indicator:** on / off (default off).
- **Missing-sample warning threshold:** percentage at which the indicator turns amber (default 5 %)
  or red (default 20 %).
- **Elevation-jump threshold:** maximum plausible elevation change per sample (default 100 m per
  30 m horizontal).
- **Indicator position:** map corner (bottom-left / bottom-right / top-left / top-right).

### Dependencies
Builds on `LosSweep.Result.missingSamples` and `TerrainProfile.isComplete()` (both already
implemented). The elevation-jump check is added to `DtedHelper.getTerrainProfile`. No analysis
features depend on R17; it is informational only.

---

### Items without an R-number
These are listed under their phase in the [Phases](#phases) table: sensor gap analysis (Phase 3);
engagement timeline (detect → jam → kinetic), best-effector designation, friendly-fire
deconfliction, MSA rings, dynamic fragmentation cylinder (Phase 6); PACE plan PDF, full-plan CoT
import/export, WinTAK push/pull (Phase 7).

### Deferred (later)
Threat ingress corridors — deferred in October 2026 to prioritise the analysis layers. The data
model and the corridor input for the minimum-intercept calculator stay as they are; the corridor
editor, corridor-based gap analysis and per-corridor timelines wait until after the items above.

### Dependencies
R0 (site grouping) is independent of all others and ships first.
R2 (designators) → R4 (LGR) → R5 (masking).
R3 (shapes) → R6 (R6 is shipped for polygons; new R3 shapes become bases when added).
R1 is independent.
R7 depends only on the Directed Energy type added in 1.0.5.
R8 (walls) is independent for measurement and drawing. Its masking rule (R8c) shares the clearance
engine with exclusion-zone heights and feeds the engagement timeline and R5 masking analysis.
R9 (visibility) is independent and works with today's asset types. The per-site toggle needs R0.
R10 (preset catalog) builds on R2's min-range / elevation fields. R11 (optics / DRI rings) needs R2
and the existing terrain/building line-of-sight mask.
R12 (detection simulation) uses R2 (min range, elevation, day/night), R10 (per-class preset ranges)
and the line-of-sight mask; it gains DRI output once R11 lands and could later feed sensor gap
analysis (Phase 3).
R13 (detection-floor probe / heat maps) is built on R12's per-sensor check. R14 (intercept heat maps)
reuses R13's grid/altitude engine, plus the existing kinetic shot-safety/clearance engine, R1 (RF
bands), R7 (directed energy) and R8c (walls). Both feed the Phase 3 sensor gap analysis.
R8 (walls) arrives in Phase 4. Until then, R14 respects exclusion-zone heights, and walls are added to
the same clearance check when R8 lands.
R15 (KMZ / KML export) is independent. It reuses the overlay geometry and R9 visibility, and adds
R13/R14 heat maps as ground overlays once they exist.
R16 (3-D altitude coverage layers) requires R13 and/or R14 for its data; reuses the R13/R14
grid/altitude engine, R2 elevation-angle fields and the LOS mask; gains R8 wall occlusion when
R8 lands. The floating-widget infrastructure (MapView overlay, toolbar button) can be scaffolded
earlier.
R17 (terrain quality indicator) is informational only; it builds on the existing `missingSamples`
count and `isComplete()` flag and adds a jump-check to `DtedHelper`. No other feature depends on it.
