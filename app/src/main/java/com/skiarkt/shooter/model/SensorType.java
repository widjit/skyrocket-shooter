package com.skiarkt.shooter.model;

/** Sensor categories supported by the planner. */
public enum SensorType {
    RADAR("Radar"),
    CAMERA("Camera"),
    EOIR("EO/IR"),
    /** R2: thermal imager (passive IR). */
    IR("IR/Thermal"),
    /** R2: laser designator / rangefinder (cues laser-guided effectors). */
    LASER_DESIGNATOR("Laser Designator");

    public final String displayName;

    SensorType(String displayName) {
        this.displayName = displayName;
    }

    /** True for imaging sensors that have a day / night capability. */
    public boolean isImaging() {
        return this == CAMERA || this == EOIR || this == IR;
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
