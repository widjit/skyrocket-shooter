package com.skiarkt.shooter.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SensorPresetTest {
    @Test
    public void rps42DataSheetValues() {
        SensorPreset p = SensorPreset.RADA_RPS42;
        assertEquals(10000.0, p.rangeMeters(0), 0);
        assertEquals(25000.0, p.rangeMeters(1), 0);
        assertEquals(150.0, p.minRangeMeters, 0);
        assertEquals(90.0, p.beamWidthDeg(1), 0);
        assertEquals(360.0, p.beamWidthDeg(4), 0);
    }

    @Test
    public void nmhrDataSheetValues() {
        SensorPreset p = SensorPreset.RADA_RPS92_NMHR;
        assertEquals(8000.0, p.rangeMeters(0), 0);
        assertEquals(36000.0, p.rangeMeters(1), 0);
        assertEquals(90.0, p.elevationMaxDeg, 0);
    }

    @Test
    public void applyToSetsRadarRangeAndFov() {
        SensorItem s = new SensorItem();
        s.type = SensorType.CAMERA;
        s.azimuthDeg = 45.0;
        SensorPreset.RADA_RPS42.applyTo(s, 0, 2);
        assertEquals(SensorType.RADAR, s.type);
        assertEquals(10000.0, s.rangeMeters, 0);
        assertEquals(180.0, s.beamWidthDeg, 0);
        assertEquals(45.0, s.azimuthDeg, 0);
        assertTrue(s.isValid() || s.position == null);
    }

    @Test
    public void applyToClampsOutOfRangeInputs() {
        SensorItem s = new SensorItem();
        SensorPreset.RADA_RPS92_NMHR.applyTo(s, 99, 9);
        assertEquals(36000.0, s.rangeMeters, 0);
        assertTrue(s.isOmnidirectional());
        SensorPreset.RADA_RPS92_NMHR.applyTo(s, -1, 0);
        assertEquals(8000.0, s.rangeMeters, 0);
        assertEquals(90.0, s.beamWidthDeg, 0);
    }
}
