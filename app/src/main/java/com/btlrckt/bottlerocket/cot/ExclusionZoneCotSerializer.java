package com.btlrckt.bottlerocket.cot;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.btlrckt.bottlerocket.model.BasePlanItem;
import com.btlrckt.bottlerocket.model.ExclusionZone;
import com.btlrckt.bottlerocket.model.ExclusionZoneShape;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.model.PlanColors;

/**
 * CoT encoding for {@link ExclusionZone} (type {@code b-m-p-btlrckt-exczone}). Plugs into the
 * generic {@link CotSerializer} framework, which writes the envelope, contact, remarks,
 * vertex {@code <link point>} children and the stroke color.
 *
 * <pre>
 * &lt;detail&gt;
 *   &lt;contact callsign="Hangar 3"/&gt;
 *   &lt;remarks&gt;...&lt;/remarks&gt;
 *   &lt;link point="lat,lon"/&gt; ...                  footprint vertices (open ring)
 *   &lt;strokeColor value="-29696"/&gt;               ARGB stroke
 *   &lt;fillColor value="1308593920"/&gt;             ARGB fill (alpha = opacity)
 *   &lt;strokeWeight value="3.0"/&gt;
 *   &lt;strokeStyle value="dashed"/&gt;
 *   &lt;height value="45.0"/&gt;                      meters AGL (TAK shape extrusion)
 *   &lt;labels_on value="true"/&gt;
 *   &lt;__btlrckt v="1" kind="exczone" heightAgl="45.0" fillColor=".." fillOpacity="0.3"
 *           strokeWidth="3.0" strokeDashed="true" .../&gt;
 * &lt;/detail&gt;
 * </pre>
 * The {@code __btlrckt} attributes are authoritative; the standard TAK elements are written so
 * WinTAK / ATAK can display the shape without the plugin, and are read back as a fallback.
 */
public final class ExclusionZoneCotSerializer {
    public static final String TYPE = CotTypes.EXCLUSION_ZONE;
    public static final String DETAIL_STROKE_WEIGHT = "strokeWeight";
    public static final String DETAIL_STROKE_STYLE = "strokeStyle";
    public static final String DETAIL_HEIGHT = "height";
    public static final String DETAIL_LABELS_ON = "labels_on";
    public static final String STYLE_DASHED = "dashed";
    public static final String STYLE_SOLID = "solid";

    private ExclusionZoneCotSerializer() {
    }

    /** Full CoT event for one zone. */
    public static CotEvent toCot(ExclusionZone zone, String planUid, int staleDays) {
        return CotSerializer.toCot(zone, planUid, staleDays);
    }

    /** Decodes a zone event; null when the event is not a valid BTLRCKT exclusion zone. */
    public static ExclusionZone fromCot(CotEvent ev) {
        if (ev == null || !TYPE.equals(ev.getType())) return null;
        BasePlanItem item = CotSerializer.fromCot(ev);
        return item instanceof ExclusionZone ? (ExclusionZone) item : null;
    }

    public static boolean isExclusionZone(CotEvent ev) {
        return ev != null && TYPE.equals(ev.getType());
    }

    /** Called by {@link CotSerializer} after the common polygon fields are written. */
    static void encode(ExclusionZone z, CotDetail btlrckt, CotDetail detail) {
        btlrckt.setAttribute("heightAgl", Double.toString(z.heightAglMeters));
        btlrckt.setAttribute("fillColor", String.valueOf(z.fillColor));
        btlrckt.setAttribute("fillOpacity", Float.toString(z.fillOpacity));
        btlrckt.setAttribute("strokeWidth", Double.toString(z.strokeWidth));
        btlrckt.setAttribute("strokeDashed", String.valueOf(z.strokeDashed));
        if (z.getShape() != ExclusionZoneShape.POLYGON && z.hasShapeParams()) {
            btlrckt.setAttribute("shape", z.getShape().name());
            btlrckt.setAttribute("center", z.center.toCotString());
            if (z.getShape() == ExclusionZoneShape.CIRCLE) {
                btlrckt.setAttribute("radius", Double.toString(z.radiusMeters));
            } else {
                btlrckt.setAttribute("rectWidth", Double.toString(z.rectWidthMeters));
                btlrckt.setAttribute("rectLength", Double.toString(z.rectLengthMeters));
                btlrckt.setAttribute("rotation", Double.toString(z.rotationDeg));
            }
        }

        add(detail, CotTypes.DETAIL_FILL_COLOR, String.valueOf(z.getFillArgb()));
        add(detail, DETAIL_STROKE_WEIGHT, Double.toString(z.strokeWidth));
        add(detail, DETAIL_STROKE_STYLE, z.strokeDashed ? STYLE_DASHED : STYLE_SOLID);
        add(detail, DETAIL_HEIGHT, Double.toString(z.heightAglMeters));
        add(detail, DETAIL_LABELS_ON, "true");
    }

