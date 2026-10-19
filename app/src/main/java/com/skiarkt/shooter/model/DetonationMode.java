package com.skiarkt.shooter.model;

import com.skiarkt.shooter.util.EnumUtils;

/** Munition fuzing / detonation mode. */
public enum DetonationMode {
    PROXIMITY("Proximity"),
    IMPACT("Impact"),
    COMMAND("Command");

    public final String displayName;

    DetonationMode(String displayName) {
        this.displayName = displayName;
    }

    /** Next constant (wraps) - used by cycling UI buttons. */
    public DetonationMode next() {
        return EnumUtils.next(this);
    }

    /** Lenient lookup by constant name; {@code dflt} when null / unknown. */
    public static DetonationMode fromName(String s, DetonationMode dflt) {
        return EnumUtils.fromName(DetonationMode.class, s, dflt);
    }
}
