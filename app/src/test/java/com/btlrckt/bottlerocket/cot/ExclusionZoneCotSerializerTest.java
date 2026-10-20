package com.btlrckt.bottlerocket.cot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.btlrckt.bottlerocket.model.ExclusionZone;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.model.SensorItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class ExclusionZoneCotSerializerTest {

    private static ExclusionZone sample() {
        ExclusionZone z = new ExclusionZone();
        z.label = "Fuel Farm";
        z.remarks = "No overflight";
        z.heightAglMeters = 22.5;
        z.color = 0xFF112233;
        z.fillColor = 0xFF00FF00;
        z.fillOpacity = 0.45f;
        z.strokeWidth = 5.0;
        z.strokeDashed = false;
        z.points = new ArrayList<>(Arrays.asList(new GeoPos(35, -117), new GeoPos(35.001, -117),
                new GeoPos(35.001, -116.999), new GeoPos(35, -116.999)));
        return z;
    }

    @Test
    public void roundTripPreservesAllProperties() {
        ExclusionZone z = sample();
        CotEvent ev = ExclusionZoneCotSerializer.toCot(z, "plan-9", 30);
        assertEquals("b-m-p-btlrckt-exczone", ev.getType());
        assertTrue(ExclusionZoneCotSerializer.isExclusionZone(ev));

        ExclusionZone back = ExclusionZoneCotSerializer.fromCot(ev);
        assertNotNull(back);
        assertEquals(z.uid, back.uid);
        assertEquals("Fuel Farm", back.label);
        assertEquals("No overflight", back.remarks);
        assertEquals(22.5, back.heightAglMeters, 1e-9);
        assertEquals(0xFF112233, back.color);
        assertEquals(0xFF112233, back.getStrokeColor());
        assertEquals(0xFF00FF00, back.fillColor);
        assertEquals(0.45f, back.fillOpacity, 1e-6);
        assertEquals(5.0, back.strokeWidth, 1e-9);
        assertFalse(back.strokeDashed);
        assertEquals(4, back.points.size());
        assertEquals(z.points.get(2).lat, back.points.get(2).lat, 1e-8);
        assertEquals(z.createdMillis, back.createdMillis);
        assertEquals(z.modifiedMillis, back.modifiedMillis);
    }

    @Test
    public void writesStandardTakShapeElements() {
        CotDetail d = ExclusionZoneCotSerializer.toCot(sample(), "p", 30).getDetail();
        assertEquals(String.valueOf(0x7300FF00), CotSerializer.child(d, "fillColor").getAttribute("value"));
        assertEquals("5.0", CotSerializer.child(d, "strokeWeight").getAttribute("value"));
        assertEquals("solid", CotSerializer.child(d, "strokeStyle").getAttribute("value"));
        assertEquals("22.5", CotSerializer.child(d, "height").getAttribute("value"));
        assertNotNull(CotSerializer.child(d, "labels_on"));
        int links = 0;
        for (CotDetail c : d.getChildren()) if ("link".equals(c.getElementName())) links++;
        assertEquals(4, links);
    }

    @Test
    public void fallsBackToStandardElementsWhenBtlrcktAttributesMissing() {
        CotEvent ev = ExclusionZoneCotSerializer.toCot(sample(), "p", 30);
        CotDetail btlrckt = CotSerializer.child(ev.getDetail(), CotTypes.DETAIL_BTLRCKT);
        // simulate an older / foreign producer that only wrote heightAgl
        CotDetail minimal = new CotDetail(CotTypes.DETAIL_BTLRCKT);
        minimal.setAttribute("v", "1");
        minimal.setAttribute("kind", "exczone");
        CotDetail newDetail = new CotDetail("detail");
        for (CotDetail c : ev.getDetail().getChildren()) {
            newDetail.addChild(c == btlrckt ? minimal : c);
        }
        ev.setDetail(newDetail);

        ExclusionZone back = ExclusionZoneCotSerializer.fromCot(ev);
        assertNotNull(back);
        assertEquals(22.5, back.heightAglMeters, 1e-9);           // from <height>
        assertEquals(0xFF00FF00, back.fillColor);                  // RGB from <fillColor>
        assertEquals(0x73 / 255f, back.fillOpacity, 1e-6);         // alpha from <fillColor>
        assertEquals(5.0, back.strokeWidth, 1e-9);                 // from <strokeWeight>
        assertFalse(back.strokeDashed);                            // from <strokeStyle>
        assertEquals(0xFF112233, back.color);                      // from <strokeColor>
    }

    @Test
    public void clampsOutOfRangeStyleValues() {
        ExclusionZone z = sample();
        z.fillOpacity = 3f;
        z.strokeWidth = 99;
        ExclusionZone back = ExclusionZoneCotSerializer.fromCot(ExclusionZoneCotSerializer.toCot(z, "p", 1));
        assertEquals(1f, back.fillOpacity, 0);
        assertEquals(ExclusionZone.MAX_STROKE_WIDTH, back.strokeWidth, 0);
    }

    @Test
    public void rejectsOtherEventTypes() {
        assertNull(ExclusionZoneCotSerializer.fromCot(null));
        CotEvent sensor = CotSerializer.toCot(new SensorItem(), "p", 1);
        assertNull(ExclusionZoneCotSerializer.fromCot(sensor));
        assertFalse(ExclusionZoneCotSerializer.isExclusionZone(sensor));
    }

    @Test
    public void copyIsDeep() {
        ExclusionZone z = sample();
        ExclusionZone c = z.copy();
        c.points.get(0).lat = 1;
        c.heightAglMeters = 1;
        assertEquals(35, z.points.get(0).lat, 0);
        assertEquals(22.5, z.heightAglMeters, 0);
        assertEquals(z.uid, c.uid);
        assertEquals(PlanColorsCheck.withAlpha(0xFF00FF00, 0.45f), z.getFillArgb());
    }

    /** Local alias to keep the expectation explicit. */
    private static final class PlanColorsCheck {
        static int withAlpha(int argb, float a) {
            return (Math.round(a * 255f) << 24) | (argb & 0x00FFFFFF);
        }
    }
}
