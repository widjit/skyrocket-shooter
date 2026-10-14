package com.skiarkt.shooter.util;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.skiarkt.shooter.model.GeoPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Terrain helper wrapping ATAK's elevation stack (DTED / SRTM via ElevationManager).
 * Provides point elevation, terrain profiles and a basic terrain line-of-sight test
 * with 4/3-earth refraction-corrected curvature. All heights are meters HAE unless
 * the method name says AGL.
 */
public class DtedHelper {
    /** Effective earth radius factor for radio / optical LOS (standard atmosphere). */
    public static final double K_FACTOR = 4.0 / 3.0;
    public static final double DEFAULT_SAMPLE_SPACING_M = 30.0; // ~DTED level 2 post spacing
    public static final int MAX_SAMPLES = 2000;

    private final ElevationSource source;

    public DtedHelper() {
        this(new AtakElevationSource());
    }

    public DtedHelper(ElevationSource source) {
        this.source = source;
    }

    public double getElevationHae(double lat, double lon) {
        return source.getElevationHae(lat, lon);
    }

    public double getElevationHae(GeoPos p) {
        return p == null ? Double.NaN : source.getElevationHae(p.lat, p.lon);
    }

    public double getElevationHae(GeoPoint p) {
        return p == null ? Double.NaN : source.getElevationHae(p.getLatitude(), p.getLongitude());
    }

    public boolean hasCoverage(GeoPos p) {
        return !Double.isNaN(getElevationHae(p));
    }

    /** Absolute HAE of a point {@code aglMeters} above terrain; NaN when terrain unknown. */
    public double aglToHae(GeoPos p, double aglMeters) {
        double ground = getElevationHae(p);
        return Double.isNaN(ground) ? Double.NaN : ground + aglMeters;
    }

    public TerrainProfile getTerrainProfile(GeoPoint a, GeoPoint b, double spacingMeters) {
        return getTerrainProfile(GeoPos.fromGeoPoint(a), GeoPos.fromGeoPoint(b), spacingMeters);
    }

    /** Samples terrain every {@code spacingMeters} (capped at {@link #MAX_SAMPLES}), endpoints included. */
    public TerrainProfile getTerrainProfile(GeoPos a, GeoPos b, double spacingMeters) {
        double total = GeoUtils.distanceMeters(a, b);
        double spacing = spacingMeters > 0 ? spacingMeters : DEFAULT_SAMPLE_SPACING_M;
        int segments = (int) Math.ceil(total / spacing);
        segments = Math.max(1, Math.min(MAX_SAMPLES - 1, segments));
        List<TerrainProfile.Sample> samples = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double f = (double) i / segments;
            GeoPos p = i == 0 ? new GeoPos(a.lat, a.lon)
                    : (i == segments ? new GeoPos(b.lat, b.lon) : GeoUtils.interpolate(a, b, f));
            samples.add(new TerrainProfile.Sample(total * f, p, source.getElevationHae(p.lat, p.lon)));
        }
        return new TerrainProfile(samples, total);
    }

    /** Earth-curvature drop (m) at distance d along a path of length D, observer at 0. */
    public static double curvatureBulge(double d, double totalDistance) {
        return d * (totalDistance - d) / (2.0 * K_FACTOR * GeoUtils.EARTH_RADIUS_M);
    }

    /**
     * Terrain line-of-sight between an observer at {@code aglA} above ground at {@code a}
     * and a target at {@code aglB} above ground at {@code b}.
     *
     * @return true when no terrain sample (plus curvature bulge) rises above the sight line.
     *         Missing elevation samples are treated as non-blocking; check
     *         {@link TerrainProfile#isComplete()} when data quality matters.
     */
    public boolean hasLineOfSight(GeoPos a, double aglA, GeoPos b, double aglB, double spacingMeters) {
        TerrainProfile profile = getTerrainProfile(a, b, spacingMeters);
        List<TerrainProfile.Sample> s = profile.getSamples();
        double groundA = s.get(0).hasElevation() ? s.get(0).elevationHae : 0.0;
        double groundB = s.get(s.size() - 1).hasElevation() ? s.get(s.size() - 1).elevationHae : 0.0;
        double hA = groundA + aglA;
        double hB = groundB + aglB;
        double total = profile.getTotalDistanceMeters();
        if (total < 1e-3) return true;
        for (int i = 1; i < s.size() - 1; i++) {
            TerrainProfile.Sample smp = s.get(i);
            if (!smp.hasElevation()) continue;
            double f = smp.distanceMeters / total;
            double lineHeight = hA + (hB - hA) * f;
            double terrain = smp.elevationHae + curvatureBulge(smp.distanceMeters, total);
            if (terrain > lineHeight) return false;
        }
        return true;
    }
}
