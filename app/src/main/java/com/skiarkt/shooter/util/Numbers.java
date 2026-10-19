package com.skiarkt.shooter.util;

/** Lenient parsing of user-typed numbers. */
public final class Numbers {

    private Numbers() {}

    /**
     * Parses {@code s} after trimming and accepting ',' as the decimal separator.
     * Returns NaN for null, blank, malformed or infinite input.
     */
    public static double parseLenient(String s) {
        if (s == null) return Double.NaN;
        String t = s.trim().replace(',', '.');
        if (t.isEmpty()) return Double.NaN;
        try {
            double v = Double.parseDouble(t);
            return Double.isInfinite(v) ? Double.NaN : v;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
