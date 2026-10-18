package com.skiarkt.shooter.overlay;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.Polyline;
import com.skiarkt.shooter.analysis.CoverageEngine;
import com.skiarkt.shooter.analysis.CoverageGeometry;
import com.skiarkt.shooter.analysis.RfHeatmap;
import com.skiarkt.shooter.analysis.RfPropagation;
import com.skiarkt.shooter.data.VisibilityStore;
import com.skiarkt.shooter.data.VisibilityStore.Layer;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.NonKineticEffector;
import com.skiarkt.shooter.model.PlanColors;
import com.skiarkt.shooter.settings.SettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RF jammer heat map rendering. The {@link RfHeatmap.Raster} (20 m ARGB-equivalent cell grid,
 * quantized into effective / 10 marginal gradient steps / insufficient bands) is projected
 * onto the map as filled, stroke-less rectangles in geographic cell coordinates; equal
 * neighbouring cells are merged ({@link RfHeatmap#mergeRects}) so a typical 1 km map is a few
 * hundred shapes. Opacity = global heat map opacity x per-effector opacity.
 *
 * <p>Design note: a GLMapItem2 texture renderer would draw the raster as one bitmap, but
 * custom GL renderers cannot be compiled or exercised against this project's SDK stubs, so
 * the shape-based projection (standard {@code Polyline} API only) is used for robustness.
 */
public class RfHeatmapOverlay {
    public static final String CELL_SUFFIX = ".heat.";
    public static final String RING_SUFFIX = ".ring";
    public static final String CENTER_SUFFIX = ".center";
    /** Max rectangles per effector; the raster is down-sampled until under this. */
    public static final int MAX_RECTS = 2500;

    private final SettingsManager settings;

    public RfHeatmapOverlay(SettingsManager settings) {
        this.settings = settings;
    }

    public void render(MapGroup group, NonKineticEffector n, CoverageEngine.HeatmapResult heat, boolean computing) {
        render(group, n, heat, computing, null);
    }

    /** @param vis R9b per-layer visibility (null = draw every layer). */
    public void render(MapGroup group, NonKineticEffector n, CoverageEngine.HeatmapResult heat, boolean computing,
            VisibilityStore vis) {
        if (group == null || n == null || !n.isValid()) return;
        normalizeColors(n);
        float alpha = settings.getHeatmapOpacity() * n.getOpacity();

        if (heat != null && heat.raster != null && alpha > 0.004f && shown(vis, n, Layer.HEATMAP)) {
            int[] palette = RfPropagation.palette(RfHeatmap.GRADIENT_STEPS, n.heatmapColor, n.marginalColor,
                    n.insufficientColor);
            RfHeatmap.Raster r = heat.raster;
            List<RfHeatmap.Rect> rects = RfHeatmap.mergeRects(r.bands, r.size, r.size);
            for (int f = 2; rects.size() > MAX_RECTS && f <= 16; f++) {
                RfHeatmap.Raster d = heat.raster.downsample(f);
                rects = RfHeatmap.mergeRects(d.bands, d.size, d.size);
                r = d;
            }
            int i = 0;
            for (RfHeatmap.Rect rc : rects) {
                List<GeoPos> pts = new ArrayList<>(4);
                double n0 = r.latAt(rc.row0), s0 = r.latAt(rc.row1);
                double w0 = r.lonAt(rc.col0), e0 = r.lonAt(rc.col1);
                pts.add(new GeoPos(n0, w0));
                pts.add(new GeoPos(n0, e0));
                pts.add(new GeoPos(s0, e0));
                pts.add(new GeoPos(s0, w0));
                Polyline p = OverlayItems.polygon(n.uid + CELL_SUFFIX + (i++), n.uid, pts,
                        OverlayItems.withAlpha(palette[rc.band], alpha), 0, 0, false);
                group.addItem(p);
            }
        }

        if (shown(vis, n, Layer.RANGE_RING)) {
            Polyline ring = OverlayItems.polygon(n.uid + RING_SUFFIX, n.uid,
                    CoverageGeometry.circle(n.position, n.maxEngagementDistMeters, CoverageGeometry.DEFAULT_ARC_STEP_DEG),
                    0, n.color | 0xFF000000, 2d, true);
            ring.setTitle(n.label);
            group.addItem(ring);
        }

        if (shown(vis, n, Layer.MARKER)) {
            group.addItem(OverlayItems.label(n.uid + CENTER_SUFFIX, n.uid, n.position,
                    labelText(n, heat, computing), n.color, true));
        }
    }

    private static boolean shown(VisibilityStore vis, NonKineticEffector n, String layer) {
        return vis == null || !vis.isLayerHidden(n.uid, layer);
    }

    String labelText(NonKineticEffector n, CoverageEngine.HeatmapResult heat, boolean computing) {
        StringBuilder sb = new StringBuilder();
        sb.append(n.label == null ? "" : n.label.trim()).append('\n')
                .append(String.format(Locale.US, "%.1f dBm, thr %.1f dBm", n.eirpDbm,
                        n.effectiveThresholdDbm(settings.getRfThresholdDbm())));
        if (heat != null && heat.raster != null) {
            sb.append(String.format(Locale.US, "\nEffective %.0f%%", heat.raster.effectiveFraction * 100.0));
            if (heat.los != null && !heat.los.terrainAvailable) sb.append(" (no DTED)");
        } else if (computing) {
            sb.append("\nHeat map computing\u2026");
        }
        return sb.toString().trim();
    }

    /** Gson-loaded legacy items may have 0 (transparent black) colors; restore defaults. */
    static void normalizeColors(NonKineticEffector n) {
        if (n.heatmapColor == 0) n.heatmapColor = PlanColors.RF_EFFECTIVE;
        if (n.marginalColor == 0) n.marginalColor = PlanColors.RF_MARGINAL;
        if (n.insufficientColor == 0) n.insufficientColor = PlanColors.RF_INSUFFICIENT;
    }
}
