# SKY ROCKET SHOOTER (SKIARKT) — Roadmap

Released: **1.0.2** (ATAK-CIV 5.6.0) — exclusion zones (polygon), sensors, RF jamming heat map,
kinetic effectors, CoT sharing.

Items below are planned, not implemented. Everything new is saved and shared as CoT, like the
existing objects. All distances follow the global unit setting (ft / m / km).

---

## R1. Multi-frequency non-kinetic (RF) effectors
- A jammer has a **list of bands**, not one frequency (e.g. 433 MHz, 915 MHz, 2.4 GHz,
  5.8 GHz, GNSS L1/L2). Each band has its own ERP, jamming threshold, and max range.
- Heat map per band (toggle each) or combined (union / weakest band).
- Drone-class presets list which bands a class uses, so the map shows where that class is
  actually denied.
- Builds on the existing terrain-aware RF model. Most of the work is the data model, UI, and CoT schema.

## R2. Sensor types
- Types: **RADAR**, **Optical (EO camera)**, **IR / thermal**, **Laser designator**.
- Each type has its own icon and type-specific fields. Common fields: range, beam width / FOV, AGL,
  azimuth sector. Type-specific examples: radar min detectable RCS; EO/IR day/night capability;
  designator max lasing range, laser code, beam divergence.
- Gap analysis and MSA rings can filter by sensor type (e.g. "IR-only coverage at night").
- Laser designators are prerequisites for R4/R5.

## R3. Exclusion-zone shapes
- **Circle** (center + radius), **rectangle / square** (two corners, or center + size + rotation),
  and **polygon** (existing).
- All shapes keep height AGL, colors, and opacity, and work in the intercept-altitude calculator
  and the engagement heat map.

## R4. Laser-guided rocket (LGR) kinetic effector
- New effector type: **laser-guided rocket** launcher, with AGL, min/max range,
  fragmentation radius, seeker field of view / acquisition range, and laser code.
- **Must be paired with one or more laser designators** (R2). The effector is shown as
  *not mission-capable* (and excluded from best-effector recommendations) when no paired
  designator is placed, in range, or code-matched.
- Fragmentation along the munition route reuses the existing kinetic model.

## R5. Laser masking analysis (designator not co-located with launcher)
For each candidate target point / ingress corridor, the analysis checks:
1. **Designator → target LOS** (DTED terrain; later, obstacles and exclusion-zone roofs).
2. **Seeker → laser spot LOS** along the rocket's flight path from the launcher.
3. **Geometry constraints**: the angle between the designator line and the rocket's approach must fall
   inside a user-set *attack cone*. The rocket must also stay out of a user-set *designator
   safety fan* around the designator line. Angles are user-configurable, not hard-coded.
4. Max lasing range and seeker acquisition range.

Output: an overlay of where a target can be **lased and engaged**, where it is **lased but
masked from the seeker**, and where it **can't be lased at all**. It also lists the best
designator position(s) for each launcher. Results feed into the engagement timeline.

## R6. Engagement-authority perimeters (buffers)
Buffer zones offset from a base perimeter (a defended asset, an exclusion zone, or a drawn boundary).
Each zone has its own distance, color, and label:
- **Non-kinetic engagement authority**: area where RF/NK effectors are authorized.
- **Kinetic engagement authority**: area where kinetic effectors (including LGRs) are
  authorized.
- **Non-hostile authority**: area where tracks are treated as non-hostile and no
  engagement is authorized (weapons hold).

Rules:
- Buffers are distances outward (or inward) from the base shape. Any R3 shape can be a base.
- Engagement timeline, deconfliction, and best-effector logic only recommend an effector
  when the threat position is inside that effector class's authority zone and outside
  non-hostile zones. Violations are flagged.
- Shown on the map and in the PACE PDF.

---

### Previously planned (unchanged)
Threat ingress corridors, sensor gap analysis, engagement timeline (detect → jam → kinetic),
MSA rings, friendly-fire deconfliction / best-effector designation, PACE plan PDF.

### Dependencies
R2 (designators) → R4 (LGR) → R5 (masking). R3 (shapes) → R6 (buffers can use any shape).
R1 is independent.
