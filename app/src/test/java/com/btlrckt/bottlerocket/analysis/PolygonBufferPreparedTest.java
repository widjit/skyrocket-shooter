package com.btlrckt.bottlerocket.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.btlrckt.bottlerocket.model.GeoPos;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** The cached {@link PolygonBuffer.Prepared} must match the static per-call API exactly. */
public class PolygonBufferPreparedTest {

    private static List<GeoPos> concave() {
        return Arrays.asList(new GeoPos(48.000, 11.000), new GeoPos(48.010, 11.000),
                new GeoPos(48.010, 11.020), new GeoPos(48.005, 11.010), new GeoPos(48.000, 11.020));
    }

    @Test
    public void matchesStaticApiForRandomPoints() {
        List<GeoPos> poly = concave();
        PolygonBuffer.Prepared prep = new PolygonBuffer.Prepared(poly);
        Random rnd = new Random(42);
        double[] buffers = {-300, -50, 0, 50, 500};
        for (int i = 0; i < 2000; i++) {
            GeoPos p = new GeoPos(47.99 + rnd.nextDouble() * 0.03, 10.99 + rnd.nextDouble() * 0.04);
            assertEquals(PolygonBuffer.signedDistance(p, poly), prep.signedDistance(p), 0.0);
            for (double b : buffers) {
                assertEquals(PolygonBuffer.contains(p, poly, b), prep.contains(p, b));
            }
        }
    }

    @Test
    public void unusablePolygonContainsNothing() {
        List<GeoPos> two = new ArrayList<>(concave().subList(0, 2));
        PolygonBuffer.Prepared prep = new PolygonBuffer.Prepared(two);
        GeoPos p = new GeoPos(48.005, 11.005);
        assertTrue(Double.isNaN(prep.signedDistance(p)));
        assertFalse(prep.contains(p, 1e6));
        assertFalse(new PolygonBuffer.Prepared(null).contains(p, 0));
        assertFalse(new PolygonBuffer.Prepared(concave()).contains(null, 0));
    }
}
