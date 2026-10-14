package com.skiarkt.shooter.overlay;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.Polyline;
import com.skiarkt.shooter.analysis.CoverageGeometry;
import com.skiarkt.shooter.analysis.LosSweep;
import com.skiarkt.shooter.model.SensorItem;
import com.skiarkt.shooter.settings.SettingsManager;

import java.util.List;
import java.util.Locale;

/**
 * Sensor coverage rendering: filled circle (omni) or sector (azimuth +/- beam/2) of radius =
 * sensor range in the sensor color at its opacity, terrain-masked regions from the DTED
 * {@link LosSweep} as a darker (semi-opaque black) overlay, a dashed ring at the max
 * engagement distance when it differs from the range, and a centre marker with label.
 */
public class SensorOverlay {
    public static final String COVERAGE_SUFFIX = ".coverage";
    public static final String MASK_SUFFIX = ".mask.";
    public static final String ENGAGE_SUFFIX = ".engage";
    public static final String CENTER_SUFFIX = ".center";
    /** Darkening applied over terrain-masked areas. */
    public static final int MASK_COLOR = 0x99000000;
    /** Cap on masked wedge shapes per sensor (beyond it the remainder is dropped and noted). */
    public static final int MAX_MASK_WEDGES = 1500;

    private final SettingsManager settings;

    public SensorOverlay(SettingsManager settings) {
        this.settings = settings;
    }

    /** @param mask terrain mask (null while computing / unavailable). */
    public void render(MapGroup group, SensorItem s, LosSweep.Result mask, boolean computing) {
        if (group == null || s == null || !s.isValid()) return;
        int color = s.color | 0xFF000000;
        Polyline cov = OverlayItems.polygon(s.uid + COVERAGE_SUFFIX, s.uid,
                CoverageGeometry.coverage(s.position, s.rangeMeters, s.azimuthDeg, s.beamWidthDeg,
                        CoverageGeometry.DEFAULT_ARC_STEP_DEG),
                OverlayItems.withAlpha(color, s.opacity), color, 2d, false);
        cov.setTitle(s.label);
        group.addItem(cov);

        if (mask != null && mask.terrainAvailable) {
            List<LosSweep.Wedge> wedges = mask.maskedWedges();
            int n = Math.min(MAX_MASK_WEDGES, wedges.size());
            for (int i = 0; i < n; i++) {
                LosSweep.Wedge w = wedges.get(i);
                Polyline p = OverlayItems.polygon(s.uid + MASK_SUFFIX + i, s.uid,
                        CoverageGeometry.annularWedge(s.position, w.innerM, w.outerM, w.startDeg, w.sweepDeg,
                                Math.max(0.5, Math.min(CoverageGeometry.DEFAULT_ARC_STEP_DEG, w.sweepDeg / 2))),
                        MASK_COLOR, 0, 0, false);
                group.addItem(p);
            }
        }

        if (Math.abs(s.maxEngagementDistMeters - s.rangeMeters) > 0.5) {
            Polyline ring = OverlayItems.polygon(s.uid + ENGAGE_SUFFIX, s.uid,
                    CoverageGeometry.coverage(s.position, s.maxEngagementDistMeters, s.azimuthDeg, s.beamWidthDeg,
                            CoverageGeometry.DEFAULT_ARC_STEP_DEG),
                    0, color, 2d, true);
            group.addItem(ring);
        }

        group.addItem(OverlayItems.label(s.uid + CENTER_SUFFIX, s.uid, s.position,
                labelText(s, mask, computing), color, true));
    }

    String labelText(SensorItem s, LosSweep.Result mask, boolean computing) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.label == null ? "" : s.label.trim()).append('\n')
                .append(s.type.displayName).append(' ').append(settings.formatDistance(s.rangeMeters));
        if (!s.isOmnidirectional()) {
            sb.append(String.format(Locale.US, " %.0f\u00B0/%.0f\u00B0", s.azimuthDeg, s.beamWidthDeg));
        }
        if (mask != null && mask.terrainAvailable) {
            sb.append(String.format(Locale.US, "\nLOS %.0f%%", mask.visibleFraction() * 100.0));
        } else if (computing) {
            sb.append("\nLOS computing\u2026");
        } else if (mask != null) {
            sb.append("\nNo DTED");
        }
        return sb.toString().trim();
    }
}
