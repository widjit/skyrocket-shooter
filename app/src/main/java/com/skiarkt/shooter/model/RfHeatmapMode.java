package com.skiarkt.shooter.model;

/**
 * How a multi-band jammer's per-band coverage is combined into one heat map (R1). Per-band
 * display is done by toggling {@link RfBand#enabled}; with one band enabled both modes show
 * exactly that band.
 */
public enum RfHeatmapMode {
    /** A cell is as good as the best enabled band there (any band defeats the link). */
    UNION("Combined: union (best band)"),
    /** A cell is only as good as the weakest enabled band (all bands must be defeated). */
    WEAKEST("Combined: weakest band");

    public final String displayName;

    RfHeatmapMode(String displayName) {
        this.displayName = displayName;
    }

    public static RfHeatmapMode parse(String s) {
        if (s != null) {
            for (RfHeatmapMode m : values()) if (m.name().equalsIgnoreCase(s.trim())) return m;
        }
        return UNION;
    }
}
