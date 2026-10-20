package com.btlrckt.bottlerocket.overlay;

import com.atakmap.android.maps.MapGroup;
import com.btlrckt.bottlerocket.analysis.PolygonBuffer;
import com.btlrckt.bottlerocket.model.EngagementAuthorityZone;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.model.PointDefensePlan;
import com.btlrckt.bottlerocket.settings.SettingsManager;
import com.btlrckt.bottlerocket.settings.UnitSystem;
import com.btlrckt.bottlerocket.util.GeoUtils;
import com.btlrckt.bottlerocket.util.UnitConverter;

import java.util.List;

/**
 * Map items for engagement-authority perimeters (R6): the buffered ring (fill + stroke in the
 * zone's colors), a faint outline of a drawn base boundary when buffered, and a text callout
 * such as "North fence\nKINETIC AUTH +500 m".
 */
public class AuthorityZoneOverlay {
    static final String RING_SUFFIX = ".ring";
    static final String BASE_SUFFIX = ".base";
    static final String LABEL_SUFFIX = ".label";

    private final SettingsManager settings;

    public AuthorityZoneOverlay(SettingsManager settings) {
        this.settings = settings;
    }

    public void render(MapGroup group, EngagementAuthorityZone zone, PointDefensePlan plan) {
        if (group == null || zone == null) return;
        zone.normalize();
        List<GeoPos> base = zone.resolveBase(plan);
        List<GeoPos> ring = PolygonBuffer.offsetRing(base, zone.bufferMeters);
        int stroke = zone.color | 0xFF000000;
        if (ring.size() >= 3) {
            group.addItem(OverlayItems.polygon(zone.uid + RING_SUFFIX, zone.uid, ring, zone.getFillArgb(), stroke,
                    3d, zone.strokeDashed));
        }
        if (zone.baseUid == null && Math.abs(zone.bufferMeters) > 0.01 && base != null && base.size() >= 3) {
            group.addItem(OverlayItems.polygon(zone.uid + BASE_SUFFIX, zone.uid, base, 0,
                    OverlayItems.withAlpha(stroke, 0.6f), 1.5d, true));
        }
        GeoPos at = GeoUtils.centroid(ring.size() >= 3 ? ring : base);
        if (at != null) {
            String text = calloutText(zone, settings.getUnitSystem(), settings.getPrecision());
            if (ring.size() < 3) text += "\n(inward buffer collapses base)";
            group.addItem(OverlayItems.label(zone.uid + LABEL_SUFFIX, zone.uid, at, text, stroke, false));
        }
    }

    /** "Label\nKINETIC AUTH +500 m" (buffer omitted when 0). */
    public static String calloutText(EngagementAuthorityZone z, UnitSystem unit, int precision) {
        StringBuilder sb = new StringBuilder();
        if (z.label != null && !z.label.trim().isEmpty()) sb.append(z.label.trim()).append('\n');
        sb.append(z.authorityType.shortTag);
        if (Math.abs(z.bufferMeters) > 0.01) {
            sb.append(' ').append(z.bufferMeters > 0 ? "+" : "\u2212")
                    .append(UnitConverter.format(Math.abs(z.bufferMeters), unit, precision));
        }
        return sb.toString();
    }
}
