package com.skiarkt.shooter.analysis;

import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.util.GeoUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RF jammer heat map raster (pure Java). A square grid of {@code cellM} cells (default 20 m)
 * covers the circle of radius = max engagement distance; for each cell centre the received
 * jamming power is computed with {@link RfPropagation} and, when the terrain viewshed
 * ({@link LosSweep}, effector at its AGL, target at the drone proxy AGL) says the cell is
 * masked, the terrain loss is subtracted. Cells outside the circle are transparent (band -1).
 *
 * <p>The raster is stored as quantized color bands ({@link RfPropagation#band}) plus the raw
 * rx values, and can be expanded to an ARGB pixel array ({@link Raster#toArgb}) or merged
 * into rectangles ({@link #mergeRects}) for rendering as map shapes.
 */
public final class RfHeatmap {
    public static final double DEFAULT_CELL_M = 20.0;
    /** Max cells per side; the cell size grows beyond this (e.g. radius > 5 km at 20 m). */
    public static final int MAX_GRID = 500;
    /** Number of gradient steps across the 10 dB marginal band (1 dB each). */
    public static final int GRADIENT_STEPS = 10;

    private RfHeatmap() {
    }

    public static final class Params {
        public double txDbm = 47.0;
        public double frequencyMhz = 2400.0;
        public double thresholdDbm = -70.0;
        public double terrainLossDb = 40.0;
        public double radiusM = 1000.0;
        public double cellM = DEFAULT_CELL_M;
        /**
         * R1 multi-band: when non-empty, each cell's value is the combined jamming margin
         * (rx - band threshold) over these bands, re-expressed as rx relative to
         * {@link #thresholdDbm} so the usual banding / classification still apply.
         */
        public final List<Band> bands = new ArrayList<>();
        /** true = weakest band (min margin), false = union (max margin). */
        public boolean combineWeakest;

        /** Cache-key fragment for the band configuration ("" when single band). */
        public String bandsKey() {
            if (bands.isEmpty()) return "";
            StringBuilder sb = new StringBuilder(combineWeakest ? "W" : "U");
            for (Band b : bands) {
                sb.append(String.format(java.util.Locale.US, "|%.3f:%.2f:%.2f:%.1f",
                        b.txDbm, b.frequencyMhz, b.thresholdDbm, b.maxRangeM));
            }
            return sb.toString();
        }
    }

    /** One band of a multi-band jammer. */
    public static final class Band {
        public final double txDbm, frequencyMhz, thresholdDbm, maxRangeM;

        public Band(double txDbm, double frequencyMhz, double thresholdDbm, double maxRangeM) {
            this.txDbm = txDbm;
            this.frequencyMhz = frequencyMhz;
            this.thresholdDbm = thresholdDbm;
            this.maxRangeM = maxRangeM;
        }
    }

    /**
     * Heat map parameters for an effector (R1-aware). Multi-band effectors with at least one
     * enabled band use the combined-band model; otherwise the legacy single band is used.
     */
    public static Params forEffector(com.skiarkt.shooter.model.NonKineticEffector n,
            double globalFrequencyMhz, double globalThresholdDbm, double terrainLossDb) {
        Params p = new Params();
        p.txDbm = n.eirpDbm;
        p.frequencyMhz = n.effectiveFrequencyMhz(globalFrequencyMhz);
        p.thresholdDbm = n.effectiveThresholdDbm(globalThresholdDbm);
        p.terrainLossDb = terrainLossDb;
        p.radiusM = n.maxEngagementDistMeters;
        p.cellM = DEFAULT_CELL_M;
        List<com.skiarkt.shooter.model.RfBand> enabled = n.enabledBands();
        if (!enabled.isEmpty()) {
            p.combineWeakest = n.getHeatmapMode() == com.skiarkt.shooter.model.RfHeatmapMode.WEAKEST;
            double reach = 0;
            for (com.skiarkt.shooter.model.RfBand b : enabled) {
                double cap = b.rangeCapMeters(n.maxEngagementDistMeters);
                reach = Math.max(reach, cap);
                p.bands.add(new Band(b.eirpDbm, b.frequencyMhz, b.thresholdDbm, cap));
            }
            if (reach > 0) p.radiusM = reach;
        }
        return p;
    }

    /** Sentinel margin for a band that cannot reach a cell (beyond its max range). */
    static final double OUT_OF_RANGE_MARGIN_DB = -200.0;

    /**
     * Combined jamming margin (dB above threshold) at distance {@code d}; NaN when no band
     * reaches the cell in union mode.
     */
    public static double combinedMarginDb(Params p, double d, boolean visible) {
        double best = Double.NEGATIVE_INFINITY, worst = Double.POSITIVE_INFINITY;
        boolean anyInRange = false;
        for (Band b : p.bands) {
            double m;
            if (b.maxRangeM > 0 && d > b.maxRangeM) {
                m = OUT_OF_RANGE_MARGIN_DB;
            } else {
                anyInRange = true;
                m = RfPropagation.receivedPowerDbm(b.txDbm, d, b.frequencyMhz, visible, p.terrainLossDb)
                        - b.thresholdDbm;
            }
            best = Math.max(best, m);
            worst = Math.min(worst, m);
        }
        if (!anyInRange && !p.combineWeakest) return Double.NaN;
        return p.combineWeakest ? worst : best;
    }

    /** @param los viewshed for the effector (may be null = assume LOS everywhere). */
    public static Raster compute(GeoPos center, Params p, LosSweep.Result los) {
        if (center == null || p == null || !(p.radiusM > 0)) throw new IllegalArgumentException("bad heat map input");
        double cell = p.cellM > 0 ? p.cellM : DEFAULT_CELL_M;
        int n = 2 * (int) Math.ceil(p.radiusM / cell);
        if (n > MAX_GRID) {
            n = MAX_GRID;
            cell = 2.0 * p.radiusM / n;
        }
        n = Math.max(2, n);
        Raster r = new Raster(center, n, cell);
        double half = n * cell / 2.0;
        int inside = 0, eff = 0, marg = 0;
        for (int row = 0; row < n; row++) {
            double y = half - (row + 0.5) * cell;
            for (int col = 0; col < n; col++) {
                double x = -half + (col + 0.5) * cell;
                int idx = row * n + col;
                double d = Math.hypot(x, y);
                if (d > p.radiusM) {
                    r.bands[idx] = -1;
                    r.rxDbm[idx] = Float.NaN;
                    continue;
                }
                double bearing = GeoUtils.normalizeDeg(Math.toDegrees(Math.atan2(x, y)));
                boolean visible = los == null || los.isVisible(bearing, Math.min(d, los.rangeM));
                double rx;
                if (p.bands.isEmpty()) {
                    rx = RfPropagation.receivedPowerDbm(p.txDbm, d, p.frequencyMhz, visible, p.terrainLossDb);
                } else {
                    double margin = combinedMarginDb(p, d, visible);
                    if (Double.isNaN(margin)) {
                        r.bands[idx] = -1;
                        r.rxDbm[idx] = Float.NaN;
                        continue;
                    }
                    rx = p.thresholdDbm + margin;
                }
                r.rxDbm[idx] = (float) rx;
                r.bands[idx] = RfPropagation.band(rx, p.thresholdDbm, GRADIENT_STEPS);
                inside++;
                RfPropagation.Effectiveness e = RfPropagation.classify(rx, p.thresholdDbm);
                if (e == RfPropagation.Effectiveness.EFFECTIVE) eff++;
                else if (e == RfPropagation.Effectiveness.MARGINAL) marg++;
            }
        }
        r.effectiveFraction = inside == 0 ? 0 : (double) eff / inside;
        r.marginalFraction = inside == 0 ? 0 : (double) marg / inside;
        return r;
    }

    public static final class Raster {
        public final GeoPos center;
        public final int size;
        public final double cellM;
        /** Row-major (row 0 = north), -1 = outside the engagement circle. */
        public final int[] bands;
        public final float[] rxDbm;
        public final double north, south, east, west;
        public double effectiveFraction;
        public double marginalFraction;

        Raster(GeoPos center, int size, double cellM) {
            this.center = center;
            this.size = size;
            this.cellM = cellM;
            this.bands = new int[size * size];
            this.rxDbm = new float[size * size];
            double half = size * cellM / 2.0;
            double dLat = Math.toDegrees(half / GeoUtils.EARTH_RADIUS_M);
            double dLon = Math.toDegrees(half / (GeoUtils.EARTH_RADIUS_M * Math.cos(Math.toRadians(center.lat))));
            north = center.lat + dLat;
            south = center.lat - dLat;
            east = center.lon + dLon;
            west = center.lon - dLon;
        }

        public int band(int col, int row) {
            return bands[row * size + col];
        }

        public double latAt(double rowEdge) {
            return north - rowEdge * (north - south) / size;
        }

        public double lonAt(double colEdge) {
            return west + colEdge * (east - west) / size;
        }

        public GeoPos cellCenter(int col, int row) {
            return new GeoPos(latAt(row + 0.5), lonAt(col + 0.5));
        }

        /** ARGB pixels (row 0 = north) using {@code palette} (index = band) at {@code alpha} 0..1. */
        public int[] toArgb(int[] palette, float alpha) {
            int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f) << 24;
            int[] out = new int[bands.length];
            for (int i = 0; i < bands.length; i++) {
                int b = bands[i];
                out[i] = b < 0 ? 0 : (palette[b] & 0x00FFFFFF) | a;
            }
            return out;
        }

        /** Same raster with cells merged in {@code factor x factor} blocks (block-centre sample). */
        public Raster downsample(int factor) {
            if (factor <= 1) return this;
            int ns = (int) Math.ceil(size / (double) factor);
            // keep the same geographic extent: ns cells of (size * cellM / ns) metres
            Raster r = new Raster(center, ns, cellM * size / (double) ns);
            for (int row = 0; row < ns; row++) {
                for (int col = 0; col < ns; col++) {
                    int sr = Math.min(size - 1, row * factor + factor / 2);
                    int sc = Math.min(size - 1, col * factor + factor / 2);
                    r.bands[row * ns + col] = bands[sr * size + sc];
                    r.rxDbm[row * ns + col] = rxDbm[sr * size + sc];
                }
            }
            r.effectiveFraction = effectiveFraction;
            r.marginalFraction = marginalFraction;
            return r;
        }
    }

    /** Rectangle of identical band: columns [col0, col1), rows [row0, row1). */
    public static final class Rect {
        public final int col0, row0, col1, row1, band;

        Rect(int col0, int row0, int col1, int row1, int band) {
            this.col0 = col0;
            this.row0 = row0;
            this.col1 = col1;
            this.row1 = row1;
            this.band = band;
        }
    }

    /**
     * Greedy run-length merge: horizontal runs of equal band, then runs with identical
     * extent and band in consecutive rows are fused. Transparent cells (band < 0) are skipped.
     */
    public static List<Rect> mergeRects(int[] bands, int w, int h) {
        List<Rect> out = new ArrayList<>();
        Map<Long, int[]> open = new HashMap<>(); // key -> {col0, col1, band, row0}
        for (int row = 0; row <= h; row++) {
            Map<Long, int[]> next = new HashMap<>();
            if (row < h) {
                int c = 0;
                while (c < w) {
                    int b = bands[row * w + c];
                    int c0 = c;
                    while (c < w && bands[row * w + c] == b) c++;
                    if (b < 0) continue;
                    long key = ((long) c0 << 40) | ((long) c << 20) | (b & 0xFFFFF);
                    int[] prev = open.remove(key);
                    next.put(key, prev == null ? new int[]{c0, c, b, row} : prev);
                }
            }
            for (int[] o : open.values()) out.add(new Rect(o[0], o[3], o[1], row, o[2]));
            open = next;
        }
        return out;
    }
}
