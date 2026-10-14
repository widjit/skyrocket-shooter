package com.skiarkt.shooter.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.skiarkt.shooter.model.ExclusionZone;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ThreatCorridor;
import com.skiarkt.shooter.settings.UnitSystem;
import com.skiarkt.shooter.util.DtedHelper;
import com.skiarkt.shooter.util.ElevationSource;
import com.skiarkt.shooter.util.GeoUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure-geometry tests for the minimum safe intercept altitude calculation. */
public class InterceptAltitudeCalculatorTest {
    private static final double EPS = 1e-6;
    /** Ingress start; the test ingress line runs due east from here. */
    private static final GeoPos A = new GeoPos(35.0, -117.0);
    private static final GeoPos B = GeoUtils.destination(A, 90, 1000);

    private static final DtedHelper FLAT_100 = new DtedHelper(constant(100.0));
    private static final DtedHelper NO_DTED = new DtedHelper(constant(Double.NaN));

    private static ElevationSource constant(final double v) {
        return new ElevationSource() {
            @Override
            public double getElevationHae(double lat, double lon) {
                return v;
            }
        };
    }

    /** Position {@code along} m east of A and {@code north} m north (negative = south). */
    private static GeoPos at(double along, double north) {
        GeoPos p = GeoUtils.destination(A, 90, along);
        return north >= 0 ? GeoUtils.destination(p, 0, north) : GeoUtils.destination(p, 180, -north);
    }

    /** Rectangular footprint spanning [along0, along1] east and [south, north] relative to the line. */
    private static ExclusionZone rect(String label, double along0, double along1, double south, double north,
            double heightAgl) {
        ExclusionZone z = new ExclusionZone();
        z.label = label;
        z.heightAglMeters = heightAgl;
        z.points = new ArrayList<>(Arrays.asList(
                at(along0, south), at(along1, south), at(along1, north), at(along0, north)));
        return z;
    }

    @Test
    public void flatTerrainTallestZoneControls() {
        ExclusionZone a = rect("Bldg A", 200, 300, -50, 50, 30);
        ExclusionZone b = rect("Bldg B", 600, 700, -50, 50, 45);
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(FLAT_100, 10, 10)
                .computeForLine(Arrays.asList(a, b), A, B);

        assertTrue(r.hasConstraint());
        assertEquals(100 + 45 + 10, r.getMinInterceptAltitudeHae(), EPS);
        assertEquals(45 + 10, r.getMinInterceptAltitudeAgl(), EPS);
        assertEquals(145, r.getMaxRoofHae(), EPS);
        assertEquals(b.uid, r.getControllingZoneUid());
        assertEquals("Bldg B", r.getControllingZoneLabel());
        double critical = r.getCriticalSample().distanceMeters;
        assertTrue("critical at " + critical, critical >= 590 && critical <= 710);
        assertEquals(2, r.getConstrainingZoneUids().size());
        assertEquals(1000, r.getTotalLengthMeters(), 0.5);
        assertTrue(r.isTerrainComplete());
        assertEquals(101, r.getProfile().size());
    }

    @Test
    public void profileSegmentsAlternateClearAndConstrained() {
        ExclusionZone a = rect("A", 200, 300, -50, 50, 30);
        ExclusionZone b = rect("B", 600, 700, -50, 50, 45);
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(FLAT_100, 10, 10)
                .computeForLine(Arrays.asList(a, b), A, B);
        List<InterceptAltitudeResult.ConstraintSegment> segs = r.getSegments();
        assertEquals(5, segs.size());
        assertFalse(segs.get(0).isConstrained());
        assertEquals(a.uid, segs.get(1).zoneUid);
        assertEquals(140, segs.get(1).maxMinSafeHae, EPS);
        assertFalse(segs.get(2).isConstrained());
        assertEquals(b.uid, segs.get(3).zoneUid);
        assertEquals(155, segs.get(3).maxMinSafeHae, EPS);
        assertFalse(segs.get(4).isConstrained());
        // constraint extents are exact to within one sample spacing (conservative)
        assertEquals(200, segs.get(1).startMeters, 10.0 + 0.5);
        assertEquals(300, segs.get(1).endMeters, 10.0 + 0.5);
        assertTrue(segs.get(1).startMeters <= 200.5 && segs.get(1).endMeters >= 299.5);
        // per-sample values
        for (InterceptAltitudeResult.Sample s : r.getProfile()) {
            if (s.distanceMeters > 210 && s.distanceMeters < 290) {
                assertEquals(140, s.minSafeHae, EPS);
                assertEquals(40, s.minSafeAgl(), EPS);
            }
            if (s.distanceMeters > 320 && s.distanceMeters < 580) assertFalse(s.isConstrained());
        }
    }

