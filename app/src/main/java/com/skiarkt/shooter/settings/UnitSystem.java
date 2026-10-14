package com.skiarkt.shooter.settings;

/** Distance / height display units selectable in plugin settings. */
public enum UnitSystem {
    FEET("ft", 0.3048),
    METERS("m", 1.0),
    KILOMETERS("km", 1000.0);

    public final String abbreviation;
    /** Number of meters in one unit. */
    public final double metersPerUnit;

    UnitSystem(String abbreviation, double metersPerUnit) {
        this.abbreviation = abbreviation;
        this.metersPerUnit = metersPerUnit;
    }

    public static UnitSystem fromName(String name, UnitSystem fallback) {
        if (name == null) return fallback;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
