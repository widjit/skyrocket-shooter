package com.skiarkt.shooter.export;

import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.ExclusionZone;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.KineticEffector;
import com.skiarkt.shooter.model.NonKineticEffector;
import com.skiarkt.shooter.model.PointDefensePlan;
import com.skiarkt.shooter.model.PointItem;
import com.skiarkt.shooter.model.PolygonItem;
import com.skiarkt.shooter.model.SensorItem;
import com.skiarkt.shooter.model.ThreatCorridor;
import com.skiarkt.shooter.settings.UnitSystem;
import com.skiarkt.shooter.util.UnitConverter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Framework-free parts of the PNG export (file naming, legend/summary text, plan-to-pixel
 * projection and scale bar), kept separate from the Android drawing code so they are unit-testable.
 */
public final class PngExportModel {
    private static final double METERS_PER_DEG_LAT = 111_320.0;

    private PngExportModel() {
    }

    /** "FOB_Bravo_20261016-182233Z.png" (UTC). */
    public static String fileName(PointDefensePlan plan, long nowMillis) {
        String safe = plan.name == null ? "" : plan.name.replaceAll("[^A-Za-z0-9_-]+", "_");
        safe = safe.replaceAll("^_+|_+$", "");
        if (safe.isEmpty()) safe = "plan";
        return safe + "_" + utc(nowMillis, "yyyyMMdd-HHmmss") + "Z.png";
    }

    /** Date-time group for the header, e.g. "16 OCT 2026 1822Z". */
    public static String dtg(long millis) {
        return utc(millis, "dd MMM yyyy HHmm").toUpperCase(Locale.US) + "Z";
    }

    private static String utc(long millis, String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }

    /** One line per item kind with at least one item. */
    public static List<String> summaryLines(PointDefensePlan plan, UnitSystem unit, int decimals) {
        List<String> out = new ArrayList<>();
        for (ItemKind kind : ItemKind.values()) {
            List<BasePlanItem> items = plan.listFor(kind);
            if (items.isEmpty()) continue;
            out.add(kind.displayName + " (" + items.size() + ")");
            for (BasePlanItem item : items) {
                out.add("  " + safeLabel(item) + describe(item, unit, decimals));
            }
        }
        if (out.isEmpty()) out.add("No plan items");
        return out;
    }

    private static String safeLabel(BasePlanItem item) {
        return item.label == null || item.label.isEmpty() ? item.getKind().displayName : item.label;
    }

    private static String describe(BasePlanItem item, UnitSystem u, int d) {
        if (item instanceof SensorItem) {
            SensorItem s = (SensorItem) item;
            return " - " + s.type.name() + ", range " + UnitConverter.format(s.rangeMeters, u, d)
                    + (s.isOmnidirectional() ? ", 360\u00B0" : String.format(Locale.US, ", %.0f\u00B0 @ %.0f\u00B0T",
                    s.beamWidthDeg, s.azimuthDeg));
        }
        if (item instanceof NonKineticEffector) {
            return " - max " + UnitConverter.format(((PointItem) item).maxEngagementDistMeters, u, d);
        }
        if (item instanceof KineticEffector) {
            KineticEffector k = (KineticEffector) item;
            return " - max " + UnitConverter.format(k.maxEngagementDistMeters, u, d)
                    + ", frag " + UnitConverter.format(k.fragmentationRadiusMeters, u, d);
        }
        if (item instanceof ExclusionZone) {
            return " - height " + UnitConverter.format(((ExclusionZone) item).heightAglMeters, u, d) + " AGL";
        }
        if (item instanceof ThreatCorridor) {
            ThreatCorridor c = (ThreatCorridor) item;
            return " - width " + UnitConverter.format(c.widthMeters, u, d);
        }
        return "";
    }

    /** Coverage radius drawn for a point item in the fallback diagram (meters). */
    public static double coverageRadiusMeters(PointItem p) {
        if (p instanceof SensorItem) return ((SensorItem) p).rangeMeters;
        return p.maxEngagementDistMeters;
    }

    /** Largest of 1/2/5 x 10^n meters that is <= {@code maxMeters} (>= 1 m). */
    public static double niceScaleMeters(double maxMeters) {
        if (!(maxMeters > 1)) return 1;
        double p = Math.pow(10, Math.floor(Math.log10(maxMeters)));
        for (double m : new double[]{5, 2, 1}) {
            if (m * p <= maxMeters) return m * p;
        }
        return p;
    }

