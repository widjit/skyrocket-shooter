package com.btlrckt.bottlerocket.analysis;

import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/** Circle / sector / annular-wedge polygon builders for sensor coverage (pure Java). */
public final class CoverageGeometry {
    /** Default arc vertex spacing (degrees). */
    public static final double DEFAULT_ARC_STEP_DEG = 2.0;

    private CoverageGeometry() {
    }

    public static boolean isOmni(double beamWidthDeg) {
        return beamWidthDeg >= 360.0;
    }

    /** Start bearing of a sector (azimuth - beam/2), normalized to [0, 360). */
    public static double sectorStartDeg(double azimuthDeg, double beamWidthDeg) {
        return isOmni(beamWidthDeg) ? 0.0 : GeoUtils.normalizeDeg(azimuthDeg - beamWidthDeg / 2.0);
    }

    /** True when {@code bearingDeg} lies within azimuth +/- beam/2 (inclusive). */
    public static boolean bearingInSector(double bearingDeg, double azimuthDeg, double beamWidthDeg) {
        if (isOmni(beamWidthDeg)) return true;
        double diff = Math.abs(GeoUtils.normalizeDeg(bearingDeg - azimuthDeg + 180.0) - 180.0);
        return diff <= beamWidthDeg / 2.0 + 1e-9;
    }

    /** Closed-ring (first point not repeated) circle approximation. */
    public static List<GeoPos> circle(GeoPos center, double radiusM, double arcStepDeg) {
        double step = sanitizeStep(arcStepDeg);
        int n = Math.max(8, (int) Math.ceil(360.0 / step));
        List<GeoPos> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(GeoUtils.destination(center, 360.0 * i / n, radiusM));
        return out;
    }

    /**
     * Coverage polygon: a circle when omni, otherwise a pie sector centre -> arc
     * (azimuth - beam/2 .. azimuth + beam/2) -> back to centre (ring implicitly closed).
     */
    public static List<GeoPos> coverage(GeoPos center, double radiusM, double azimuthDeg,
            double beamWidthDeg, double arcStepDeg) {
        if (isOmni(beamWidthDeg)) return circle(center, radiusM, arcStepDeg);
        List<GeoPos> out = new ArrayList<>();
        out.add(new GeoPos(center.lat, center.lon));
        out.addAll(arc(center, radiusM, sectorStartDeg(azimuthDeg, beamWidthDeg), beamWidthDeg, arcStepDeg));
        return out;
    }

    /** Arc points from {@code startDeg} sweeping clockwise {@code sweepDeg}, both ends included. */
    public static List<GeoPos> arc(GeoPos center, double radiusM, double startDeg, double sweepDeg, double arcStepDeg) {
        double step = sanitizeStep(arcStepDeg);
        int n = Math.max(1, (int) Math.ceil(sweepDeg / step));
        List<GeoPos> out = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            out.add(GeoUtils.destination(center, GeoUtils.normalizeDeg(startDeg + sweepDeg * i / n), radiusM));
        }
        return out;
    }

    /**
     * Annular wedge between radii r0 < r1 spanning bearings start..start+sweep (clockwise):
     * outer arc forward, inner arc backward (or the centre when r0 == 0).
     */
    public static List<GeoPos> annularWedge(GeoPos center, double r0, double r1, double startDeg,
            double sweepDeg, double arcStepDeg) {
        List<GeoPos> out = new ArrayList<>(arc(center, r1, startDeg, sweepDeg, arcStepDeg));
        if (r0 <= 0.5) {
            out.add(new GeoPos(center.lat, center.lon));
        } else {
            List<GeoPos> inner = arc(center, r0, startDeg, sweepDeg, arcStepDeg);
            for (int i = inner.size() - 1; i >= 0; i--) out.add(inner.get(i));
        }
        return out;
    }

    private static double sanitizeStep(double s) {
        return s > 0.01 && s <= 90 ? s : DEFAULT_ARC_STEP_DEG;
    }
}
