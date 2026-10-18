# SKY ROCKET SHOOTER (SKIARKT) — Roadmap

Current build: **1.0.8** (ATAK-CIV 5.6.0, debug pre-release). Released: **1.0.6** — exclusion zones (polygon), sensors, RF jamming heat map,
kinetic effectors (including Directed Energy / laser at speed-of-light default), engagement-authority
perimeters (R6), CoT sharing, autosave persistence.

Items below are planned, not implemented, unless marked **Implemented**. Everything new is saved and shared as CoT, like the
existing objects. All distances follow the global unit setting (ft / m / km).

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
  also stored as a snapshot so a zone shared over CoT (`b-m-p-skiarkt-authzone`) still works on a
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
  (complements the Phase 5 dynamic-frag-cylinder item for projectiles).

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
- Shared as CoT (type `b-m-p-skiarkt-wall`) and saved with the plan like every other object.

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
in the main menu. `VisibilityStore` saves to `visibility.skiarkt.json` beside the autosave; never
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
- Saved in `sensor_presets.skiarkt.json` beside the autosave: overrides keyed by built-in id plus
  the custom list. Built-ins are never deleted, only reset.
- The sensor panel's preset picker shows built-ins (with overrides applied) followed by customs.

## R11. Optics/IR definition and PID rings  *(pending)*
Optics/IR definition and PID rings: per-sensor resolution, zoom and HFOV for day and IR/thermal
channels; Detect/Recognise/Identify (DRI) range rings for Group 1 and Group 2 UAS that clip to the
terrain/building mask so blind spots are preserved.
- Builds on R2 (EO / IR_THERMAL types and the day/night field).

---

### Phase 5 core items (previously planned)
Sensor gap analysis, engagement timeline (detect → jam → kinetic),
MSA rings, friendly-fire deconfliction / best-effector designation, dynamic fragmentation
cylinder along threat vector, PACE plan PDF, full-plan CoT import/export, WinTAK push/pull.

### Deferred (later)
Threat ingress corridors — pushed down the road at the user's request (October 2026). The data
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
