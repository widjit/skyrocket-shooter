package com.btlrckt.bottlerocket.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.btlrckt.bottlerocket.settings.UnitSystem;

import org.junit.Test;

public class UnitConverterTest {
    @Test
    public void convertsBetweenUnits() {
        assertEquals(3280.84, UnitConverter.convert(1000, UnitSystem.METERS, UnitSystem.FEET), 0.01);
        assertEquals(0.3048, UnitConverter.feetToMeters(1), 1e-9);
        assertEquals(1.5, UnitConverter.convert(1500, UnitSystem.METERS, UnitSystem.KILOMETERS), 1e-9);
        assertEquals(42.0, UnitConverter.convert(42, UnitSystem.FEET, UnitSystem.FEET), 0);
    }

    @Test
    public void roundsToTenths() {
        assertEquals(3280.8, UnitConverter.roundTenths(3280.84), 1e-9);
        assertEquals(0.2, UnitConverter.roundTenths(0.15), 1e-9);
        assertTrue(Double.isNaN(UnitConverter.roundTenths(Double.NaN)));
    }

    @Test
    public void formatsAndParses() {
        assertEquals("3280.8 ft", UnitConverter.format(1000, UnitSystem.FEET, 1));
        assertEquals("1.0 km", UnitConverter.format(1000, UnitSystem.KILOMETERS, 1));
        assertEquals("--", UnitConverter.format(Double.NaN, UnitSystem.METERS, 1));
        assertEquals(304.8, UnitConverter.parseToMeters("1000 ft", UnitSystem.METERS), 1e-9);
        assertEquals(2000, UnitConverter.parseToMeters("2", UnitSystem.KILOMETERS), 1e-9);
        assertTrue(Double.isNaN(UnitConverter.parseToMeters("abc", UnitSystem.METERS)));
    }
}
