package com.btlrckt.bottlerocket.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.btlrckt.bottlerocket.model.GeoPos;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class GeoAndDtedTest {
    private static final GeoPos A = new GeoPos(35.0, -117.0);

    @Test
    public void distanceBearingDestinationRoundTrip() {
        GeoPos b = GeoUtils.destination(A, 45.0, 5000.0);
        assertEquals(5000.0, GeoUtils.distanceMeters(A, b), 0.5);
        assertEquals(45.0, GeoUtils.bearingDeg(A, b), 0.05);
        // one degree of latitude ~ 111.2 km
        assertEquals(111195, GeoUtils.distanceMeters(new GeoPos(0, 0), new GeoPos(1, 0)), 50);
    }

    @Test
    public void polygonHelpers() {
        List<GeoPos> square = Arrays.asList(
                GeoUtils.destination(A, 0, 0), GeoUtils.destination(A, 90, 100),
                GeoUtils.destination(GeoUtils.destination(A, 90, 100), 0, 100), GeoUtils.destination(A, 0, 100));
        assertEquals(10000, GeoUtils.polygonAreaSquareMeters(square), 50);
        GeoPos c = GeoUtils.centroid(square);
        assertTrue(GeoUtils.pointInPolygon(c, square));
        assertFalse(GeoUtils.pointInPolygon(GeoUtils.destination(A, 270, 50), square));
    }

    @Test
    public void terrainProfileAndLineOfSight() {
        final GeoPos ridge = GeoUtils.destination(A, 90, 1000);
        // flat 100 m terrain with a 200 m-high ridge 1 km east of A
        ElevationSource ridgeTerrain = new ElevationSource() {
            @Override
            public double getElevationHae(double lat, double lon) {
                return GeoUtils.distanceMeters(new GeoPos(lat, lon), ridge) < 60 ? 300 : 100;
            }
        };
        DtedHelper dted = new DtedHelper(ridgeTerrain);
        GeoPos b = GeoUtils.destination(A, 90, 2000);
        TerrainProfile p = dted.getTerrainProfile(A, b, 30);
        assertTrue(p.isComplete());
        assertEquals(300, p.maxElevation(), 1e-9);
        assertEquals(2000, p.getTotalDistanceMeters(), 1);
        assertFalse(dted.hasLineOfSight(A, 10, b, 10, 30));
        assertTrue(dted.hasLineOfSight(A, 250, b, 250, 30));
        assertEquals(110, dted.aglToHae(A, 10), 1e-9);

        DtedHelper noData = new DtedHelper(new ElevationSource() {
            @Override
            public double getElevationHae(double lat, double lon) {
                return Double.NaN;
            }
        });
        assertFalse(noData.hasCoverage(A));
        assertTrue(noData.hasLineOfSight(A, 2, b, 2, 30));
    }
}
