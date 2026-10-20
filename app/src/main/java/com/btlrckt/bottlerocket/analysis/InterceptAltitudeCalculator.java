package com.btlrckt.bottlerocket.analysis;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.btlrckt.bottlerocket.model.ExclusionZone;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.model.ThreatCorridor;
import com.btlrckt.bottlerocket.util.DtedHelper;
import com.btlrckt.bottlerocket.util.GeoUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimum safe intercept altitude along an ingress line.
 *
 * <p>The ingress path (a user-drawn straight line, or a {@link ThreatCorridor} centreline)
 * is sampled every {@code sampleSpacingMeters}. At each sample the calculator finds every
 * {@link ExclusionZone} that touches the path there and takes the highest roof:
 * <pre>roofHae = zoneBaseHae + heightAgl</pre>
 * The minimum safe intercept altitude at that sample is {@code roofHae + safetyBuffer}; the
 * overall answer is the maximum of that over the whole path (the altitude an interceptor must
 * hold to engage anywhere on the line without violating any zone's vertical limit).
 *
 * <p>Zone base elevation is conservative: the highest DTED value among the zone's vertices
 * and centroid, raised to the terrain at the sample point when the sample lies inside the
 * footprint. Missing DTED is treated as 0 m HAE and reported via
 * {@link InterceptAltitudeResult#isTerrainComplete()}.
 *
 * <p>A zone "touches" a sample when the sample is inside the footprint, or when either
 * path interval adjacent to the sample comes within {@code halfWidthMeters} of the
 * footprint boundary (0 for a straight line = the interval crosses the boundary). The interval
 * test guarantees zones smaller than the sample spacing are never skipped; the price is that a
 * constraint may start / end up to one sample spacing early / late (conservative).
 *
 * <p>Pure Java (no Android / ATAK runtime) so it can be unit tested on the JVM.
 */
public final class InterceptAltitudeCalculator {
    public static final double DEFAULT_SAFETY_BUFFER_M = 10.0;
    public static final double DEFAULT_SAMPLE_SPACING_M = 10.0;
    /** Hard cap on samples; spacing grows automatically for very long paths. */
    public static final int MAX_SAMPLES = 5000;

    private final DtedHelper dted;
    private final double sampleSpacingMeters;
    private final double safetyBufferMeters;

    public InterceptAltitudeCalculator(DtedHelper dted) {
        this(dted, DEFAULT_SAMPLE_SPACING_M, DEFAULT_SAFETY_BUFFER_M);
    }

    public InterceptAltitudeCalculator(DtedHelper dted, double sampleSpacingMeters, double safetyBufferMeters) {
        if (dted == null) throw new IllegalArgumentException("dted helper required");
        if (!(sampleSpacingMeters > 0) || Double.isInfinite(sampleSpacingMeters)) {
            throw new IllegalArgumentException("sample spacing must be > 0: " + sampleSpacingMeters);
        }
        if (!(safetyBufferMeters >= 0) || Double.isInfinite(safetyBufferMeters)) {
            throw new IllegalArgumentException("safety buffer must be >= 0: " + safetyBufferMeters);
        }
        this.dted = dted;
        this.sampleSpacingMeters = sampleSpacingMeters;
        this.safetyBufferMeters = safetyBufferMeters;
    }

    public double getSampleSpacingMeters() {
        return sampleSpacingMeters;
    }

    public double getSafetyBufferMeters() {
        return safetyBufferMeters;
    }

    /** Convenience entry point taking ATAK {@link GeoPoint}s. */
    public static InterceptAltitudeResult compute(List<ExclusionZone> zones, GeoPoint start, GeoPoint end,
            DtedHelper dted, double safetyBufferMeters) {
        return new InterceptAltitudeCalculator(dted, DEFAULT_SAMPLE_SPACING_M, safetyBufferMeters)
                .computeForLine(zones, GeoPos.fromGeoPoint(start), GeoPos.fromGeoPoint(end));
    }

    /** Straight ingress line between two points (zero width). */
    public InterceptAltitudeResult computeForLine(List<ExclusionZone> zones, GeoPos start, GeoPos end) {
        List<GeoPos> path = new ArrayList<>(2);
        path.add(start);
        path.add(end);
        return compute(zones, path, 0.0, null, "Ingress line");
    }

    /** Threat corridor centreline; zones within half the corridor width also constrain. */
    public InterceptAltitudeResult computeForCorridor(List<ExclusionZone> zones, ThreatCorridor corridor) {
        if (corridor == null) throw new IllegalArgumentException("corridor required");
        double half = corridor.widthMeters > 0 ? corridor.widthMeters / 2.0 : 0.0;
        return compute(zones, corridor.points, half, corridor.uid, corridor.label);
    }

    /**
     * General form.
     *
     * @param zones           exclusion zones (invalid / null entries are ignored)
     * @param path            ordered ingress polyline, at least two valid points
     * @param halfWidthMeters lateral half-width around the path (0 = thin line)
     * @param sourceUid       uid of the corridor analysed (null for an ad-hoc line)
     * @param sourceLabel     display name of the analysed path
     */
    public InterceptAltitudeResult compute(List<ExclusionZone> zones, List<GeoPos> path, double halfWidthMeters,
            String sourceUid, String sourceLabel) {
        if (path == null || path.size() < 2) throw new IllegalArgumentException("path needs >= 2 points");
        for (GeoPos p : path) {
            if (p == null || !p.isValid()) throw new IllegalArgumentException("invalid path point: " + p);
        }
        double halfWidth = Double.isNaN(halfWidthMeters) || halfWidthMeters < 0 ? 0.0 : halfWidthMeters;

        LocalProjection proj = new LocalProjection(path.get(0));
        List<ZoneGeom> geoms = prepareZones(zones, proj);
        boolean terrainComplete = true;
        for (ZoneGeom g : geoms) terrainComplete &= g.baseKnown;

        // --- sample positions along the path
        double totalLength = 0;
        for (int i = 1; i < path.size(); i++) totalLength += GeoUtils.distanceMeters(path.get(i - 1), path.get(i));
        double spacing = sampleSpacingMeters;
        if (totalLength / spacing > MAX_SAMPLES - 1) spacing = totalLength / (MAX_SAMPLES - 1);
        List<GeoPos> pts = new ArrayList<>();
        List<Double> dists = new ArrayList<>();
        double cumulative = 0;
        for (int s = 1; s < path.size(); s++) {
            GeoPos a = path.get(s - 1), b = path.get(s);
            double len = GeoUtils.distanceMeters(a, b);
            int n = Math.max(1, (int) Math.ceil(len / spacing - 1e-9));
            for (int k = (s == 1 ? 0 : 1); k <= n; k++) {
                double f = (double) k / n;
                GeoPos p = k == 0 ? new GeoPos(a.lat, a.lon)
                        : (k == n ? new GeoPos(b.lat, b.lon) : GeoUtils.interpolate(a, b, f));
                pts.add(p);
                dists.add(cumulative + len * f);
            }
            cumulative += len;
        }

        int count = pts.size();
        double[][] xy = new double[count][];
        for (int i = 0; i < count; i++) xy[i] = proj.project(pts.get(i));

        // --- evaluate each sample
        List<InterceptAltitudeResult.Sample> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            GeoPos p = pts.get(i);
            double ground = dted.getElevationHae(p);
            if (Double.isNaN(ground)) terrainComplete = false;
            double bestRoof = Double.NaN;
            ZoneGeom best = null;
            for (ZoneGeom g : geoms) {
                boolean inside = g.contains(xy[i]);
                boolean touches = inside
                        || (i > 0 && g.segmentDistance(xy[i - 1], xy[i]) <= halfWidth)
                        || (i < count - 1 && g.segmentDistance(xy[i], xy[i + 1]) <= halfWidth);
                if (!touches) continue;
                double base = g.baseKnown ? g.baseHae : 0.0;
                if (inside && !Double.isNaN(ground)) base = Math.max(base, ground);
                double roof = base + g.zone.heightAglMeters;
                if (best == null || roof > bestRoof) {
                    bestRoof = roof;
                    best = g;
                }
            }
            double minSafe = best == null ? Double.NaN : bestRoof + safetyBufferMeters;
            samples.add(new InterceptAltitudeResult.Sample(dists.get(i), p, ground, bestRoof, minSafe,
                    best == null ? null : best.zone.uid, best == null ? null : best.zone.label));
        }

        List<String> touched = new ArrayList<>();
        for (InterceptAltitudeResult.Sample s : samples) {
            if (s.zoneUid != null && !touched.contains(s.zoneUid)) touched.add(s.zoneUid);
        }
        return new InterceptAltitudeResult(sourceUid, sourceLabel, new ArrayList<>(path), totalLength, spacing,
                safetyBufferMeters, halfWidth, samples, Collections.unmodifiableList(touched), terrainComplete);
    }

    private List<ZoneGeom> prepareZones(List<ExclusionZone> zones, LocalProjection proj) {
        List<ZoneGeom> out = new ArrayList<>();
        if (zones == null) return out;
        for (ExclusionZone z : zones) {
            if (z == null || !z.isValid()) continue;
            double base = Double.NaN;
            for (GeoPos v : z.points) base = maxIgnoringNaN(base, dted.getElevationHae(v));
            base = maxIgnoringNaN(base, dted.getElevationHae(GeoUtils.centroid(z.points)));
            out.add(new ZoneGeom(z, proj, base));
        }
        return out;
    }

    private static double maxIgnoringNaN(double a, double b) {
        if (Double.isNaN(a)) return b;
        if (Double.isNaN(b)) return a;
        return Math.max(a, b);
    }

    // ------------------------------------------------------------ planar geometry

    /** Equirectangular projection to local metres around an origin (fine for tactical ranges). */
    static final class LocalProjection {
        private final double lat0, lon0, cosLat0;

        LocalProjection(GeoPos origin) {
            lat0 = origin.lat;
            lon0 = origin.lon;
            cosLat0 = Math.cos(Math.toRadians(origin.lat));
        }

        double[] project(GeoPos p) {
            double x = Math.toRadians(p.lon - lon0) * GeoUtils.EARTH_RADIUS_M * cosLat0;
            double y = Math.toRadians(p.lat - lat0) * GeoUtils.EARTH_RADIUS_M;
            return new double[] {x, y};
        }
    }

    private static final class ZoneGeom {
        final ExclusionZone zone;
        final double[][] v;
        final double baseHae;
        final boolean baseKnown;
        final double minX, minY, maxX, maxY;

        ZoneGeom(ExclusionZone zone, LocalProjection proj, double baseHae) {
            this.zone = zone;
            this.baseHae = baseHae;
            this.baseKnown = !Double.isNaN(baseHae);
            v = new double[zone.points.size()][];
            double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
            for (int i = 0; i < v.length; i++) {
                v[i] = proj.project(zone.points.get(i));
                x0 = Math.min(x0, v[i][0]);
                y0 = Math.min(y0, v[i][1]);
                x1 = Math.max(x1, v[i][0]);
                y1 = Math.max(y1, v[i][1]);
            }
            minX = x0;
            minY = y0;
            maxX = x1;
            maxY = y1;
        }

        boolean contains(double[] p) {
            if (p[0] < minX || p[0] > maxX || p[1] < minY || p[1] > maxY) return false;
            boolean inside = false;
            for (int i = 0, j = v.length - 1; i < v.length; j = i++) {
                if ((v[i][1] > p[1]) != (v[j][1] > p[1])
                        && p[0] < (v[j][0] - v[i][0]) * (p[1] - v[i][1]) / (v[j][1] - v[i][1]) + v[i][0]) {
                    inside = !inside;
                }
            }
            return inside;
        }

        /** Minimum distance between segment p-q and the polygon boundary (0 when they cross). */
        double segmentDistance(double[] p, double[] q) {
            double best = Double.MAX_VALUE;
            for (int i = 0, j = v.length - 1; i < v.length; j = i++) {
                best = Math.min(best, segSegDistance(p, q, v[j], v[i]));
                if (best == 0) return 0;
            }
            return best;
        }
    }

    static double segSegDistance(double[] a, double[] b, double[] c, double[] d) {
        if (segmentsIntersect(a, b, c, d)) return 0;
        return Math.min(Math.min(pointSegDistance(a, c, d), pointSegDistance(b, c, d)),
                Math.min(pointSegDistance(c, a, b), pointSegDistance(d, a, b)));
    }

    static double pointSegDistance(double[] p, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double len2 = dx * dx + dy * dy;
        double t = len2 < 1e-12 ? 0 : ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double ex = a[0] + t * dx - p[0], ey = a[1] + t * dy - p[1];
        return Math.sqrt(ex * ex + ey * ey);
    }

    static boolean segmentsIntersect(double[] a, double[] b, double[] c, double[] d) {
        double d1 = cross(c, d, a), d2 = cross(c, d, b), d3 = cross(a, b, c), d4 = cross(a, b, d);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true;
        return (d1 == 0 && onSegment(c, d, a)) || (d2 == 0 && onSegment(c, d, b))
                || (d3 == 0 && onSegment(a, b, c)) || (d4 == 0 && onSegment(a, b, d));
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    private static boolean onSegment(double[] a, double[] b, double[] p) {
        return Math.min(a[0], b[0]) <= p[0] && p[0] <= Math.max(a[0], b[0])
                && Math.min(a[1], b[1]) <= p[1] && p[1] <= Math.max(a[1], b[1]);
    }
}
