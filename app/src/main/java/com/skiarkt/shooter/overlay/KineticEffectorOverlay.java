package com.skiarkt.shooter.overlay;

import android.util.Log;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.Polyline;
import com.skiarkt.shooter.analysis.CoverageGeometry;
import com.skiarkt.shooter.analysis.LaserPerformance;
import com.skiarkt.shooter.data.VisibilityStore;
import com.skiarkt.shooter.data.VisibilityStore.Layer;
import com.skiarkt.shooter.model.AzimuthArc;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.KineticEffector;
import com.skiarkt.shooter.settings.SettingsManager;

import java.util.List;
import java.util.Locale;

/**
 * Kinetic effector rendering:
 * <ul>
 *   <li>three concentric heat bands (red = min-range dead zone, yellow = min + fragmentation
 *   radius, green = out to max range), clipped to the azimuth sector when limits are set;</li>
 *   <li>max engagement ring (green) and min ring (red dead zone);</li>
 *   <li>sector outline when azimuth limits are set;</li>
 *   <li>restricted (no-fire) azimuth wedges in solid red;</li>
 *   <li>centre marker with name / type / range / status.</li>
 * </ul>
 * R9b: every sub-layer can be hidden per asset ({@link VisibilityStore.Layer}).
 */
public class KineticEffectorOverlay {
    private static final String TAG = "SkiarktKineticOverlay";
    public static final String BAND_RED_SUFFIX = ".band.red";
    public static final String BAND_YELLOW_SUFFIX = ".band.yellow";
    public static final String BAND_GREEN_SUFFIX = ".band.green";
    public static final String MAX_RING_SUFFIX = ".ring.max";
    public static final String MIN_RING_SUFFIX = ".ring.min";
    public static final String SECTOR_SUFFIX = ".sector";
    public static final String RESTRICTED_SUFFIX = ".restricted.";
    public static final String CENTER_SUFFIX = ".center";
    /** R7 directed energy: beam-intensity gradient bands, effective-range ring, suppression zone. */
    public static final String BEAM_BAND_SUFFIX = ".beam.";
    public static final String EFFECTIVE_RING_SUFFIX = ".ring.effective";
    public static final String SUPPRESSION_SUFFIX = ".suppression";
    /** Number of gradient bands from min range to max range. */
    static final int BEAM_BANDS = 8;
    static final float BEAM_MAX_ALPHA = 0.45f;

    static final float BAND_ALPHA = 0.22f;
    static final float RESTRICTED_ALPHA = 0.75f;

    private final SettingsManager settings;
    /** Layer visibility for the render in progress (map thread only). */
    private VisibilityStore vis;

    public KineticEffectorOverlay(SettingsManager settings) {
        this.settings = settings;
    }

    public void render(MapGroup group, KineticEffector k) {
        render(group, k, null);
    }

