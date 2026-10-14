package com.skiarkt.shooter.model;

/** Every plannable object type in a {@link PointDefensePlan}. */
public enum ItemKind {
    EXCLUSION_ZONE("exczone", "Exclusion Zone"),
    SENSOR("sensor", "Sensor"),
    NON_KINETIC_EFFECTOR("nke", "Non-Kinetic Effector"),
    KINETIC_EFFECTOR("ke", "Kinetic Effector"),
    DEFENDED_ASSET("defasset", "Defended Asset"),
    THREAT_CORRIDOR("corridor", "Threat Corridor");

    /** Short code used in CoT types and file names. */
    public final String code;
    public final String displayName;

    ItemKind(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public static ItemKind fromCode(String code) {
        for (ItemKind k : values()) {
            if (k.code.equals(code)) return k;
        }
        return null;
    }
}
