package com.skiarkt.shooter.model;

import com.skiarkt.shooter.util.EnumUtils;

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
        return EnumUtils.next(this);
    }

    /** Lenient lookup by constant name; {@code dflt} when null / unknown. */
    public static MunitionType fromName(String s, MunitionType dflt) {
        return EnumUtils.fromName(MunitionType.class, s, dflt);
    }
}
