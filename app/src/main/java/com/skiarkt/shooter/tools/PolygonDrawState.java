package com.skiarkt.shooter.tools;

import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.util.GeoUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java vertex bookkeeping for the draw tool (no Android / ATAK types), so the
 * add / undo / close rules are unit testable.
 */
public final class PolygonDrawState {
    /** Taps closer than this to the previous vertex are ignored (double-tap jitter). */
    public static final double MIN_VERTEX_SEPARATION_M = 0.5;
    public static final int MAX_VERTICES = 500;

    private final List<GeoPos> vertices = new ArrayList<>();
    private final int requiredVertices;

    /** @param requiredVertices 3 for a polygon; 2 for a straight line. */
    public PolygonDrawState(int requiredVertices) {
        this.requiredVertices = Math.max(2, requiredVertices);
    }

    /** @return true if the vertex was accepted. */
    public boolean add(GeoPos p) {
        if (p == null || !p.isValid() || vertices.size() >= MAX_VERTICES) return false;
        if (!vertices.isEmpty()
                && GeoUtils.distanceMeters(vertices.get(vertices.size() - 1), p) < MIN_VERTEX_SEPARATION_M) {
            return false;
        }
        vertices.add(new GeoPos(p));
        return true;
    }

    public boolean undo() {
        if (vertices.isEmpty()) return false;
        vertices.remove(vertices.size() - 1);
        return true;
    }

    public void clear() {
        vertices.clear();
    }

    public int size() {
        return vertices.size();
    }

    public boolean canFinish() {
        return vertices.size() >= requiredVertices;
    }

    public List<GeoPos> getVertices() {
        return Collections.unmodifiableList(vertices);
    }

    /** Copy of the vertices for the finished shape (open ring; closure is implicit). */
    public List<GeoPos> snapshot() {
        List<GeoPos> out = new ArrayList<>(vertices.size());
        for (GeoPos p : vertices) out.add(new GeoPos(p));
        return out;
    }

    /** True when any two non-adjacent edges of the closed ring cross (bow-tie footprint). */
    public static boolean isSelfIntersecting(List<GeoPos> ring) {
        int n = ring == null ? 0 : ring.size();
        if (n < 4) return false;
        GeoPos o = ring.get(0);
        double cos = Math.cos(Math.toRadians(o.lat));
        double[][] v = new double[n][];
        for (int i = 0; i < n; i++) {
            v[i] = new double[] {(ring.get(i).lon - o.lon) * cos, ring.get(i).lat - o.lat};
        }
        for (int i = 0; i < n; i++) {
            double[] a = v[i], b = v[(i + 1) % n];
            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) continue; // edges sharing vertex 0
                double[] c = v[j], d = v[(j + 1) % n];
                if (properCross(a, b, c, d)) return true;
            }
        }
        return false;
    }

    private static boolean properCross(double[] a, double[] b, double[] c, double[] d) {
        double d1 = cross(c, d, a), d2 = cross(c, d, b), d3 = cross(a, b, c), d4 = cross(a, b, d);
        return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0)) && d1 != 0 && d2 != 0 && d3 != 0 && d4 != 0;
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }
}
