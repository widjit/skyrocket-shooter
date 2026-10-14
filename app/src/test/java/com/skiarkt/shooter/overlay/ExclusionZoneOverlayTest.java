package com.skiarkt.shooter.overlay;

import static org.junit.Assert.assertEquals;

import com.skiarkt.shooter.settings.UnitSystem;

import org.junit.Test;

public class ExclusionZoneOverlayTest {

    @Test
    public void calloutUsesUnitAndPrecision() {
        assertEquals("Bldg A\n45.0 m AGL", ExclusionZoneOverlay.calloutText("Bldg A", 45.0, UnitSystem.METERS, 1));
        assertEquals("Bldg A\n147.6 ft AGL", ExclusionZoneOverlay.calloutText(" Bldg A ", 45.0, UnitSystem.FEET, 1));
        assertEquals("0.05 km AGL", ExclusionZoneOverlay.calloutText("", 45.0, UnitSystem.KILOMETERS, 2));
        assertEquals("45 m AGL", ExclusionZoneOverlay.calloutText(null, 45.0, UnitSystem.METERS, 0));
    }

    @Test
    public void opaqueForcesAlpha() {
        assertEquals(0xFF123456, ExclusionZoneOverlay.opaque(0x00123456));
        assertEquals(0xFF123456, ExclusionZoneOverlay.opaque(0x80123456));
    }
}
