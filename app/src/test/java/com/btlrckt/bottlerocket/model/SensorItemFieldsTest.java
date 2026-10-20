package com.btlrckt.bottlerocket.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.btlrckt.bottlerocket.cot.CotSerializer;
import com.btlrckt.bottlerocket.cot.CotTypes;

import org.junit.Test;

/**
 * R2 sensor fields. android.os.Parcel is not available in JVM unit tests (no Robolectric), so the
 * round trips cover copy(), the Gson plan JSON and CoT; legacy data without the new fields must
 * load with defaults.
 */
public class SensorItemFieldsTest {
    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private static SensorItem full() {
        SensorItem s = new SensorItem();
        s.type = SensorType.LASER_DESIGNATOR;
        s.position = new GeoPos(34.5, -116.5);
        s.rangeMeters = 5000;
        s.minRangeMeters = 150;
        s.elevationMinDeg = -10;
        s.elevationMaxDeg = 70;
        s.dayNight = DayNight.NIGHT;
        s.maxLasingRangeMeters = 4200;
        s.laserPrfCode = 1511;
        s.beamDivergenceMrad = 0.25;
        return s;
    }

    private static void assertNewFields(SensorItem e, SensorItem a) {
        assertEquals(e.type, a.type);
        assertEquals(e.minRangeMeters, a.minRangeMeters, 1e-9);
        assertEquals(e.elevationMinDeg, a.elevationMinDeg, 1e-9);
        assertEquals(e.elevationMaxDeg, a.elevationMaxDeg, 1e-9);
        assertEquals(e.dayNight, a.dayNight);
        assertEquals(e.maxLasingRangeMeters, a.maxLasingRangeMeters, 1e-9);
        assertEquals(e.laserPrfCode, a.laserPrfCode);
        assertEquals(e.beamDivergenceMrad, a.beamDivergenceMrad, 1e-9);
    }

    @Test
    public void defaults() {
        SensorItem s = new SensorItem();
        assertEquals(0, s.minRangeMeters, 0);
        assertTrue(Double.isNaN(s.elevationMinDeg));
        assertTrue(Double.isNaN(s.elevationMaxDeg));
        assertEquals(DayNight.DAY_NIGHT, s.dayNight);
        assertEquals(1688, s.laserPrfCode);
        assertEquals(0.5, s.beamDivergenceMrad, 0);
        assertFalse(s.hasMinRange());
    }

    @Test
    public void copyKeepsNewFields() {
        SensorItem s = full();
        assertNewFields(s, s.copy());
        assertTrue(s.hasMinRange());
    }

    @Test
    public void jsonRoundTripAndLegacyJson() {
        SensorItem s = full();
        assertNewFields(s, GSON.fromJson(GSON.toJson(s), SensorItem.class));
        SensorItem old = GSON.fromJson("{\"type\":\"RADAR\",\"rangeMeters\":3000,\"beamWidthDeg\":90}",
                SensorItem.class);
        assertEquals(3000, old.rangeMeters, 0);
        assertEquals(0, old.minRangeMeters, 0);
        assertEquals(DayNight.DAY_NIGHT, old.dayNight);
        assertEquals(1688, old.laserPrfCode);
        assertTrue(Double.isNaN(old.elevationMinDeg));
    }

    @Test
    public void cotRoundTripAndLegacyCot() {
        SensorItem s = full();
        assertNewFields(s, (SensorItem) CotSerializer.fromCot(CotSerializer.toCot(s, "p", 1)));

        SensorItem plain = new SensorItem();
        plain.position = new GeoPos(1, 2);
        CotEvent ev = CotSerializer.toCot(plain, "p", 1);
        for (CotDetail c : ev.getDetail().getChildren()) {
            if (CotTypes.DETAIL_BTLRCKT.equals(c.getElementName())) c.setAttribute("dayNight", null);
        }
        SensorItem back = (SensorItem) CotSerializer.fromCot(ev);
        assertEquals(0, back.minRangeMeters, 0);
        assertTrue(Double.isNaN(back.elevationMaxDeg));
        assertEquals(DayNight.DAY_NIGHT, back.dayNight);
        assertEquals(1688, back.laserPrfCode);
        assertEquals(0.5, back.beamDivergenceMrad, 0);
    }

    @Test
    public void validity() {
        SensorItem s = full();
        assertTrue(s.isValid());
        s.minRangeMeters = s.rangeMeters;
        assertFalse(s.isValid());
        s = full();
        s.elevationMinDeg = 80;
        assertFalse(s.isValid());
    }

    @Test
    public void presetAppliesMinRangeAndElevation() {
        SensorItem s = new SensorItem();
        SensorPreset.RADA_RPS42.applyTo(s, 0, 4);
        assertEquals(150, s.minRangeMeters, 0);
        assertEquals(-10, s.elevationMinDeg, 0);
        assertEquals(70, s.elevationMaxDeg, 0);
        SensorItem d = new SensorItem();
        SensorPresetDef.fromEnum(SensorPreset.RADA_RPS42).applyTo(d, 0, 4);
        assertEquals(s.rangeMeters, d.rangeMeters, 0);
        assertEquals(s.beamWidthDeg, d.beamWidthDeg, 0);
        assertEquals(150, d.minRangeMeters, 0);
    }
}
