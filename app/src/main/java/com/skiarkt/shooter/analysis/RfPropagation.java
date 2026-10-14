package com.skiarkt.shooter.analysis;

import com.skiarkt.shooter.model.PlanColors;

/**
 * Free-space RF link math for jammer effectiveness (pure Java, unit-testable).
 *
 * <pre>
 *   FSPL(dB)  = 20 log10(d_m) + 20 log10(f_MHz) - 27.55
 *   rx (dBm)  = P_tx - FSPL            (i.e. P - 20log10(d) - 20log10(f) + 27.55)
 * </pre>
 *
 * Note on the sign of the 27.55 dB constant: the planning brief wrote
 * {@code rx = P - 20log10(d) - 20log10(f) - 27.55}; the physically correct free-space
 * constant for metres + MHz is FSPL = 20log10(d) + 20log10(f) <b>- 27.55</b>
 * (e.g. 1 km @ 2.4 GHz = 100.05 dB). This class uses the standard (correct) form, so the
 * received power is 55.1 dB higher than the literal brief formula would give. When no
 * terrain line of sight exists the configurable terrain loss (default 40 dB) is subtracted.
 */
public final class RfPropagation {
    /** FSPL constant for d in metres and f in MHz. */
    public static final double FSPL_CONSTANT_DB = 27.55;
    /** Width of the "marginal" band below the jam threshold (dB). */
    public static final double MARGINAL_BAND_DB = 10.0;
    /** Distances below this are clamped (near-field; avoids log10(0)). */
    public static final double MIN_DISTANCE_M = 1.0;

    public enum Effectiveness { EFFECTIVE, MARGINAL, INSUFFICIENT }

    private RfPropagation() {
    }

    /** Free-space path loss in dB (distance clamped to {@link #MIN_DISTANCE_M}). */
    public static double fsplDb(double distanceM, double frequencyMhz) {
        if (!(frequencyMhz > 0)) throw new IllegalArgumentException("frequency must be > 0 MHz");
        double d = Math.max(MIN_DISTANCE_M, distanceM);
        return 20.0 * Math.log10(d) + 20.0 * Math.log10(frequencyMhz) - FSPL_CONSTANT_DB;
    }

    /** Received power (dBm) at {@code distanceM} for transmit power / EIRP {@code txDbm}. */
    public static double receivedPowerDbm(double txDbm, double distanceM, double frequencyMhz) {
        return txDbm - fsplDb(distanceM, frequencyMhz);
    }

    /** Received power including the terrain penalty when the path is masked. */
    public static double receivedPowerDbm(double txDbm, double distanceM, double frequencyMhz,
            boolean lineOfSight, double terrainLossDb) {
        double rx = receivedPowerDbm(txDbm, distanceM, frequencyMhz);
        return lineOfSight ? rx : rx - Math.max(0.0, terrainLossDb);
    }

    /** Range (m) at which free-space rx falls to {@code thresholdDbm}. */
    public static double rangeForThresholdM(double txDbm, double thresholdDbm, double frequencyMhz) {
        double exp = (txDbm - thresholdDbm - 20.0 * Math.log10(frequencyMhz) + FSPL_CONSTANT_DB) / 20.0;
        return Math.pow(10.0, exp);
    }

    public static Effectiveness classify(double rxDbm, double thresholdDbm) {
        if (rxDbm >= thresholdDbm) return Effectiveness.EFFECTIVE;
        if (rxDbm >= thresholdDbm - MARGINAL_BAND_DB) return Effectiveness.MARGINAL;
        return Effectiveness.INSUFFICIENT;
    }

    /**
     * Position within the gradient: 1 at/above threshold, 0 at/below threshold - 10 dB,
     * linear in between.
     */
    public static double gradientFraction(double rxDbm, double thresholdDbm) {
        if (rxDbm >= thresholdDbm) return 1.0;
        double t = (rxDbm - (thresholdDbm - MARGINAL_BAND_DB)) / MARGINAL_BAND_DB;
        return Math.max(0.0, Math.min(1.0, t));
    }

    /**
     * Opaque display color: effective at/above threshold, insufficient more than 10 dB below,
     * and in the marginal band a two-segment gradient insufficient -> marginal -> effective
     * (marginal color at threshold - 5 dB).
     */
    public static int colorFor(double rxDbm, double thresholdDbm, int effective, int marginal, int insufficient) {
        return colorForFraction(gradientFraction(rxDbm, thresholdDbm), effective, marginal, insufficient);
    }

    public static int colorForFraction(double t, int effective, int marginal, int insufficient) {
        if (t >= 1.0) return effective | 0xFF000000;
        if (t <= 0.0) return insufficient | 0xFF000000;
        int c = t < 0.5 ? PlanColors.lerp(insufficient, marginal, t * 2.0)
                : PlanColors.lerp(marginal, effective, (t - 0.5) * 2.0);
        return c | 0xFF000000;
    }

    /**
     * Quantized color band for rendering: 0 = insufficient, {@code steps + 1} = effective,
     * 1..steps = marginal-band gradient steps (lowest to highest).
     */
    public static int band(double rxDbm, double thresholdDbm, int steps) {
        if (rxDbm >= thresholdDbm) return steps + 1;
        if (rxDbm < thresholdDbm - MARGINAL_BAND_DB) return 0;
        double t = gradientFraction(rxDbm, thresholdDbm);
        return 1 + Math.min(steps - 1, (int) Math.floor(t * steps));
    }

    /** Opaque palette matching {@link #band}; index = band. */
    public static int[] palette(int steps, int effective, int marginal, int insufficient) {
        int[] p = new int[steps + 2];
        p[0] = insufficient | 0xFF000000;
        p[steps + 1] = effective | 0xFF000000;
        for (int i = 1; i <= steps; i++) {
            double t = (i - 0.5) / steps; // band centre
            p[i] = colorForFraction(t, effective, marginal, insufficient);
        }
        return p;
    }
}
