package com.skiarkt.shooter.model;

import com.skiarkt.shooter.util.EnumUtils;

/** Operational status of an effector. */
public enum EffectorStatus {
    READY("Ready"),
    OFFLINE("Offline"),
    ENGAGED("Engaged"),
    MAINTENANCE("Maintenance");

    public final String displayName;

    EffectorStatus(String displayName) {
        this.displayName = displayName;
    }

    /** Next constant (wraps) - used by cycling UI buttons. */
    public EffectorStatus next() {
        return EnumUtils.next(this);
    }

    /** Lenient lookup by constant name; {@code dflt} when null / unknown. */
    public static EffectorStatus fromName(String s, EffectorStatus dflt) {
        return EnumUtils.fromName(EffectorStatus.class, s, dflt);
    }
}
