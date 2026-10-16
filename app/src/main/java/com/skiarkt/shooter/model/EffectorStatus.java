package com.skiarkt.shooter.model;

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
        EffectorStatus[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Lenient lookup by constant name; {@code dflt} when null / unknown. */
    public static EffectorStatus fromName(String s, EffectorStatus dflt) {
        if (s == null) return dflt;
        for (EffectorStatus v : values()) {
            if (v.name().equalsIgnoreCase(s.trim())) return v;
        }
        return dflt;
    }
}
