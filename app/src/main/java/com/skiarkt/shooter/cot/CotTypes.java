package com.skiarkt.shooter.cot;

import com.skiarkt.shooter.model.ItemKind;

/** CoT type strings and detail element names used by SKY ROCKET SHOOTER. */
public final class CotTypes {
    private CotTypes() {
    }

    public static final String PREFIX = "b-m-p-skiarkt-";
    public static final String EXCLUSION_ZONE = PREFIX + ItemKind.EXCLUSION_ZONE.code;
    public static final String SENSOR = PREFIX + ItemKind.SENSOR.code;
    public static final String NON_KINETIC_EFFECTOR = PREFIX + ItemKind.NON_KINETIC_EFFECTOR.code;
    public static final String KINETIC_EFFECTOR = PREFIX + ItemKind.KINETIC_EFFECTOR.code;
    public static final String DEFENDED_ASSET = PREFIX + ItemKind.DEFENDED_ASSET.code;
    public static final String THREAT_CORRIDOR = PREFIX + ItemKind.THREAT_CORRIDOR.code;
    public static final String AUTHORITY_ZONE = PREFIX + ItemKind.AUTHORITY_ZONE.code;
    public static final String PLAN = PREFIX + "plan";

    /** "h-g-i-g-o": human generated, GIGO (manually placed planning object). */
    public static final String HOW = "h-g-i-g-o";

    /** Custom detail element carrying SKIARKT-specific attributes. */
    public static final String DETAIL_SKIARKT = "__skiarkt";
    public static final String DETAIL_CONTACT = "contact";
    public static final String DETAIL_REMARKS = "remarks";
    public static final String DETAIL_LINK = "link";
    public static final String DETAIL_STROKE_COLOR = "strokeColor";
    public static final String DETAIL_FILL_COLOR = "fillColor";
    public static final int SCHEMA_VERSION = 1;

    /** Sentinel used by CoT for unknown hae / ce / le. */
    public static final double COT_UNKNOWN = 9999999.0;

    public static String forKind(ItemKind kind) {
        return PREFIX + kind.code;
    }

    /** @return the item kind for a CoT type, or null if not a SKIARKT item type. */
    public static ItemKind kindForType(String type) {
        if (type == null || !type.startsWith(PREFIX)) return null;
        if (KineticEffectorCot.COT_TYPE.equals(type)) return ItemKind.KINETIC_EFFECTOR;
        return ItemKind.fromCode(type.substring(PREFIX.length()));
    }

    public static boolean isSkiarktType(String type) {
        return type != null && type.startsWith(PREFIX);
    }
}
