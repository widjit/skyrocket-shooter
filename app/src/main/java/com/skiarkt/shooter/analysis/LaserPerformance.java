package com.skiarkt.shooter.analysis;

import com.skiarkt.shooter.model.KineticEffector;

/**
 * Planning-grade directed-energy (laser) performance model (R7).
 *
 * <p>The beam is assumed focused on the target. The 1/e² spot radius at slant range R combines
 * diffraction ({@code M² λ R / (π w0)}, w0 = aperture radius) and platform jitter
 * ({@code jitter · R}) in quadrature. Average power density over the spot is
 * {@code P · T(R) / (π w²)} with atmospheric transmission {@code T = exp(-α R)}. Fluence
 * delivered during the dwell is power density × dwell. The effective range is the furthest range
 * at which that fluence still meets the damage threshold.
 *
 * <p>Laser power is optional. When power or aperture is unknown the operator-entered qualitative
 * effective range is used instead (never blocks), falling back to the max engagement range.
 * This is an estimate for planning only; it ignores turbulence, thermal blooming and target
 * aspect.
 */
public final class LaserPerformance {
    /** Wavelength (m) - 1.07 µm fiber laser, typical counter-UAS class. */
    public static final double WAVELENGTH_M = 1.07e-6;
    /** Beam quality factor. */
    public static final double BEAM_QUALITY_M2 = 1.5;
    /** Residual pointing jitter (radians). */
    public static final double JITTER_RAD = 5e-6;
    /** Clear-air extinction coefficient (1/m) = 0.2 / km. */
    public static final double EXTINCTION_PER_M = 0.2e-3;
    /** Upper bound for the effective-range search (m). */
    public static final double MAX_SEARCH_M = 100_000.0;

    /** How the effective range was obtained. */
    public enum Source { COMPUTED, QUALITATIVE, MAX_RANGE }

    private LaserPerformance() {
    }

    /** True when power (kW) and aperture (cm) are both usable for the physics model. */
    public static boolean canCompute(Double powerKw, double apertureCm) {
        return powerKw != null && powerKw > 0 && apertureCm > 0 && !Double.isInfinite(powerKw);
    }

    /** 1/e² spot radius (cm) at {@code rangeM}. */
    public static double spotRadiusCm(double apertureCm, double rangeM) {
        double w0 = Math.max(1e-6, apertureCm / 200.0); // radius, m
        double diff = BEAM_QUALITY_M2 * WAVELENGTH_M * rangeM / (Math.PI * w0);
        double jit = JITTER_RAD * rangeM;
        double w = Math.sqrt(diff * diff + jit * jit);
        // Never smaller than a diffraction-limited spot at 1 m (avoids divide-by-zero at R=0).
        return Math.max(w, 1e-4) * 100.0;
    }

    /** Average power density (W/cm²) on target at {@code rangeM}; NaN when not computable. */
    public static double powerDensityWPerCm2(Double powerKw, double apertureCm, double rangeM) {
        if (!canCompute(powerKw, apertureCm) || rangeM < 0) return Double.NaN;
        double w = spotRadiusCm(apertureCm, Math.max(1.0, rangeM));
        double t = Math.exp(-EXTINCTION_PER_M * rangeM);
        return powerKw * 1000.0 * t / (Math.PI * w * w);
    }

    /** Fluence (J/cm²) delivered during {@code dwellSec} at {@code rangeM}; NaN when not computable. */
    public static double fluenceJPerCm2(Double powerKw, double apertureCm, double dwellSec, double rangeM) {
        double pd = powerDensityWPerCm2(powerKw, apertureCm, rangeM);
        return Double.isNaN(pd) ? Double.NaN : pd * Math.max(0.0, dwellSec);
    }

    /**
     * Furthest range (m) at which fluence ≥ {@code thresholdJcm2}; NaN when not computable,
     * 0 when even 1 m is insufficient. Fluence decreases monotonically with range, so bisection.
     */
    public static double computedRangeM(Double powerKw, double apertureCm, double dwellSec, double thresholdJcm2) {
        if (!canCompute(powerKw, apertureCm) || !(dwellSec > 0) || !(thresholdJcm2 > 0)) return Double.NaN;
        if (fluenceJPerCm2(powerKw, apertureCm, dwellSec, 1.0) < thresholdJcm2) return 0.0;
        if (fluenceJPerCm2(powerKw, apertureCm, dwellSec, MAX_SEARCH_M) >= thresholdJcm2) return MAX_SEARCH_M;
        double lo = 1.0, hi = MAX_SEARCH_M;
        for (int i = 0; i < 60 && hi - lo > 0.5; i++) {
            double mid = 0.5 * (lo + hi);
            if (fluenceJPerCm2(powerKw, apertureCm, dwellSec, mid) >= thresholdJcm2) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    /** Which input drives the effective range of {@code k}. */
    public static Source source(KineticEffector k) {
        if (canCompute(k.laserPowerKw, k.apertureDiameterCm) && k.beamDwellSec > 0 && k.damageFluenceJPerCm2 > 0) {
            return Source.COMPUTED;
        }
        if (k.qualitativeEffectiveRangeM != null && k.qualitativeEffectiveRangeM > 0) return Source.QUALITATIVE;
        return Source.MAX_RANGE;
    }

    /** Effective range (m) of a directed-energy effector, capped at its max engagement range. */
    public static double effectiveRangeM(KineticEffector k) {
        double max = Math.max(0.0, k.maxEngagementDistMeters);
        switch (source(k)) {
            case COMPUTED:
                return Math.min(max, computedRangeM(k.laserPowerKw, k.apertureDiameterCm, k.beamDwellSec,
                        k.damageFluenceJPerCm2));
            case QUALITATIVE:
                return Math.min(max, k.qualitativeEffectiveRangeM);
            default:
                return max;
        }
    }

    /**
     * Relative beam intensity in [0,1] at {@code rangeM} for the overlay gradient: fluence relative
     * to the threshold (clamped) when computable, else a linear fall-off to the effective range.
     */
    public static double relativeIntensity(KineticEffector k, double rangeM) {
        if (source(k) == Source.COMPUTED) {
            double f = fluenceJPerCm2(k.laserPowerKw, k.apertureDiameterCm, k.beamDwellSec, rangeM);
            if (Double.isNaN(f)) return 0.0;
            // log scale: 1 at >= 10x threshold, 0.5 at threshold, 0 at <= 0.1x threshold.
            double r = Math.log10(f / k.damageFluenceJPerCm2);
            return Math.max(0.0, Math.min(1.0, 0.5 + r / 2.0));
        }
        double eff = effectiveRangeM(k);
        if (!(eff > 0)) return 0.0;
        return Math.max(0.0, Math.min(1.0, 1.0 - 0.5 * rangeM / eff));
    }
}
