package com.skiarkt.shooter.model;

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
        DetonationMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Lenient lookup by constant name; {@code dflt} when null / unknown. */
    public static DetonationMode fromName(String s, DetonationMode dflt) {
        if (s == null) return dflt;
        for (DetonationMode v : values()) {
            if (v.name().equalsIgnoreCase(s.trim())) return v;
        }
        return dflt;
    }
}
