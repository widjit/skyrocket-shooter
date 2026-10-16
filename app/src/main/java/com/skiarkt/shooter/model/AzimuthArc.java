package com.skiarkt.shooter.model;

import java.util.Locale;

/**
 * Bearing arc (degrees true) swept clockwise from {@link #bearingMin} to {@link #bearingMax};
 * wraps through north when max &lt; min (e.g. 350..10). Used for no-fire (restricted) azimuths.
 */
public class AzimuthArc {
    public double bearingMin;
    public double bearingMax;

    public AzimuthArc() {
    }

    public AzimuthArc(double bearingMin, double bearingMax) {
        this.bearingMin = bearingMin;
        this.bearingMax = bearingMax;
    }

    public static double normalize(double deg) {
        double d = deg % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    /** Clockwise sweep in (0, 360]; equal bounds = full circle. */
    public double sweepDeg() {
        double s = normalize(bearingMax - bearingMin);
        return s == 0 ? 360.0 : s;
    }

    public boolean contains(double bearingDeg) {
        return normalize(bearingDeg - bearingMin) <= sweepDeg() + 1e-9;
    }

    public boolean isValid() {
        return isBearing(bearingMin) && isBearing(bearingMax);
    }

    static boolean isBearing(double d) {
        return !Double.isNaN(d) && d >= 0 && d <= 360;
    }

    public AzimuthArc copy() {
        return new AzimuthArc(bearingMin, bearingMax);
    }

    /** "min:max" (locale independent). */
    public String encode() {
        return Double.toString(bearingMin) + ":" + Double.toString(bearingMax);
    }

    /** Parses "min:max" or "min-max"; null when malformed. */
    public static AzimuthArc parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        int i = t.indexOf(':');
        if (i < 0) i = t.indexOf('-', 1);
        if (i <= 0) return null;
        try {
            AzimuthArc a = new AzimuthArc(Double.parseDouble(t.substring(0, i).trim()),
                    Double.parseDouble(t.substring(i + 1).trim()));
            return a.isValid() ? a : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AzimuthArc)) return false;
        AzimuthArc a = (AzimuthArc) o;
        return Double.compare(bearingMin, a.bearingMin) == 0 && Double.compare(bearingMax, a.bearingMax) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Double.valueOf(bearingMin).hashCode() + Double.valueOf(bearingMax).hashCode();
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%.0f-%.0f", bearingMin, bearingMax);
    }
}
