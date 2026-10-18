package com.skiarkt.shooter.model;

/**
 * Footprint shape of an {@link ExclusionZone} (R3). Circles and rectangles are stored as
 * parameters and expanded into {@link PolygonItem#points} so every polygon-based analysis
 * (intercept altitude, line-of-fire, authority-zone bases) keeps working unchanged.
 */
public enum ExclusionZoneShape {
    POLYGON("Polygon"),
    CIRCLE("Circle"),
    RECTANGLE("Rectangle");

    public final String displayName;

    ExclusionZoneShape(String displayName) {
        this.displayName = displayName;
    }

    /** Lenient parse; unknown / null values fall back to {@link #POLYGON}. */
    public static ExclusionZoneShape parse(String s) {
        if (s == null) return POLYGON;
        for (ExclusionZoneShape v : values()) {
            if (v.name().equalsIgnoreCase(s.trim())) return v;
        }
        return POLYGON;
    }
}
