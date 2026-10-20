package com.btlrckt.bottlerocket.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.btlrckt.bottlerocket.model.KineticEffector;
import com.btlrckt.bottlerocket.model.KineticEffectorType;

import org.junit.Test;

public class LaserPerformanceTest {

    private static KineticEffector laser() {
        KineticEffector k = new KineticEffector();
        k.type = KineticEffectorType.DIRECTED_ENERGY;
        k.maxEngagementDistMeters = 50_000;
        return k;
    }

    @Test
    public void fluenceDecreasesWithRange() {
        double prev = Double.MAX_VALUE;
        for (double r = 10; r <= 10_000; r *= 2) {
            double f = LaserPerformance.fluenceJPerCm2(10.0, 30, 3, r);
            assertTrue(f < prev);
            prev = f;
        }
    }

    @Test
    public void computedRangeMeetsThresholdAndScalesWithPower() {
        double r10 = LaserPerformance.computedRangeM(10.0, 30, 3, 1000);
        double r50 = LaserPerformance.computedRangeM(50.0, 30, 3, 1000);
        assertTrue(r10 > 0);
        assertTrue(r50 > r10);
        assertTrue(LaserPerformance.fluenceJPerCm2(10.0, 30, 3, r10) >= 1000 * 0.999);
        assertTrue(LaserPerformance.fluenceJPerCm2(10.0, 30, 3, r10 + 5) < 1000);
    }

    @Test
    public void unknownPowerFallsBackToQualitativeThenMaxRange() {
        KineticEffector k = laser();
        assertFalse(LaserPerformance.canCompute(null, 30));
        assertEquals(LaserPerformance.Source.MAX_RANGE, LaserPerformance.source(k));
        assertEquals(50_000, LaserPerformance.effectiveRangeM(k), 1e-9);
        k.qualitativeEffectiveRangeM = 1200.0;
        assertEquals(LaserPerformance.Source.QUALITATIVE, LaserPerformance.source(k));
        assertEquals(1200, LaserPerformance.effectiveRangeM(k), 1e-9);
        k.laserPowerKw = 10.0;
        assertEquals(LaserPerformance.Source.COMPUTED, LaserPerformance.source(k));
    }

    @Test
    public void effectiveRangeIsCappedAtMaxEngagementRange() {
        KineticEffector k = laser();
        k.laserPowerKw = 300.0;
        k.maxEngagementDistMeters = 800;
        assertEquals(800, LaserPerformance.effectiveRangeM(k), 1e-9);
        k.laserPowerKw = null;
        k.qualitativeEffectiveRangeM = 5000.0;
        assertEquals(800, LaserPerformance.effectiveRangeM(k), 1e-9);
    }

    @Test
    public void relativeIntensityIsBoundedAndFalling() {
        KineticEffector k = laser();
        k.laserPowerKw = 10.0;
        double eff = LaserPerformance.effectiveRangeM(k);
        assertEquals(0.5, LaserPerformance.relativeIntensity(k, eff), 0.01);
        assertTrue(LaserPerformance.relativeIntensity(k, 10) <= 1.0);
        assertTrue(LaserPerformance.relativeIntensity(k, eff * 3) < 0.5);
        assertTrue(LaserPerformance.relativeIntensity(k, 1e6) >= 0.0);
    }
}
