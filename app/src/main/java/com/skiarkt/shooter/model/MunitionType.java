package com.skiarkt.shooter.model;

/** Munition payload of a kinetic effector. */
public enum MunitionType {
    HE("HE"),
    FLECHETTE("Flechette"),
    NET("Net"),
    KINETIC_IMPACTOR("Kinetic Impactor"),
    LASER_SPOT("Laser Spot");

    public final String displayName;

    MunitionType(String displayName) {
        this.displayName = displayName;
    }

    /** Next constant (wraps) - used by cycling UI buttons. */
    public MunitionType next() {
        MunitionType[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Lenient lookup by constant name; {@code dflt} when null / unknown. */
    public static MunitionType fromName(String s, MunitionType dflt) {
        if (s == null) return dflt;
        for (MunitionType v : values()) {
            if (v.name().equalsIgnoreCase(s.trim())) return v;
        }
        return dflt;
    }
}
