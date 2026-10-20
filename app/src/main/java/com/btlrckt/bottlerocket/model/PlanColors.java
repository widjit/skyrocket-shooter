package com.btlrckt.bottlerocket.model;

/**
 * Default ARGB colors for plan items (plain ints so models stay free of Android
 * framework calls and are unit-testable). User overrides live in SettingsManager.
 */
public final class PlanColors {
    private PlanColors() {
    }

    public static final int EXCLUSION_ZONE = 0xFFFF8C00;   // orange
    public static final int SENSOR = 0xFF00BFFF;           // deep sky blue
    public static final int NKE_HEATMAP = 0xFFAA00FF;      // purple
    public static final int KE_NO_SHOOT = 0xFFFF0000;      // red
    public static final int KE_FRAG_ZONE = 0xFFFFD700;     // gold
    public static final int KE_CLEAR_ZONE = 0xFF00C853;    // green
    public static final int DEFENDED_FILL = 0x400000FF;    // translucent blue
    public static final int DEFENDED_BORDER = 0xFF0000FF;  // blue
    public static final int THREAT_CORRIDOR = 0xFFD50000;  // dark red
    /** RF heat map gradient defaults. */
    public static final int RF_EFFECTIVE = 0xFF00E676;     // green
    public static final int RF_MARGINAL = 0xFFFFEB3B;      // yellow
    public static final int RF_INSUFFICIENT = 0xFFFF1744;  // red

    /** Linear ARGB interpolation, t in 0..1 (clamped). */
    public static int lerp(int a, int b, double t) {
        double f = Math.max(0.0, Math.min(1.0, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= ((int) Math.round(ca + (cb - ca) * f) & 0xFF) << shift;
        }
        return out;
    }

    /** Replaces the alpha channel of {@code argb} with {@code alpha01} (0..1). */
    public static int withAlpha(int argb, float alpha01) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha01)) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static float alphaOf(int argb) {
        return ((argb >>> 24) & 0xFF) / 255f;
    }
}
