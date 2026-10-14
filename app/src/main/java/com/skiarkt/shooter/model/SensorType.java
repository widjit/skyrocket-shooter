package com.skiarkt.shooter.model;

/** Sensor categories supported by the planner. */
public enum SensorType {
    RADAR("Radar"),
    CAMERA("Camera"),
    EOIR("EO/IR");

    public final String displayName;

    SensorType(String displayName) {
        this.displayName = displayName;
    }

    public static SensorType fromName(String name, SensorType fallback) {
        if (name == null) return fallback;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
