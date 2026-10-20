package com.btlrckt.bottlerocket.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.btlrckt.bottlerocket.model.GeoPos;

import org.junit.Test;

import java.util.Arrays;

public class PolygonDrawStateTest {
    @Test
    public void addUndoAndFinishRules() {
        PolygonDrawState s = new PolygonDrawState(3);
        assertFalse(s.add(null));
        assertFalse(s.add(new GeoPos(Double.NaN, 0)));
        assertTrue(s.add(new GeoPos(35, -117)));
        assertFalse("jitter tap ignored", s.add(new GeoPos(35.000001, -117)));
        assertTrue(s.add(new GeoPos(35.001, -117)));
        assertFalse(s.canFinish());
        assertTrue(s.add(new GeoPos(35.001, -116.999)));
        assertTrue(s.canFinish());
        assertTrue(s.undo());
        assertFalse(s.canFinish());
        assertEquals(2, s.size());
        s.snapshot().get(0).lat = 0; // snapshot is a copy
        assertEquals(35, s.getVertices().get(0).lat, 0);
        s.clear();
        assertFalse(s.undo());
    }

    @Test
    public void lineNeedsTwoVertices() {
        PolygonDrawState s = new PolygonDrawState(2);
        s.add(new GeoPos(35, -117));
        assertFalse(s.canFinish());
        s.add(new GeoPos(35.01, -117));
        assertTrue(s.canFinish());
    }

    @Test
    public void detectsBowTie() {
        assertFalse(PolygonDrawState.isSelfIntersecting(Arrays.asList(
                new GeoPos(0, 0), new GeoPos(0, 1), new GeoPos(1, 1), new GeoPos(1, 0))));
        assertTrue(PolygonDrawState.isSelfIntersecting(Arrays.asList(
                new GeoPos(0, 0), new GeoPos(1, 1), new GeoPos(0, 1), new GeoPos(1, 0))));
        assertFalse(PolygonDrawState.isSelfIntersecting(Arrays.asList(
                new GeoPos(0, 0), new GeoPos(1, 1), new GeoPos(0, 1))));
    }
}
