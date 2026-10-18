package com.skiarkt.shooter.model;

/** Engagement-authority perimeter classes (ROADMAP R6). */
public enum AuthorityType {
    /** RF / non-kinetic effectors may engage inside this zone. */
    NON_KINETIC("Non-kinetic authority", "NK AUTH", 0xFF00E5FF),
    /** Kinetic effectors may engage inside this zone. */
    KINETIC("Kinetic authority", "KINETIC AUTH", 0xFFFF6D00),
    /** Weapons hold: no engagement of any class inside this zone. */
    NON_HOSTILE("Non-hostile (weapons hold)", "WEAPONS HOLD", 0xFF00E676);

    public final String displayName;
    /** Short map callout tag. */
    public final String shortTag;
    public final int defaultColor;

    AuthorityType(String displayName, String shortTag, int defaultColor) {
        this.displayName = displayName;
        this.shortTag = shortTag;
        this.defaultColor = defaultColor;
    }

    public AuthorityType next() {
        AuthorityType[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static AuthorityType fromName(String name, AuthorityType fallback) {
        if (name == null) return fallback;
        for (AuthorityType t : values()) {
            if (t.name().equalsIgnoreCase(name.trim())) return t;
        }
        return fallback;
    }
}
