package com.btlrckt.bottlerocket.util;

import com.btlrckt.bottlerocket.settings.UnitSystem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Converts between feet / meters / kilometers. All plan data is stored in meters;
 * conversion happens only at the UI boundary. Default precision is tenths.
 */
public final class UnitConverter {
    public static final int TENTHS = 1;

    private UnitConverter() {
    }

    public static double convert(double value, UnitSystem from, UnitSystem to) {
        if (from == to) return value;
        return value * from.metersPerUnit / to.metersPerUnit;
    }

    public static double toMeters(double value, UnitSystem from) {
        return value * from.metersPerUnit;
    }

    public static double fromMeters(double meters, UnitSystem to) {
        return meters / to.metersPerUnit;
    }

    public static double feetToMeters(double ft) {
        return convert(ft, UnitSystem.FEET, UnitSystem.METERS);
    }

    public static double metersToFeet(double m) {
        return convert(m, UnitSystem.METERS, UnitSystem.FEET);
    }

    /** Half-up rounding to {@code decimals} places (1 = tenths). NaN/Inf pass through. */
    public static double round(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return value;
        return BigDecimal.valueOf(value).setScale(Math.max(0, decimals), RoundingMode.HALF_UP).doubleValue();
    }

    public static double roundTenths(double value) {
        return round(value, TENTHS);
    }

    /** Converts meters to the display unit and rounds it. */
    public static double displayValue(double meters, UnitSystem unit, int decimals) {
        return round(fromMeters(meters, unit), decimals);
    }

    /** e.g. format(1000, FEET, 1) -> "3280.8 ft". Returns "--" for NaN. */
    public static String format(double meters, UnitSystem unit, int decimals) {
        if (Double.isNaN(meters)) return "--";
        int d = Math.max(0, decimals);
        return String.format(Locale.US, "%." + d + "f %s", displayValue(meters, unit, d), unit.abbreviation);
    }

    /** Parses user input expressed in {@code unit} to meters; NaN on failure. */
    public static double parseToMeters(String text, UnitSystem unit) {
        if (text == null) return Double.NaN;
        String t = text.trim().toLowerCase(Locale.US);
        for (UnitSystem u : UnitSystem.values()) {
            if (t.endsWith(u.abbreviation)) {
                t = t.substring(0, t.length() - u.abbreviation.length()).trim();
                unit = u;
                break;
            }
        }
        try {
            return toMeters(Double.parseDouble(t), unit);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
