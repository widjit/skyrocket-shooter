package com.btlrckt.bottlerocket.overlay;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.btlrckt.bottlerocket.analysis.InterceptAltitudeResult;
import com.btlrckt.bottlerocket.model.ExclusionZone;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.settings.SettingsManager;
import com.btlrckt.bottlerocket.settings.UnitSystem;
import com.btlrckt.bottlerocket.util.UnitConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the ATAK map items for exclusion zones and intercept-altitude annotations.
 *
 * <p>Per zone: a closed, filled, semi-transparent {@link Polyline} (dashed or solid perimeter
 * in the zone's stroke color / width, extruded via {@link MapItem#setHeight} so 3D views show
 * the volume) plus a text-only {@link Marker} at the centroid carrying the label and a
 * height callout such as "45.0 m AGL" (unit + precision from {@link SettingsManager}).
 *
 * <p>Items are tagged {@code nevercot} so ATAK never re-broadcasts them as native shapes; the
 * plugin's own CoT ({@code b-m-p-btlrckt-exczone}) is the sharing path.
 */
public class ExclusionZoneOverlay {
    public static final String SHAPE_SUFFIX = ".shape";
    public static final String LABEL_SUFFIX = ".label";
    public static final String META_ZONE_UID = "btlrckt_zone_uid";
    static final String INTERCEPT_PREFIX = "btlrckt-intercept-";

    /** Intercept annotation colors. */
    static final int PATH_COLOR = 0xFFFFFFFF;
    static final int CONSTRAINED_COLOR = 0xFFFF1744;
    static final int CRITICAL_TEXT_COLOR = 0xFFFFEB3B;

    private final SettingsManager settings;

    public ExclusionZoneOverlay(SettingsManager settings) {
        this.settings = settings;
    }

    /** Adds the polygon + centroid label for {@code zone} to {@code group}. Invalid zones are skipped. */
    public void render(MapGroup group, ExclusionZone zone) {
        if (group == null || zone == null || !zone.isValid()) return;
        zone.normalizeStyle();

        Polyline poly = new Polyline(zone.uid + SHAPE_SUFFIX);
        poly.setType("u-d-f");
        poly.setTitle(zone.label);
        poly.setPoints(toMetaPoints(zone.points));
        poly.setStyle(Shape.STYLE_STROKE_MASK | Shape.STYLE_FILLED_MASK | Polyline.STYLE_CLOSED_MASK);
        poly.setStrokeColor(opaque(zone.getStrokeColor()));
        poly.setStrokeWeight(zone.strokeWidth);
        poly.setFillColor(zone.getFillArgb());
        poly.setBasicLineStyle(zone.strokeDashed ? Polyline.BASIC_LINE_STYLE_DASHED
                : Polyline.BASIC_LINE_STYLE_SOLID);
        poly.setHeight(zone.heightAglMeters);
        tag(poly, zone.uid);
        group.addItem(poly);

        GeoPos c = zone.getAnchor();
        if (c != null) {
            Marker label = new Marker(c.toGeoPoint(), zone.uid + LABEL_SUFFIX);
            label.setType("b-m-p-s-p-i");
            label.setTitle(calloutText(zone.label, zone.heightAglMeters,
                    settings.getUnitSystem(), settings.getPrecision()));
            label.setIconVisibility(Marker.ICON_INVISIBLE);
            label.setTextColor(opaque(zone.getStrokeColor()));
            label.setClickable(false);
            tag(label, zone.uid);
            group.addItem(label);
        }
    }

    /**
     * Draws an intercept-altitude result: the analysed path (thin white dashed), every
     * constrained segment (thick red) and a callout at the critical sample.
     */
    public void renderIntercept(MapGroup group, InterceptAltitudeResult result) {
        if (group == null || result == null || result.getPath().size() < 2) return;
        UnitSystem unit = settings.getUnitSystem();
        int precision = settings.getPrecision();

        Polyline path = new Polyline(INTERCEPT_PREFIX + "path");
        path.setPoints(toMetaPoints(result.getPath()));
        path.setStyle(Shape.STYLE_STROKE_MASK);
        path.setStrokeColor(PATH_COLOR);
        path.setStrokeWeight(2d);
        path.setBasicLineStyle(Polyline.BASIC_LINE_STYLE_DASHED);
        path.setTitle(result.getSourceLabel() == null ? "Ingress line" : result.getSourceLabel());
        tag(path, null);
        group.addItem(path);

        int n = 0;
        for (InterceptAltitudeResult.ConstraintSegment seg : result.getSegments()) {
            if (!seg.isConstrained() || seg.start == null || seg.end == null) continue;
            Polyline p = new Polyline(INTERCEPT_PREFIX + "seg-" + (n++));
            List<GeoPos> pts = new ArrayList<>();
            pts.add(seg.start);
            pts.add(seg.end);
            p.setPoints(toMetaPoints(pts));
            p.setStyle(Shape.STYLE_STROKE_MASK);
            p.setStrokeColor(CONSTRAINED_COLOR);
            p.setStrokeWeight(6d);
            p.setBasicLineStyle(Polyline.BASIC_LINE_STYLE_SOLID);
            p.setTitle(InterceptAltitudeResult.formatSegment(seg, unit, precision));
            tag(p, seg.zoneUid);
            group.addItem(p);
        }

        InterceptAltitudeResult.Sample crit = result.getCriticalSample();
        GeoPos at = crit != null ? crit.position : result.getPath().get(0);
        if (at != null) {
            Marker m = new Marker(at.toGeoPoint(), INTERCEPT_PREFIX + "callout");
            m.setType("b-m-p-s-p-i");
            m.setTitle(interceptCalloutText(result, unit, precision));
            m.setIconVisibility(Marker.ICON_INVISIBLE);
            m.setTextColor(CRITICAL_TEXT_COLOR);
            m.setClickable(false);
            tag(m, null);
            group.addItem(m);
        }
    }

    // ---------------------------------------------------------------- pure helpers

    /** "Bldg A\n45.0 m AGL" (label omitted when blank). */
    public static String calloutText(String label, double heightAglMeters, UnitSystem unit, int precision) {
        String h = UnitConverter.format(heightAglMeters, unit, precision) + " AGL";
        if (label == null || label.trim().isEmpty()) return h;
        return label.trim() + "\n" + h;
    }

    /** "Min intercept 165.0 m HAE (55.0 m AGL)" for the map callout. */
    public static String interceptCalloutText(InterceptAltitudeResult r, UnitSystem unit, int precision) {
        if (!r.hasConstraint()) return "Intercept: no zone constraint";
        return "Min intercept " + r.formatAltitude(unit, precision);
    }

    static int opaque(int argb) {
        return argb | 0xFF000000;
    }

    private static void tag(MapItem item, String zoneUid) {
        item.setMetaBoolean("nevercot", true);
        item.setMetaBoolean("addToObjList", false);
        item.setMetaBoolean("editable", false);
        item.setMetaBoolean("removable", false);
        if (zoneUid != null) item.setMetaString(META_ZONE_UID, zoneUid);
    }

    private static GeoPointMetaData[] toMetaPoints(List<GeoPos> pts) {
        GeoPointMetaData[] out = new GeoPointMetaData[pts.size()];
        for (int i = 0; i < out.length; i++) out[i] = GeoPointMetaData.wrap(pts.get(i).toGeoPoint());
        return out;
    }
}
