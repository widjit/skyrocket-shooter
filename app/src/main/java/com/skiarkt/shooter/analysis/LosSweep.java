package com.skiarkt.shooter.analysis;

import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.util.DtedHelper;
import com.skiarkt.shooter.util.ElevationSource;
import com.skiarkt.shooter.util.GeoUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Radial terrain viewshed ("ray-march sweep") used for sensor DTED masking and for the RF
 * heat map's line-of-sight test (pure Java; elevation comes from any {@link ElevationSource},
 * normally ATAK's DTED stack via {@code AtakElevationSource}).
 *
 * <p>Rays are cast every {@code angularStepDeg} across the sector (or 360 degrees) and terrain is
 * sampled every {@code rangeStepM} along each ray. With the observer at terrain + observer
 * AGL, a sample is <i>visible</i> when a target {@code targetAglM} above the terrain there is at
 * or above the running terrain horizon (max elevation angle of all nearer samples). Earth
 * curvature with 4/3 refraction ({@link DtedHelper#K_FACTOR}) lowers distant terrain. Samples
 * with no elevation data are visible and non-blocking (same convention as
 * {@link DtedHelper#hasLineOfSight}); if the observer's own terrain is unknown, every sample is
 * visible and {@link Result#terrainAvailable} is false.
 */
public final class LosSweep {
    public static final int MAX_RAYS = 3600;
    /** Upper bound on elevation lookups per sweep; the range step is coarsened beyond it. */
    public static final int MAX_LOOKUPS = 250_000;

    private LosSweep() {
    }

    public static Result compute(ElevationSource src, GeoPos center, double observerAglM, double targetAglM,
            double rangeM, double azimuthDeg, double beamWidthDeg, double angularStepDeg, double rangeStepM) {
        if (center == null || !(rangeM > 0)) throw new IllegalArgumentException("center and range required");
        double ang = angularStepDeg > 0 ? angularStepDeg : 1.0;
        double step = rangeStepM > 0 ? rangeStepM : 50.0;
        boolean omni = CoverageGeometry.isOmni(beamWidthDeg);
        double sweep = omni ? 360.0 : Math.max(0.0, beamWidthDeg);
        double start = CoverageGeometry.sectorStartDeg(azimuthDeg, beamWidthDeg);

        int rays = omni ? (int) Math.ceil(360.0 / ang) : (int) Math.ceil(sweep / ang) + 1;
        rays = Math.max(omni ? 4 : 2, Math.min(MAX_RAYS, rays));
        double actualAng = omni ? 360.0 / rays : sweep / (rays - 1);

        int samples = (int) Math.ceil(rangeM / step);
        if ((long) samples * rays > MAX_LOOKUPS) {
            samples = Math.max(1, MAX_LOOKUPS / rays);
            step = rangeM / samples;
        }
        samples = Math.max(1, samples);

        Result r = new Result(center, rangeM, start, sweep, omni, actualAng, step, rays, samples);
        double ground = src == null ? Double.NaN : src.getElevationHae(center.lat, center.lon);
        r.terrainAvailable = !Double.isNaN(ground);
        double hObs = (r.terrainAvailable ? ground : 0.0) + observerAglM;
        double twoKR = 2.0 * DtedHelper.K_FACTOR * GeoUtils.EARTH_RADIUS_M;

        for (int i = 0; i < rays; i++) {
            double bearing = r.bearings[i];
            boolean[] vis = r.visible[i];
            double maxTan = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < samples; j++) {
                if (!r.terrainAvailable) {
                    vis[j] = true;
                    continue;
                }
                double d = r.sampleDistance(j);
                GeoPos p = GeoUtils.destination(center, bearing, d);
                double e = src.getElevationHae(p.lat, p.lon);
                if (Double.isNaN(e)) {
                    r.missingSamples++;
                    vis[j] = true;
                    continue;
                }
                double terrain = e - d * d / twoKR;
                double targetTan = (terrain + targetAglM - hObs) / d;
                vis[j] = targetTan >= maxTan;
                double terrainTan = (terrain - hObs) / d;
                if (terrainTan > maxTan) maxTan = terrainTan;
            }
        }
        return r;
    }

    /** One masked (not visible) annular wedge, for rendering. Bearings clockwise from start. */
    public static final class Wedge {
        public final double innerM, outerM, startDeg, sweepDeg;

        Wedge(double innerM, double outerM, double startDeg, double sweepDeg) {
            this.innerM = innerM;
            this.outerM = outerM;
            this.startDeg = startDeg;
            this.sweepDeg = sweepDeg;
        }
    }

    public static final class Result {
        public final GeoPos center;
        public final double rangeM;
        public final double startDeg;
        public final double sweepDeg;
        public final boolean omni;
        public final double angularStepDeg;
        public final double rangeStepM;
        public final int rays;
        public final int samples;
        public final double[] bearings;
        /** [ray][sample]; sample j represents the annulus ending at {@link #sampleDistance(int)}. */
        public final boolean[][] visible;
        public boolean terrainAvailable;
        public int missingSamples;

        Result(GeoPos center, double rangeM, double startDeg, double sweepDeg, boolean omni,
                double angularStepDeg, double rangeStepM, int rays, int samples) {
            this.center = center;
            this.rangeM = rangeM;
            this.startDeg = startDeg;
            this.sweepDeg = sweepDeg;
            this.omni = omni;
            this.angularStepDeg = angularStepDeg;
            this.rangeStepM = rangeStepM;
            this.rays = rays;
            this.samples = samples;
            this.bearings = new double[rays];
            for (int i = 0; i < rays; i++) bearings[i] = GeoUtils.normalizeDeg(startDeg + i * angularStepDeg);
            this.visible = new boolean[rays][samples];
        }

        public double sampleDistance(int j) {
            return Math.min((j + 1) * rangeStepM, rangeM);
        }

        /** Visibility at a bearing / distance (nearest ray, enclosing sample); false outside coverage. */
        public boolean isVisible(double bearingDeg, double distanceM) {
            if (distanceM > rangeM + 1e-6) return false;
            int ray = rayIndex(bearingDeg);
            if (ray < 0) return false;
            if (distanceM <= 0) return true;
            int j = (int) Math.ceil(distanceM / rangeStepM) - 1;
            j = Math.max(0, Math.min(samples - 1, j));
            return visible[ray][j];
        }

        /** Nearest ray to a bearing, or -1 when outside the sector. */
        public int rayIndex(double bearingDeg) {
            double off = GeoUtils.normalizeDeg(bearingDeg - startDeg);
            if (omni) return (int) Math.round(off / angularStepDeg) % rays;
            if (off > sweepDeg + 1e-9) {
                // just counter-clockwise of the start edge counts as outside
                return -1;
            }
            return Math.max(0, Math.min(rays - 1, (int) Math.round(off / angularStepDeg)));
        }

        /** Area-weighted fraction of the coverage footprint with terrain LOS (0..1). */
        public double visibleFraction() {
            double vis = 0, total = 0;
            for (int j = 0; j < samples; j++) {
                double d1 = sampleDistance(j);
                double d0 = j == 0 ? 0 : sampleDistance(j - 1);
                double w = d1 * d1 - d0 * d0;
                for (int i = 0; i < rays; i++) {
                    double rw = rayWeight(i) * w;
                    total += rw;
                    if (visible[i][j]) vis += rw;
                }
            }
            return total <= 0 ? 1.0 : vis / total;
        }

        private double rayWeight(int i) {
            return (!omni && (i == 0 || i == rays - 1)) ? 0.5 : 1.0;
        }

        /** Angular span [start, start + sweep] covered by ray {@code i} (clipped to the sector). */
        double[] raySpan(int i) {
            double half = angularStepDeg / 2.0;
            double a0 = i * angularStepDeg - half;
            double a1 = i * angularStepDeg + half;
            if (!omni) {
                a0 = Math.max(0.0, a0);
                a1 = Math.min(sweepDeg, a1);
            }
            return new double[]{a0, a1};
        }

        /**
         * Masked regions as annular wedges. Consecutive rays with an identical masked sample
         * run are merged into one wedge to keep the map item count low.
         */
        public List<Wedge> maskedWedges() {
            List<Wedge> out = new ArrayList<>();
            // key = run (j0,j1) -> [firstRay, lastRay]
            Map<Long, int[]> open = new HashMap<>();
            for (int i = 0; i <= rays; i++) {
                Map<Long, int[]> next = new HashMap<>();
                if (i < rays) {
                    boolean[] v = visible[i];
                    int j = 0;
                    while (j < samples) {
                        if (v[j]) {
                            j++;
                            continue;
                        }
                        int j0 = j;
                        while (j < samples && !v[j]) j++;
                        long key = ((long) j0 << 32) | (j - 1);
                        int[] span = open.remove(key);
                        next.put(key, span == null ? new int[]{i, i} : new int[]{span[0], i});
                    }
                }
                for (Map.Entry<Long, int[]> e : open.entrySet()) out.add(toWedge(e.getKey(), e.getValue()));
                open = next;
            }
            return out;
        }

        private Wedge toWedge(long key, int[] span) {
            int j0 = (int) (key >>> 32);
            int j1 = (int) (key & 0xFFFFFFFFL);
            double inner = j0 == 0 ? 0 : sampleDistance(j0 - 1);
            double outer = sampleDistance(j1);
            double a0 = raySpan(span[0])[0];
            double a1 = raySpan(span[1])[1];
            return new Wedge(inner, outer, GeoUtils.normalizeDeg(startDeg + a0), a1 - a0);
        }

        /** Number of masked samples (diagnostics / tests). */
        public int maskedSampleCount() {
            int n = 0;
            for (boolean[] row : visible) for (boolean b : row) if (!b) n++;
            return n;
        }
    }
}
