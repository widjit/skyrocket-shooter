package com.skiarkt.shooter.model;

/** Kinetic effector weapon class. */
public enum KineticEffectorType {
    RIFLE("Rifle"),
    SHOTGUN("Shotgun"),
    NET_GUN("Net Gun"),
    ROCKET("Rocket"),
    LASER_GUIDED_ROCKET("Laser-Guided Rocket"),
    DRONE_INTERCEPTOR("Drone Interceptor");

    public final String displayName;

    KineticEffectorType(String displayName) {
        this.displayName = displayName;
    }

    /** Next constant (wraps) - used by cycling UI buttons. */
    public KineticEffectorType next() {
        KineticEffectorType[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Lenient lookup by constant name; {@code dflt} when null / unknown. */
    public static KineticEffectorType fromName(String s, KineticEffectorType dflt) {
        if (s == null) return dflt;
        for (KineticEffectorType v : values()) {
            if (v.name().equalsIgnoreCase(s.trim())) return v;
        }
        return dflt;
    }
}