    /** @param vis per-layer visibility (null = draw every layer). */
    public void render(MapGroup group, KineticEffector k, VisibilityStore vis) {
        this.vis = vis;
        if (group == null || k == null || k.position == null || !k.position.isValid()) return;
        k.ensureLists();
        GeoPos pos = k.position;
        double max = Math.max(1.0, k.maxEngagementDistMeters);
        double min = Math.max(0.0, Math.min(k.minEngagementRange, max));
        double frag = Math.min(max, min + Math.max(0.0, k.fragmentationRadiusMeters));
        boolean full = k.isFullCircle();
        double start = full ? 0.0 : AzimuthArc.normalize(k.azimuthMin);
        double sweep = k.azimuthSweepDeg();
        double step = CoverageGeometry.DEFAULT_ARC_STEP_DEG;
        int red = k.noShootColor | 0xFF000000;
        int yellow = k.fragZoneColor | 0xFF000000;
        int green = k.clearZoneColor | 0xFF000000;

        if (k.isDirectedEnergy()) {
            renderDirectedEnergy(group, k, pos, max, min, full, start, sweep, step, red, yellow, green);
            return;
        }
        try {
            // Heat bands (outer first so inner bands draw on top).
            boolean bands = shown(k, Layer.FRAG_BANDS);
            if (bands && max > frag + 0.5) {
                group.addItem(OverlayItems.polygon(k.uid + BAND_GREEN_SUFFIX, k.uid,
                        band(pos, frag, max, full, start, sweep, step),
                        OverlayItems.withAlpha(green, BAND_ALPHA), 0, 0, false));
            }
            if (bands && frag > min + 0.5) {
                group.addItem(OverlayItems.polygon(k.uid + BAND_YELLOW_SUFFIX, k.uid,
                        band(pos, min, frag, full, start, sweep, step),
                        OverlayItems.withAlpha(yellow, BAND_ALPHA + 0.08f), 0, 0, false));
            }
            if (bands && min > 0.5) {
                group.addItem(OverlayItems.polygon(k.uid + BAND_RED_SUFFIX, k.uid,
                        CoverageGeometry.circle(pos, min, step),
                        OverlayItems.withAlpha(red, BAND_ALPHA + 0.13f), 0, 0, false));
            }

            // Rings.
            if (shown(k, Layer.MAX_RING)) {
                Polyline maxRing = OverlayItems.polygon(k.uid + MAX_RING_SUFFIX, k.uid,
                        CoverageGeometry.circle(pos, max, step), 0, OverlayItems.withAlpha(green, 0.85f), 2d, false);
                maxRing.setTitle(k.label);
                group.addItem(maxRing);
            }
            if (min > 0.5 && shown(k, Layer.MIN_RING)) {
                group.addItem(OverlayItems.polygon(k.uid + MIN_RING_SUFFIX, k.uid,
                        CoverageGeometry.circle(pos, min, step), 0, red, 2d, false));
            }

            // Sector outline.
            if (!full && sweep < 360.0 && shown(k, Layer.SECTOR)) {
                group.addItem(OverlayItems.polygon(k.uid + SECTOR_SUFFIX, k.uid,
                        CoverageGeometry.annularWedge(pos, 0, max, start, sweep, step),
                        0, 0xFFFFFFFF, 2d, true));
            }

            // Restricted (no-fire) azimuth wedges.
            for (int i = 0; shown(k, Layer.RESTRICTED) && i < k.restrictedAzimuths.size(); i++) {
                AzimuthArc a = k.restrictedAzimuths.get(i);
                if (a == null || !a.isValid()) continue;
                double s = a.sweepDeg();
                group.addItem(OverlayItems.polygon(k.uid + RESTRICTED_SUFFIX + i, k.uid,
                        CoverageGeometry.annularWedge(pos, 0, max, AzimuthArc.normalize(a.bearingMin), s,
                                Math.max(0.5, Math.min(step, s / 2))),
                        OverlayItems.withAlpha(red, RESTRICTED_ALPHA), red, 1.5d, false));
            }

            if (shown(k, Layer.MARKER)) {
                group.addItem(OverlayItems.label(k.uid + CENTER_SUFFIX, k.uid, pos, labelText(k), green, true));
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to render kinetic effector " + k.uid, e);
        }
    }

    /**
     * Directed energy (R7): no frag rings. Instead a beam-intensity gradient along the
     * engagement (threat) vector from min range outward: each band's opacity follows the relative
     * fluence ({@link LaserPerformance#relativeIntensity}), so the shading fades where the beam can
     * no longer defeat the target within the dwell time. The effective-range ring is solid; the
     * suppression zone (replaces the frag corridor) is shown as a dashed ring of that radius
     * around the effective-range point on every bearing, i.e. an outer dashed ring at
     * effective + suppression radius.
     */
    private void renderDirectedEnergy(MapGroup group, KineticEffector k, GeoPos pos, double max, double min,
            boolean full, double start, double sweep, double step, int red, int yellow, int green) {
        try {
            double eff = Math.max(0.0, Math.min(max, LaserPerformance.effectiveRangeM(k)));
            double span = Math.max(1.0, max - min);
            for (int i = BEAM_BANDS - 1; shown(k, Layer.FRAG_BANDS) && i >= 0; i--) {
                double r0 = min + span * i / BEAM_BANDS;
                double r1 = min + span * (i + 1) / BEAM_BANDS;
                if (r1 - r0 < 0.5) continue;
                double rel = LaserPerformance.relativeIntensity(k, 0.5 * (r0 + r1));
                if (rel <= 0.01) continue;
                int color = 0.5 * (r0 + r1) <= eff ? green : yellow;
                group.addItem(OverlayItems.polygon(k.uid + BEAM_BAND_SUFFIX + i, k.uid,
                        band(pos, r0, r1, full, start, sweep, step),
                        OverlayItems.withAlpha(color, (float) (BEAM_MAX_ALPHA * rel)), 0, 0, false));
            }
            if (min > 0.5 && shown(k, Layer.FRAG_BANDS)) {
                group.addItem(OverlayItems.polygon(k.uid + BAND_RED_SUFFIX, k.uid,
                        CoverageGeometry.circle(pos, min, step),
                        OverlayItems.withAlpha(red, BAND_ALPHA + 0.13f), 0, 0, false));
            }
            if (min > 0.5 && shown(k, Layer.MIN_RING)) {
                group.addItem(OverlayItems.polygon(k.uid + MIN_RING_SUFFIX, k.uid,
                        CoverageGeometry.circle(pos, min, step), 0, red, 2d, false));
            }
            if (shown(k, Layer.MAX_RING)) {
                Polyline maxRing = OverlayItems.polygon(k.uid + MAX_RING_SUFFIX, k.uid,
                        CoverageGeometry.circle(pos, max, step), 0, OverlayItems.withAlpha(green, 0.5f), 1.5d, true);
                maxRing.setTitle(k.label);
                group.addItem(maxRing);
            }
            if (eff > 0.5 && shown(k, Layer.EFFECTIVE_RING)) {
                group.addItem(OverlayItems.polygon(k.uid + EFFECTIVE_RING_SUFFIX, k.uid,
                        band(pos, 0, eff, full, start, sweep, step), 0, OverlayItems.withAlpha(green, 0.95f),
                        2.5d, false));
                double sup = Math.max(0.0, k.suppressionZoneRadiusM);
                if (sup > 0.5) {
                    group.addItem(OverlayItems.polygon(k.uid + SUPPRESSION_SUFFIX, k.uid,
                            band(pos, 0, eff + sup, full, start, sweep, step), 0,
                            OverlayItems.withAlpha(yellow, 0.9f), 1.5d, true));
                }
            }
            if (!full && sweep < 360.0 && shown(k, Layer.SECTOR)) {
                group.addItem(OverlayItems.polygon(k.uid + SECTOR_SUFFIX, k.uid,
                        CoverageGeometry.annularWedge(pos, 0, max, start, sweep, step), 0, 0xFFFFFFFF, 2d, true));
            }
            for (int i = 0; shown(k, Layer.RESTRICTED) && i < k.restrictedAzimuths.size(); i++) {
                AzimuthArc a = k.restrictedAzimuths.get(i);
                if (a == null || !a.isValid()) continue;
                double s = a.sweepDeg();
                group.addItem(OverlayItems.polygon(k.uid + RESTRICTED_SUFFIX + i, k.uid,
                        CoverageGeometry.annularWedge(pos, 0, max, AzimuthArc.normalize(a.bearingMin), s,
                                Math.max(0.5, Math.min(step, s / 2))),
                        OverlayItems.withAlpha(red, RESTRICTED_ALPHA), red, 1.5d, false));
            }
            if (shown(k, Layer.MARKER)) {
                group.addItem(OverlayItems.label(k.uid + CENTER_SUFFIX, k.uid, pos, labelText(k), green, true));
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to render directed-energy effector " + k.uid, e);
        }
    }

    private boolean shown(KineticEffector k, String layer) {
        return vis == null || !vis.isLayerHidden(k.uid, layer);
    }

    private static List<GeoPos> band(GeoPos pos, double r0, double r1, boolean full, double start,
            double sweep, double step) {
        return CoverageGeometry.annularWedge(pos, r0, r1, full ? 0.0 : start, full ? 360.0 : sweep, step);
    }

    String labelText(KineticEffector k) {
        StringBuilder sb = new StringBuilder();
        sb.append(k.label == null ? "" : k.label.trim()).append('\n')
                .append(k.type.displayName).append(' ')
                .append(settings.formatDistance(k.minEngagementRange)).append('-')
                .append(settings.formatDistance(k.maxEngagementDistMeters));
        if (!k.isFullCircle()) {
            sb.append(String.format(Locale.US, " %.0f\u00B0-%.0f\u00B0", k.azimuthMin, k.azimuthMax));
        }
        if (k.isDirectedEnergy()) {
            sb.append('\n').append(k.status.displayName).append(" eff ")
                    .append(settings.formatDistance(LaserPerformance.effectiveRangeM(k)));
            if (LaserPerformance.source(k) == LaserPerformance.Source.COMPUTED) {
                sb.append(String.format(Locale.US, " %.0f kW", k.laserPowerKw));
            } else if (LaserPerformance.source(k) == LaserPerformance.Source.QUALITATIVE) {
                sb.append(" (est.)");
            }
            return sb.toString().trim();
        }
        sb.append('\n').append(k.status.displayName).append(String.format(Locale.US, " x%d", k.ammoCount));
        if (k.laserCode != null) sb.append(" PRF ").append(k.laserCode);
        return sb.toString().trim();
    }
}
