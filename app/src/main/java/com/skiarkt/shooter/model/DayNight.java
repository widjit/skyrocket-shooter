package com.skiarkt.shooter.model;

/** R2: day / night capability of an imaging sensor (camera, EO/IR, IR/thermal). */
public enum DayNight {
    DAY("Day"),
    NIGHT("Night"),
    DAY_NIGHT("Day + night");

    public final String displayName;

    DayNight(String displayName) {
        this.displayName = displayName;
    }

    public static DayNight fromName(String name, DayNight fallback) {
        if (name == null) return fallback;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
