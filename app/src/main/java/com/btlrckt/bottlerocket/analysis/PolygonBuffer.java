package com.btlrckt.bottlerocket.analysis;

import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.util.GeoUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Buffered-polygon geometry for engagement-authority perimeters (ROADMAP R6). Pure Java.
 *
 * <p>Works in a local equirectangular projection centred on the polygon (meter accuracy over
 * the few-kilometre extents used in point defense).
 *
 * <ul>
 * <li>{@link #contains} is exact: a point is inside a buffer of {@code b} meters when its signed
 * distance to the perimeter (negative inside) is {@code <= b}. Positive b grows the polygon
 * (rounded corners), negative b shrinks it.</li>
 * <li>{@link #offsetRing} produces the display outline: offset edges joined by round arcs on
 * the side that opens up and by miter points on the side that closes. Very concave shapes with
 * large buffers may self-intersect in the drawing only; containment stays exact.</li>
 * </ul>
 */
public final class PolygonBuffer {
    /** Arc segment step for rounded joins (degrees). */
    public static final double DEFAULT_ARC_STEP_DEG = 10.0;

    private PolygonBuffer() {
    }

    /** Local planar frame (x east, y north, meters). */
    static final class Frame {
        final double lat0, lon0, cosLat;

        Frame(List<GeoPos> poly) {
            GeoPos c = GeoUtils.centroid(poly);
            lat0 = c.lat;
            lon0 = c.lon;
            cosLat = Math.cos(Math.toRadians(lat0));
        }

        double[] xy(GeoPos p) {
            double dLon = GeoUtils.normalizeLon(p.lon - lon0);
            return new double[] {Math.toRadians(dLon) * GeoUtils.EARTH_RADIUS_M * cosLat,
                    Math.toRadians(p.lat - lat0) * GeoUtils.EARTH_RADIUS_M};
        }

        GeoPos geo(double x, double y) {
            double lat = lat0 + Math.toDegrees(y / GeoUtils.EARTH_RADIUS_M);
            double lon = lon0 + Math.toDegrees(x / (GeoUtils.EARTH_RADIUS_M * Math.max(1e-9, cosLat)));
            return new GeoPos(lat, GeoUtils.normalizeLon(lon));
        }
    }

    static boolean usable(List<GeoPos> poly) {
        if (poly == null || poly.size() < 3) return false;
        for (GeoPos p : poly) {
            if (p == null || !p.isValid()) return false;
        }
        return true;
    }

    /** Distance (m) from p to the polygon perimeter: negative inside, positive outside. NaN if unusable. */
    public static double signedDistance(GeoPos p, List<GeoPos> poly) {
        if (p == null || !usable(poly)) return Double.NaN;
        Frame f = new Frame(poly);
        double[] q = f.xy(p);
        double best = Double.POSITIVE_INFINITY;
        int n = poly.size();
        double[] a = f.xy(poly.get(n - 1));
        for (int i = 0; i < n; i++) {
            double[] b = f.xy(poly.get(i));
            best = Math.min(best, segDist(q[0], q[1], a[0], a[1], b[0], b[1]));
            a = b;
        }
        return GeoUtils.pointInPolygon(p, poly) ? -best : best;
    }

    /** True when p lies inside {@code poly} buffered by {@code bufferM} (positive = outward). */
    public static boolean contains(GeoPos p, List<GeoPos> poly, double bufferM) {
        double sd = signedDistance(p, poly);
        return !Double.isNaN(sd) && sd <= bufferM;
    }

    /**
     * A polygon with its local frame and projected vertices computed once, for repeated
     * {@link #contains} tests against many points. Results are identical to the static
     * {@link PolygonBuffer#signedDistance} / {@link PolygonBuffer#contains}; the polygon must
     * not be modified while a Prepared instance is in use.
     */
    public static final class Prepared {
        private final List<GeoPos> poly;
        private final Frame frame;
        private final double[][] xy;

        public Prepared(List<GeoPos> poly) {
            this.poly = poly;
            if (usable(poly)) {
                frame = new Frame(poly);
                xy = new double[poly.size()][];
                for (int i = 0; i < xy.length; i++) xy[i] = frame.xy(poly.get(i));
            } else {
                frame = null;
                xy = null;
            }
        }

        public double signedDistance(GeoPos p) {
            if (p == null || frame == null) return Double.NaN;
            double[] q = frame.xy(p);
            double best = Double.POSITIVE_INFINITY;
            int n = xy.length;
            double[] a = xy[n - 1];
            for (int i = 0; i < n; i++) {
                double[] b = xy[i];
                best = Math.min(best, segDist(q[0], q[1], a[0], a[1], b[0], b[1]));
                a = b;
            }
            return GeoUtils.pointInPolygon(p, poly) ? -best : best;
        }

        public boolean contains(GeoPos p, double bufferM) {
            double sd = signedDistance(p);
            return !Double.isNaN(sd) && sd <= bufferM;
        }
    }

    /** Display outline of the buffered polygon; empty when an inward buffer collapses it. */
    public static List<GeoPos> offsetRing(List<GeoPos> poly, double bufferM) {
        return offsetRing(poly, bufferM, DEFAULT_ARC_STEP_DEG);
    }

    public static List<GeoPos> offsetRing(List<GeoPos> poly, double bufferM, double arcStepDeg) {
        if (!usable(poly)) return Collections.emptyList();
        Frame f = new Frame(poly);
        List<double[]> pts = new ArrayList<>();
        for (GeoPos p : poly) {
            double[] xy = f.xy(p);
            if (!pts.isEmpty()) {
                double[] last = pts.get(pts.size() - 1);
                if (Math.hypot(xy[0] - last[0], xy[1] - last[1]) < 1e-3) continue; // drop duplicates
            }
            pts.add(xy);
        }
        if (pts.size() > 1) {
            double[] a = pts.get(0), b = pts.get(pts.size() - 1);
            if (Math.hypot(a[0] - b[0], a[1] - b[1]) < 1e-3) pts.remove(pts.size() - 1);
        }
        if (pts.size() < 3) return Collections.emptyList();
        double area0 = signedArea(pts);
        if (area0 < 0) Collections.reverse(pts); // force CCW: outward normal = right of edge
        area0 = Math.abs(area0);

        List<double[]> out = new ArrayList<>();
        if (Math.abs(bufferM) < 1e-6) {
            out.addAll(pts);
        } else {
            double step = Math.toRadians(Math.max(1.0, arcStepDeg));
            int n = pts.size();
            for (int i = 0; i < n; i++) {
                double[] prev = pts.get((i - 1 + n) % n), v = pts.get(i), next = pts.get((i + 1) % n);
                double[] n1 = normal(prev, v), n2 = normal(v, next);
                double e1x = v[0] - prev[0], e1y = v[1] - prev[1];
                double e2x = next[0] - v[0], e2y = next[1] - v[1];
                double cross = e1x * e2y - e1y * e2x;
                double turn = Math.atan2(n1[0] * n2[1] - n1[1] * n2[0], n1[0] * n2[0] + n1[1] * n2[1]);
                if (Math.abs(turn) < 1e-9) {
                    out.add(new double[] {v[0] + bufferM * n1[0], v[1] + bufferM * n1[1]});
                } else if ((cross > 0) == (bufferM > 0)) {
                    int steps = Math.max(1, (int) Math.ceil(Math.abs(turn) / step));
                    for (int k = 0; k <= steps; k++) {
                        double t = turn * k / steps;
                        double c = Math.cos(t), s = Math.sin(t);
                        double nx = n1[0] * c - n1[1] * s, ny = n1[0] * s + n1[1] * c;
                        out.add(new double[] {v[0] + bufferM * nx, v[1] + bufferM * ny});
                    }
                } else {
                    double denom = 1 + n1[0] * n2[0] + n1[1] * n2[1];
                    if (denom < 0.125) { // very sharp: avoid a long miter spike
                        out.add(new double[] {v[0] + bufferM * n1[0], v[1] + bufferM * n1[1]});
                        out.add(new double[] {v[0] + bufferM * n2[0], v[1] + bufferM * n2[1]});
                    } else {
                        out.add(new double[] {v[0] + bufferM * (n1[0] + n2[0]) / denom,
                                v[1] + bufferM * (n1[1] + n2[1]) / denom});
                    }
                }
            }
        }
        // Drop vertices that ended up closer to the base than the buffer (outward) or not deep
        // enough inside it (inward): these come from self-overlapping offset loops in narrow
        // notches / necks, or from an inward buffer deeper than the shape (inverted ring).
        double tol = 0.02 * Math.abs(bufferM) + 0.5;
        List<GeoPos> geo = new ArrayList<>(out.size());
        List<double[]> kept = new ArrayList<>(out.size());
        for (double[] xy : out) {
            GeoPos g = f.geo(xy[0], xy[1]);
            if (Math.abs(bufferM) >= 1e-6) {
                double sd = signedDistance(g, poly);
                if (bufferM > 0 ? sd < bufferM - tol : sd > bufferM + tol) continue;
            }
            geo.add(g);
            kept.add(xy);
        }
        if (geo.size() < 3) return Collections.emptyList();
        double area1 = signedArea(kept);
        if (bufferM < 0 && (area1 <= 0 || area1 >= area0)) return Collections.emptyList(); // collapsed
        return geo;
    }

    /** Unit outward normal (right-hand side) of edge a->b of a CCW ring. */
    private static double[] normal(double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double len = Math.hypot(dx, dy);
        if (len < 1e-12) return new double[] {0, 0};
        return new double[] {dy / len, -dx / len};
    }

    static double signedArea(List<double[]> pts) {
        double s = 0;
        for (int i = 0; i < pts.size(); i++) {
            double[] a = pts.get(i), b = pts.get((i + 1) % pts.size());
            s += a[0] * b[1] - b[0] * a[1];
        }
        return s / 2.0;
    }

    private static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }
}