    /**
     * Local equirectangular projection fitted to every item's geometry (including coverage
     * radii) inside a pixel rectangle, north up, uniform scale.
     */
    public static final class Projection {
        public final double metersPerPixel;
        private final double centerLat, centerLon, cosLat, centerX, centerY;

        private Projection(double centerLat, double centerLon, double metersPerPixel, double centerX, double centerY) {
            this.centerLat = centerLat;
            this.centerLon = centerLon;
            this.cosLat = Math.max(1e-6, Math.cos(Math.toRadians(centerLat)));
            this.metersPerPixel = metersPerPixel;
            this.centerX = centerX;
            this.centerY = centerY;
        }

        public float x(GeoPos p) {
            return (float) (centerX + (p.lon - centerLon) * METERS_PER_DEG_LAT * cosLat / metersPerPixel);
        }

        public float y(GeoPos p) {
            return (float) (centerY - (p.lat - centerLat) * METERS_PER_DEG_LAT / metersPerPixel);
        }

        public float pixels(double meters) {
            return (float) (meters / metersPerPixel);
        }

        /** @return null when the plan has no valid geometry. */
        public static Projection fit(PointDefensePlan plan, float left, float top, float width, float height,
                                     float marginPx) {
            double[] b = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE}; // N/E in meters
            boolean any = false;
            double refLat = Double.NaN;
            for (BasePlanItem item : plan.getAllItems()) {
                GeoPos a = item.getAnchor();
                if (a != null && a.isValid()) {
                    refLat = a.lat;
                    break;
                }
            }
            if (Double.isNaN(refLat)) return null;
            double cos = Math.max(1e-6, Math.cos(Math.toRadians(refLat)));
            for (BasePlanItem item : plan.getAllItems()) {
                if (item instanceof PointItem) {
                    PointItem p = (PointItem) item;
                    if (p.position == null || !p.position.isValid()) continue;
                    any |= extend(b, p.position, cos, Math.max(0, coverageRadiusMeters(p)));
                } else {
                    for (GeoPos g : pointsOf(item)) {
                        if (g != null && g.isValid()) any |= extend(b, g, cos, 0);
                    }
                }
            }
            if (!any) return null;
            double spanE = Math.max(b[3] - b[2], 50), spanN = Math.max(b[1] - b[0], 50);
            float w = Math.max(1, width - 2 * marginPx), h = Math.max(1, height - 2 * marginPx);
            double mpp = Math.max(spanE / w, spanN / h);
            double cN = (b[0] + b[1]) / 2, cE = (b[2] + b[3]) / 2;
            double cLat = cN / METERS_PER_DEG_LAT, cLon = cE / (METERS_PER_DEG_LAT * cos);
            return new Projection(cLat, cLon, mpp, left + width / 2.0, top + height / 2.0);
        }

        private static boolean extend(double[] b, GeoPos g, double cos, double r) {
            double n = g.lat * METERS_PER_DEG_LAT, e = g.lon * METERS_PER_DEG_LAT * cos;
            b[0] = Math.min(b[0], n - r);
            b[1] = Math.max(b[1], n + r);
            b[2] = Math.min(b[2], e - r);
            b[3] = Math.max(b[3], e + r);
            return true;
        }
    }

    /** Polygon / polyline vertices of an item (empty for point items). */
    public static List<GeoPos> pointsOf(BasePlanItem item) {
        if (item instanceof PolygonItem && ((PolygonItem) item).points != null) return ((PolygonItem) item).points;
        if (item instanceof ThreatCorridor && ((ThreatCorridor) item).points != null) {
            return ((ThreatCorridor) item).points;
        }
        return new ArrayList<>();
    }

    /** Legend entries (kind, ARGB) for kinds present in the plan. */
    public static List<Object[]> legend(PointDefensePlan plan) {
        List<Object[]> out = new ArrayList<>();
        for (ItemKind k : ItemKind.values()) {
            List<BasePlanItem> items = plan.listFor(k);
            if (items.isEmpty()) continue;
            out.add(new Object[]{k.displayName, items.get(0).color});
        }
        return out;
    }
}