    @Test
    public void noIntersectingZonesMeansNoConstraint() {
        ExclusionZone off = rect("Off-axis", 400, 500, 300, 400, 80);
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(FLAT_100)
                .computeForLine(Collections.singletonList(off), A, B);
        assertFalse(r.hasConstraint());
        assertTrue(Double.isNaN(r.getMinInterceptAltitudeHae()));
        assertTrue(Double.isNaN(r.getMinInterceptAltitudeAgl()));
        assertNull(r.getCriticalSample());
        assertEquals(1, r.getSegments().size());
        assertEquals("No exclusion zone constraint", r.formatAltitude(UnitSystem.METERS, 1));

        InterceptAltitudeResult empty = new InterceptAltitudeCalculator(FLAT_100).computeForLine(null, A, B);
        assertFalse(empty.hasConstraint());
    }

    @Test
    public void terrainUnderZoneRaisesRoof() {
        // 150 m hill beneath the first (shorter) building, 100 m elsewhere
        final GeoPos hill = at(250, 0);
        DtedHelper hilly = new DtedHelper(new ElevationSource() {
            @Override
            public double getElevationHae(double lat, double lon) {
                return GeoUtils.distanceMeters(new GeoPos(lat, lon), hill) < 120 ? 150 : 100;
            }
        });
        ExclusionZone onHill = rect("Hill bldg", 200, 300, -50, 50, 30);   // roof 180
        ExclusionZone valley = rect("Valley bldg", 600, 700, -50, 50, 45); // roof 145
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(hilly, 10, 10)
                .computeForLine(Arrays.asList(onHill, valley), A, B);
        assertEquals(onHill.uid, r.getControllingZoneUid());
        assertEquals(190, r.getMinInterceptAltitudeHae(), EPS);
        assertEquals(40, r.getMinInterceptAltitudeAgl(), EPS);
    }

    @Test
    public void slopingTerrainUsesHighestFootprintElevation() {
        // terrain rises 0.1 m per metre east: 100 m at A -> 200 m at B
        DtedHelper slope = new DtedHelper(new ElevationSource() {
            @Override
            public double getElevationHae(double lat, double lon) {
                double east = GeoUtils.distanceMeters(A, new GeoPos(A.lat, lon));
                return 100 + 0.1 * east;
            }
        });
        ExclusionZone z = rect("Sloped", 400, 500, -20, 20, 20);
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(slope, 10, 5)
                .computeForLine(Collections.singletonList(z), A, B);
        // highest footprint terrain ~150 m (east edge) + 20 m + 5 m buffer
        assertEquals(175, r.getMinInterceptAltitudeHae(), 0.5);
    }

    @Test
    public void zoneSmallerThanSampleSpacingIsNotSkipped() {
        // 4 m wide tower between samples at 500 and 550 (spacing 50 m)
        ExclusionZone tower = rect("Tower", 523, 527, -2, 2, 60);
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(FLAT_100, 50, 10)
                .computeForLine(Collections.singletonList(tower), A, B);
        assertTrue(r.hasConstraint());
        assertEquals(170, r.getMinInterceptAltitudeHae(), EPS);
        double d = r.getCriticalSample().distanceMeters;
        assertTrue(d >= 499 && d <= 551);
    }

    @Test
    public void corridorWidthCatchesOffsetZones() {
        ExclusionZone side = rect("Side", 400, 500, 60, 120, 50); // 60 m north of centreline
        List<ExclusionZone> zones = Collections.singletonList(side);
        InterceptAltitudeCalculator calc = new InterceptAltitudeCalculator(FLAT_100, 10, 10);

        assertFalse(calc.computeForLine(zones, A, B).hasConstraint());

        ThreatCorridor narrow = new ThreatCorridor();
        narrow.points = new ArrayList<>(Arrays.asList(A, B));
        narrow.widthMeters = 100; // half width 50 < 60
        assertFalse(calc.computeForCorridor(zones, narrow).hasConstraint());

        ThreatCorridor wide = new ThreatCorridor();
        wide.label = "Corridor North";
        wide.points = new ArrayList<>(Arrays.asList(A, B));
        wide.widthMeters = 200; // half width 100 > 60
        InterceptAltitudeResult r = calc.computeForCorridor(zones, wide);
        assertTrue(r.hasConstraint());
        assertEquals(160, r.getMinInterceptAltitudeHae(), EPS);
        assertEquals(wide.uid, r.getSourceUid());
        assertEquals("Corridor North", r.getSourceLabel());
        assertEquals(100, r.getHalfWidthMeters(), EPS);
    }

