package com.skiarkt.shooter.cot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.skiarkt.shooter.model.AzimuthArc;
import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.DetonationMode;
import com.skiarkt.shooter.model.EffectorStatus;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.KineticEffector;
import com.skiarkt.shooter.model.KineticEffectorType;
import com.skiarkt.shooter.model.MunitionType;

import org.junit.Test;

import java.util.Arrays;

public class KineticEffectorCotTest {

    private static KineticEffector sample() {
        KineticEffector k = new KineticEffector();
        k.label = "Rocket Pod East";
        k.position = new GeoPos(34.5, -116.25, 701.25);
        k.aglMeters = 4;
        k.maxEngagementDistMeters = 3000;
        k.type = KineticEffectorType.LASER_GUIDED_ROCKET;
        k.minEngagementRange = 120;
        k.azimuthMin = 45.0;
        k.azimuthMax = 135.0;
        k.elevationMin = 0;
        k.elevationMax = 60;
        k.fragmentationRadiusMeters = 30;
        k.fragmentationRouteWidth = 8;
        k.munitionType = MunitionType.HE;
        k.detonationMode = DetonationMode.COMMAND;
        k.munitionSpeedMps = 550;
        k.restrictedAzimuths.add(new AzimuthArc(80, 95));
        k.minInterceptAltAGL = 25;
        k.engagementAuthorityPerimeterUid = "skiarkt-exczone-x";
        k.friendlyFireCheckEnabled = false;
        k.linkedSensorUids = Arrays.asList("s-1", "s-2");
        k.linkedLaserDesignatorUids = Arrays.asList("d-1");
        k.laserCode = 1511;
        k.status = EffectorStatus.MAINTENANCE;
        k.ammoCount = 7;
        k.reloadTimeSec = 90;
        k.notes = "Check <rails> & \"umbilical\"";
        k.remarks = "east pad";
        k.noShootColor = 0x80FF0000;
        return k;
    }

    @Test
    public void toCotThenFromCotEqualsOriginal() {
        KineticEffector k = sample();
        CotEvent ev = KineticEffectorCot.toCot(k);
        assertEquals(KineticEffectorCot.COT_TYPE, ev.getType());
        assertEquals("b-m-p-skiarkt-kinetic", ev.getType());
        assertEquals(k.uid, ev.getUID());
        assertNotNull(CotSerializer.child(ev.getDetail(), KineticEffectorCot.DETAIL_ELEMENT));

        KineticEffector back = KineticEffectorCot.fromCot(ev);
        assertNotNull(back);
        assertTrue(k.sameContentAs(back));
        assertTrue(back.isValid());
    }

    @Test
    public void fullCircleAndEmptyListsRoundTrip() {
        KineticEffector k = new KineticEffector();
        k.position = new GeoPos(35, -117);
        KineticEffector back = KineticEffectorCot.fromCot(KineticEffectorCot.toCot(k, "plan-9", 1));
        assertNotNull(back);
        assertNull(back.azimuthMin);
        assertNull(back.azimuthMax);
        assertNull(back.laserCode);
        assertNull(back.engagementAuthorityPerimeterUid);
        assertTrue(back.restrictedAzimuths.isEmpty());
        assertTrue(back.linkedSensorUids.isEmpty());
        assertTrue(k.sameContentAs(back));
    }

    @Test
    public void planShareViaCotSerializerCarriesKineticDetail() {
        KineticEffector k = sample();
        CotEvent legacy = CotSerializer.toCot(k, "p", 1);
        assertEquals(CotTypes.KINETIC_EFFECTOR, legacy.getType());
        KineticEffector viaLegacy = KineticEffectorCot.fromCot(legacy);
        assertTrue(k.sameContentAs(viaLegacy));
        BasePlanItem generic = CotSerializer.fromCot(KineticEffectorCot.toCot(k));
        assertTrue(generic instanceof KineticEffector);
        assertEquals(ItemKind.KINETIC_EFFECTOR, CotTypes.kindForType(KineticEffectorCot.COT_TYPE));
    }

    @Test
    public void rejectsForeignEvents() {
        assertNull(KineticEffectorCot.fromCot(null));
        CotEvent ev = new CotEvent();
        ev.setType(CotTypes.SENSOR);
        assertNull(KineticEffectorCot.fromCot(ev));
        ev.setType("a-f-G");
        ev.setDetail(new CotDetail("detail"));
        assertNull(KineticEffectorCot.fromCot(ev));
    }
}
