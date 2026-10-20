package com.btlrckt.bottlerocket.cot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.atakmap.coremap.cot.event.CotEvent;
import com.btlrckt.bottlerocket.model.BasePlanItem;
import com.btlrckt.bottlerocket.model.DefendedAsset;
import com.btlrckt.bottlerocket.model.ExclusionZone;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.model.ItemKind;
import com.btlrckt.bottlerocket.model.KineticEffector;
import com.btlrckt.bottlerocket.model.NonKineticEffector;
import com.btlrckt.bottlerocket.model.PointDefensePlan;
import com.btlrckt.bottlerocket.model.SensorItem;
import com.btlrckt.bottlerocket.model.SensorType;
import com.btlrckt.bottlerocket.model.ThreatCorridor;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CotSerializerTest {
    private static List<GeoPos> tri() {
        return Arrays.asList(new GeoPos(35, -117), new GeoPos(35.001, -117), new GeoPos(35.001, -116.999, 812.5));
    }

    @Test
    public void exclusionZoneRoundTrip() {
        ExclusionZone z = new ExclusionZone();
        z.label = "Hangar 3";
        z.heightAglMeters = 18.3;
        z.points = tri();
        z.remarks = "Steel <roof> & antenna";
        CotEvent ev = CotSerializer.toCot(z, "plan-1", 30);
        assertEquals(CotTypes.EXCLUSION_ZONE, ev.getType());
        assertTrue(ev.isValid());
        ExclusionZone back = (ExclusionZone) CotSerializer.fromCot(ev);
        assertEquals(z.uid, back.uid);
        assertEquals("Hangar 3", back.label);
        assertEquals(18.3, back.heightAglMeters, 1e-9);
        assertEquals(3, back.points.size());
        assertEquals(812.5, back.points.get(2).altHae, 1e-6);
        assertEquals(z.remarks, back.remarks);
        assertEquals("plan-1", CotSerializer.planUidOf(ev));
    }

    @Test
    public void pointItemsRoundTrip() {
        SensorItem s = new SensorItem();
        s.type = SensorType.EOIR;
        s.position = new GeoPos(34.5, -116.5);
        s.beamWidthDeg = 60;
        s.azimuthDeg = 270;
        s.rangeMeters = 3500;
        SensorItem sb = (SensorItem) CotSerializer.fromCot(CotSerializer.toCot(s, "p", 1));
        assertEquals(SensorType.EOIR, sb.type);
        assertEquals(34.5, sb.position.lat, 1e-8);
        assertEquals(60, sb.beamWidthDeg, 0);
        assertEquals(270, sb.azimuthDeg, 0);
        assertEquals(3500, sb.rangeMeters, 0);

        KineticEffector k = new KineticEffector();
        k.position = new GeoPos(34.6, -116.6);
        k.fragmentationRadiusMeters = 22.5;
        k.munitionSpeedMps = 1100;
        k.noShootColor = 0x80FF0000;
        KineticEffector kb = (KineticEffector) CotSerializer.fromCot(CotSerializer.toCot(k, "p", 1));
        assertEquals(22.5, kb.fragmentationRadiusMeters, 0);
        assertEquals(1100, kb.munitionSpeedMps, 0);
        assertEquals(0x80FF0000, kb.noShootColor);

        NonKineticEffector n = new NonKineticEffector();
        n.position = new GeoPos(34.7, -116.7);
        n.rfThresholdDbm = -82.5;
        n.transparency = 0.25f;
        NonKineticEffector nb = (NonKineticEffector) CotSerializer.fromCot(CotSerializer.toCot(n, "p", 1));
        assertEquals(-82.5, nb.rfThresholdDbm, 0);
        assertEquals(0.25f, nb.transparency, 0);
    }

    @Test
    public void polygonAndCorridorRoundTrip() {
        DefendedAsset a = new DefendedAsset();
        a.points = tri();
        a.priority = 2;
        a.fillColor = 0x4400FF00;
        DefendedAsset ab = (DefendedAsset) CotSerializer.fromCot(CotSerializer.toCot(a, "p", 1));
        assertEquals(2, ab.priority);
        assertEquals(0x4400FF00, ab.fillColor);
        assertEquals(3, ab.points.size());

        ThreatCorridor c = new ThreatCorridor();
        c.points = Arrays.asList(new GeoPos(35, -117), new GeoPos(35.01, -117.01));
        c.widthMeters = 350;
        ThreatCorridor cb = (ThreatCorridor) CotSerializer.fromCot(CotSerializer.toCot(c, "p", 1));
        assertEquals(2, cb.points.size());
        assertEquals(350, cb.widthMeters, 0);
        assertTrue(cb.isValid());
    }

    @Test
    public void planHeaderAndFullExport() {
        PointDefensePlan plan = new PointDefensePlan();
        plan.name = "FOB Alpha";
        plan.author = "SSG Doe";
        plan.classification = "CUI";
        ExclusionZone z = new ExclusionZone();
        z.points = tri();
        plan.exclusionZones.add(z);
        plan.sensors.add(new SensorItem());
        List<CotEvent> events = CotSerializer.planToCotEvents(plan, 30);
        assertEquals(3, events.size());
        PointDefensePlan meta = CotSerializer.planFromCot(events.get(0));
        assertNotNull(meta);
        assertEquals("FOB Alpha", meta.name);
        assertEquals("CUI", meta.classification);
        assertEquals(plan.uid, meta.uid);
        assertTrue(events.get(1).toString().contains("b-m-p-btlrckt-exczone"));
    }

    @Test
    public void ignoresForeignEvents() {
        CotEvent ev = new CotEvent();
        ev.setType("a-f-G-U-C");
        assertNull(CotSerializer.fromCot(ev));
        assertNull(CotTypes.kindForType("b-m-p-btlrckt-unknown"));
        assertEquals(ItemKind.SENSOR, CotTypes.kindForType(CotTypes.SENSOR));
        BasePlanItem none = CotSerializer.fromCot(null);
        assertNull(none);
    }
}