    /** Called by {@link CotSerializer#fromCot} for exclusion zone events. */
    static void decode(CotDetail btlrckt, CotDetail detail, ExclusionZone z) {
        CotDetail fill = CotSerializer.child(detail, CotTypes.DETAIL_FILL_COLOR);
        Integer fillArgb = fill == null ? null : parseInt(fill.getAttribute("value"));

        double height = parseDouble(btlrckt.getAttribute("heightAgl"));
        if (Double.isNaN(height)) height = parseDouble(valueOf(detail, DETAIL_HEIGHT));
        if (!Double.isNaN(height)) z.heightAglMeters = height;

        Integer rgb = parseInt(btlrckt.getAttribute("fillColor"));
        if (rgb != null) z.fillColor = rgb;
        else if (fillArgb != null) z.fillColor = fillArgb | 0xFF000000;

        double opacity = parseDouble(btlrckt.getAttribute("fillOpacity"));
        if (Double.isNaN(opacity) && fillArgb != null) opacity = PlanColors.alphaOf(fillArgb);
        if (!Double.isNaN(opacity)) z.fillOpacity = (float) opacity;

        double width = parseDouble(btlrckt.getAttribute("strokeWidth"));
        if (Double.isNaN(width)) width = parseDouble(valueOf(detail, DETAIL_STROKE_WEIGHT));
        if (!Double.isNaN(width)) z.strokeWidth = width;

        String dashed = btlrckt.getAttribute("strokeDashed");
        if (dashed != null) {
            z.strokeDashed = Boolean.parseBoolean(dashed.trim());
        } else {
            String style = valueOf(detail, DETAIL_STROKE_STYLE);
            if (style != null) z.strokeDashed = STYLE_DASHED.equalsIgnoreCase(style.trim());
        }

        // Foreign producers may only send <strokeColor>; the framework reads __btlrckt color first.
        if (btlrckt.getAttribute("color") == null) {
            CotDetail stroke = CotSerializer.child(detail, CotTypes.DETAIL_STROKE_COLOR);
            Integer s = stroke == null ? null : parseInt(stroke.getAttribute("value"));
            if (s != null) z.color = s;
        }
        decodeShape(btlrckt, z);
        z.normalizeStyle();
    }

    /** R3: restores circle / rectangle parameters; points stay as transmitted if params bad. */
    static void decodeShape(CotDetail btlrckt, ExclusionZone z) {
        ExclusionZoneShape shape = ExclusionZoneShape.parse(btlrckt.getAttribute("shape"));
        if (shape == ExclusionZoneShape.POLYGON) {
            z.shape = ExclusionZoneShape.POLYGON;
            return;
        }
        GeoPos c = GeoPos.fromCotString(btlrckt.getAttribute("center"));
        z.shape = shape;
        z.center = c;
        if (shape == ExclusionZoneShape.CIRCLE) {
            double r = parseDouble(btlrckt.getAttribute("radius"));
            z.radiusMeters = Double.isNaN(r) ? 0 : r;
        } else {
            double w = parseDouble(btlrckt.getAttribute("rectWidth"));
            double l = parseDouble(btlrckt.getAttribute("rectLength"));
            double rot = parseDouble(btlrckt.getAttribute("rotation"));
            z.rectWidthMeters = Double.isNaN(w) ? 0 : w;
            z.rectLengthMeters = Double.isNaN(l) ? 0 : l;
            z.rotationDeg = Double.isNaN(rot) ? 0 : rot;
        }
        if (z.hasShapeParams()) {
            z.regeneratePoints();
        } else {
            z.shape = ExclusionZoneShape.POLYGON; // fall back to the transmitted vertices
        }
    }

    private static void add(CotDetail detail, String element, String value) {
        CotDetail c = new CotDetail(element);
        c.setAttribute("value", value);
        detail.addChild(c);
    }

    private static String valueOf(CotDetail detail, String element) {
        CotDetail c = CotSerializer.child(detail, element);
        return c == null ? null : c.getAttribute("value");
    }

    private static double parseDouble(String s) {
        if (s == null) return Double.NaN;
        try {
            double v = Double.parseDouble(s.trim());
            return Double.isInfinite(v) ? Double.NaN : v;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try {
            return (int) Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