    @Test
    public void multiSegmentCorridorIsSampledAlongEveryLeg() {
        GeoPos corner = at(500, 0);
        GeoPos end = GeoUtils.destination(corner, 0, 500);
        ExclusionZone onSecondLeg = new ExclusionZone();
        onSecondLeg.heightAglMeters = 25;
        GeoPos c = GeoUtils.destination(corner, 0, 300);
        onSecondLeg.points = new ArrayList<>(Arrays.asList(
                GeoUtils.destination(GeoUtils.destination(c, 270, 20), 180, 20),
                GeoUtils.destination(GeoUtils.destination(c, 90, 20), 180, 20),
                GeoUtils.destination(GeoUtils.destination(c, 90, 20), 0, 20),
                GeoUtils.destination(GeoUtils.destination(c, 270, 20), 0, 20)));
        ThreatCorridor corridor = new ThreatCorridor();
        corridor.points = new ArrayList<>(Arrays.asList(A, corner, end));
        corridor.widthMeters = 1; // essentially a line
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(FLAT_100, 10, 10)
                .computeForCorridor(Collections.singletonList(onSecondLeg), corridor);
        assertEquals(1000, r.getTotalLengthMeters(), 1);
        assertEquals(135, r.getMinInterceptAltitudeHae(), EPS);
        double d = r.getCriticalSample().distanceMeters;
        assertTrue("critical at " + d, d >= 769.5 && d <= 830.5);
        // no duplicated corner sample
        List<InterceptAltitudeResult.Sample> p = r.getProfile();
        for (int i = 1; i < p.size(); i++) assertTrue(p.get(i).distanceMeters > p.get(i - 1).distanceMeters);
    }

    @Test
    public void overlappingZonesTakeHighestRoof() {
        ExclusionZone low = rect("Low", 300, 700, -100, 100, 20);
        ExclusionZone high = rect("High", 450, 550, -10, 10, 70);
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(FLAT_100, 10, 10)
                .computeForLine(Arrays.asList(low, high), A, B);
        assertEquals(180, r.getMinInterceptAltitudeHae(), EPS);
        assertEquals(high.uid, r.getControllingZoneUid());
        for (InterceptAltitudeResult.Sample s : r.getProfile()) {
            if (s.distanceMeters > 320 && s.distanceMeters < 430) assertEquals(low.uid, s.zoneUid);
            if (s.distanceMeters > 460 && s.distanceMeters < 540) assertEquals(high.uid, s.zoneUid);
        }
    }

    @Test
    public void safetyBufferIsConfigurable() {
        List<ExclusionZone> zones = Collections.singletonList(rect("Z", 400, 500, -50, 50, 40));
        assertEquals(140, new InterceptAltitudeCalculator(FLAT_100, 10, 0)
                .computeForLine(zones, A, B).getMinInterceptAltitudeHae(), EPS);
        assertEquals(165, new InterceptAltitudeCalculator(FLAT_100, 10, 25)
                .computeForLine(zones, A, B).getMinInterceptAltitudeHae(), EPS);
        // default buffer is 10 m
        assertEquals(InterceptAltitudeCalculator.DEFAULT_SAFETY_BUFFER_M, 10.0, 0);
        assertEquals(150, new InterceptAltitudeCalculator(FLAT_100)
                .computeForLine(zones, A, B).getMinInterceptAltitudeHae(), EPS);
    }

    @Test
    public void missingTerrainFallsBackToZeroAndFlagsIncomplete() {
        List<ExclusionZone> zones = Collections.singletonList(rect("Z", 400, 500, -50, 50, 40));
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(NO_DTED, 10, 10).computeForLine(zones, A, B);
        assertFalse(r.isTerrainComplete());
        assertEquals(50, r.getMinInterceptAltitudeHae(), EPS);
        assertTrue(Double.isNaN(r.getMinInterceptAltitudeAgl()));
        assertTrue(r.format(UnitSystem.METERS, 1).contains("WARNING"));
    }

    @Test
    public void invalidZonesAreIgnored() {
        ExclusionZone degenerate = new ExclusionZone();
        degenerate.points = new ArrayList<>(Arrays.asList(at(400, 0), at(500, 0)));
        ExclusionZone negative = rect("Neg", 400, 500, -50, 50, -5);
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(FLAT_100)
                .computeForLine(Arrays.asList(degenerate, null, negative), A, B);
        assertFalse(r.hasConstraint());
    }

    @Test
    public void sampleCountIsCappedForLongPaths() {
        GeoPos far = GeoUtils.destination(A, 90, 200_000);
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(FLAT_100, 1, 10)
                .computeForLine(Collections.<ExclusionZone>emptyList(), A, far);
        assertEquals(InterceptAltitudeCalculator.MAX_SAMPLES, r.getProfile().size());
        assertTrue(r.getSampleSpacingMeters() > 1);
    }

    @Test
    public void formattingUsesRequestedUnitsAndPrecision() {
        List<ExclusionZone> zones = Collections.singletonList(rect("Hangar", 400, 500, -50, 50, 40));
        InterceptAltitudeResult r = new InterceptAltitudeCalculator(FLAT_100, 10, 10).computeForLine(zones, A, B);
        // 150 m HAE = 492.1 ft, 50 m AGL = 164.0 ft
        assertEquals("492.1 ft HAE (164.0 ft AGL)", r.formatAltitude(UnitSystem.FEET, 1));
        assertEquals("150.0 m HAE (50.0 m AGL)", r.formatAltitude(UnitSystem.METERS, 1));
        assertEquals("0.15 km HAE (0.05 km AGL)", r.formatAltitude(UnitSystem.KILOMETERS, 2));
        String summary = r.format(UnitSystem.METERS, 1);
        assertTrue(summary, summary.contains("Controlling zone: Hangar"));
        String seg = InterceptAltitudeResult.formatSegment(r.getSegments().get(1), UnitSystem.METERS, 0);
        assertTrue(seg, seg.contains("Hangar") && seg.contains("150 m HAE"));
    }

    @Test
    public void geoPointConvenienceMatchesGeoPosApi() {
        List<ExclusionZone> zones = Collections.singletonList(rect("Z", 400, 500, -50, 50, 40));
        InterceptAltitudeResult r = InterceptAltitudeCalculator.compute(zones,
                new GeoPoint(A.lat, A.lon), new GeoPoint(B.lat, B.lon), FLAT_100, 10);
        assertEquals(150, r.getMinInterceptAltitudeHae(), EPS);
    }

    @Test
    public void rejectsInvalidArguments() {
        expectIae(new Runnable() {
            @Override
            public void run() {
                new InterceptAltitudeCalculator(FLAT_100, 0, 10);
            }
        });
        expectIae(new Runnable() {
            @Override
            public void run() {
                new InterceptAltitudeCalculator(FLAT_100, 10, -1);
            }
        });
        expectIae(new Runnable() {
            @Override
            public void run() {
                new InterceptAltitudeCalculator(null, 10, 10);
            }
        });
        expectIae(new Runnable() {
            @Override
            public void run() {
                new InterceptAltitudeCalculator(FLAT_100).compute(null,
                        Collections.singletonList(A), 0, null, null);
            }
        });
        expectIae(new Runnable() {
            @Override
            public void run() {
                new InterceptAltitudeCalculator(FLAT_100).computeForLine(null, A, new GeoPos(Double.NaN, 0));
            }
        });
    }

    @Test
    public void planarHelpers() {
        double[] o = {0, 0}, e = {10, 0}, n = {5, -5}, s = {5, 5};
        assertTrue(InterceptAltitudeCalculator.segmentsIntersect(o, e, n, s));
        assertEquals(0, InterceptAltitudeCalculator.segSegDistance(o, e, n, s), EPS);
        assertEquals(3, InterceptAltitudeCalculator.segSegDistance(o, e, new double[] {2, 3},
                new double[] {8, 3}), EPS);
        assertEquals(5, InterceptAltitudeCalculator.pointSegDistance(new double[] {-3, 4}, o, e), EPS);
    }

    private static void expectIae(Runnable r) {
        try {
            r.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
