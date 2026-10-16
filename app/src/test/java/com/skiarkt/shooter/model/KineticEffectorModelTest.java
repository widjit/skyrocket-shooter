package com.skiarkt.shooter.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Test;

import java.util.Arrays;

public class KineticEffectorModelTest {
    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    static KineticEffector sample() {
        KineticEffector k = new KineticEffector();
        k.setName("LGR Battery North");
        k.position = new GeoPos(34.1234567, -116.7654321, 812.5);
        k.aglMeters = 3.5;
        k.type = KineticEffectorType.LASER_GUIDED_ROCKET;
        k.setMaxEngagementRange(4500);
        k.minEngagementRange = 150;
        k.azimuthMin = 300.0;
        k.azimuthMax = 60.0;
        k.elevationMin = -2;
        k.elevationMax = 70;
        k.fragmentationRadiusMeters = 25;
        k.fragmentationRouteWidth = 12.5;
        k.munitionType = MunitionType.HE;
        k.detonationMode = DetonationMode.PROXIMITY;
        k.setMunitionVelocity(650);
        k.restrictedAzimuths.add(new AzimuthArc(350, 10));
        k.restrictedAzimuths.add(new AzimuthArc(30, 45.5));
        k.minInterceptAltAGL = 20;
        k.engagementAuthorityPerimeterUid = "skiarkt-exczone-perimeter-1";
        k.friendlyFireCheckEnabled = false;
        k.linkedSensorUids = Arrays.asList("skiarkt-sensor-a", "skiarkt-sensor-b");
        k.linkedLaserDesignatorUids = Arrays.asList("designator-1");
        k.laserCode = 1688;
        k.status = EffectorStatus.ENGAGED;
        k.ammoCount = 12;
        k.reloadTimeSec = 45;
        k.notes = "Two-man crew; \"hot\" <standby> & ready";
        k.remarks = "Phase 4 demo";
        return k;
    }

    @Test
    public void constructDefaults() {
        KineticEffector k = new KineticEffector();
        assertEquals(ItemKind.KINETIC_EFFECTOR, k.getKind());
        assertTrue(k.uid.startsWith("skiarkt-ke-"));
        assertEquals(KineticEffectorType.RIFLE, k.type);
        assertEquals(EffectorStatus.READY, k.status);
        assertEquals(MunitionType.KINETIC_IMPACTOR, k.munitionType);
        assertEquals(DetonationMode.IMPACT, k.detonationMode);
        assertTrue(k.isFullCircle());
        assertEquals(360.0, k.azimuthSweepDeg(), 0);
        assertNull(k.laserCode);
        assertNotNull(k.restrictedAzimuths);
        assertTrue(k.friendlyFireCheckEnabled);
        assertTrue(k.isValid());
        assertNull(k.validationError());
    }

    @Test
    public void specAccessorsMapOntoPointFields() {
        KineticEffector k = sample();
        assertEquals("LGR Battery North", k.label);
        assertEquals(34.1234567, k.getLat(), 0);
        assertEquals(-116.7654321, k.getLon(), 0);
        assertEquals(3.5, k.getAgl(), 0);
        assertEquals(4500, k.maxEngagementDistMeters, 0);
        assertEquals(650, k.munitionSpeedMps, 0);
        assertEquals(25, k.getFragmentationRadius(), 0);
        assertTrue(k.isValid());
    }

    @Test
    public void sectorAndRestrictedAzimuths() {
        KineticEffector k = sample(); // sector 300 -> 60 through north (120 deg)
        assertFalse(k.isFullCircle());
        assertEquals(120.0, k.azimuthSweepDeg(), 1e-9);
        assertTrue(k.isBearingAllowed(320));
        assertTrue(k.isBearingAllowed(20));
        assertFalse(k.isBearingAllowed(0));    // restricted 350..10
        assertFalse(k.isBearingAllowed(40));   // restricted 30..45.5
        assertFalse(k.isBearingAllowed(180));  // outside sector
        assertTrue(k.canEngage(1000, 320));
        assertFalse(k.canEngage(100, 320));    // inside dead zone
        assertFalse(k.canEngage(5000, 320));   // beyond max range
    }

    @Test
    public void gsonRoundTrip() {
        KineticEffector k = sample();
        KineticEffector back = GSON.fromJson(GSON.toJson(k), KineticEffector.class);
        assertTrue(k.sameContentAs(back));
        assertEquals(Integer.valueOf(1688), back.laserCode);
        assertEquals(2, back.restrictedAzimuths.size());
        assertEquals(new AzimuthArc(30, 45.5), back.restrictedAzimuths.get(1));
    }

    @Test
    public void planGsonRoundTripAndHelpers() {
        PointDefensePlan plan = new PointDefensePlan();
        KineticEffector k = sample();
        plan.addKineticEffector(k);
        plan.addKineticEffector(new KineticEffector());
        assertEquals(2, plan.count(ItemKind.KINETIC_EFFECTOR));
        assertSame(k, plan.findKineticEffector(k.uid));

        PointDefensePlan back = GSON.fromJson(GSON.toJson(plan), PointDefensePlan.class);
        back.ensureLists();
        assertEquals(2, back.kineticEffectors.size());
        assertTrue(k.sameContentAs(back.findKineticEffector(k.uid)));

        KineticEffector edited = k.copy();
        edited.ammoCount = 3;
        assertTrue(plan.updateKineticEffector(edited));
        assertEquals(3, plan.findKineticEffector(k.uid).ammoCount);
        assertTrue(plan.removeKineticEffector(k.uid));
        assertFalse(plan.removeKineticEffector(k.uid));
        assertEquals(1, plan.kineticEffectors.size());
    }

    @Test
    public void legacyJsonWithoutPhase4FieldsGetsDefaults() {
        String json = "{\"kineticEffectors\":[{\"uid\":\"skiarkt-ke-old\",\"label\":\"Old gun\","
                + "\"position\":{\"lat\":35.0,\"lon\":-117.0},\"aglMeters\":2.0,\"maxEngagementDistMeters\":800.0,"
                + "\"fragmentationRadiusMeters\":15.0,\"munitionSpeedMps\":800.0,"
                + "\"restrictedAzimuths\":null,\"type\":null}]}";
        PointDefensePlan p = GSON.fromJson(json, PointDefensePlan.class);
        p.ensureLists();
        KineticEffector k = p.kineticEffectors.get(0);
        assertEquals(KineticEffectorType.RIFLE, k.type);
        assertNotNull(k.restrictedAzimuths);
        assertNotNull(k.linkedSensorUids);
        assertEquals(EffectorStatus.READY, k.status);
        assertTrue(k.isValid());
    }

    @Test
    public void copyIsDeep() {
        KineticEffector k = sample();
        KineticEffector c = k.copy();
        assertTrue(k.sameContentAs(c));
        c.restrictedAzimuths.get(0).bearingMin = 1;
        c.position.lat = 10;
        assertEquals(350, k.restrictedAzimuths.get(0).bearingMin, 0);
        assertEquals(34.1234567, k.position.lat, 0);
    }

    @Test
    public void validation() {
        KineticEffector k = sample();
        assertNull(k.validationError());

        k.minEngagementRange = 5000; // >= max
        assertEquals("minEngagementRange", k.validationError());
        assertFalse(k.isValid());
        k = sample();
        k.azimuthMax = null; // only one bound set
        assertEquals("azimuth", k.validationError());
        k = sample();
        k.elevationMin = 80; // >= elevationMax
        assertEquals("elevation", k.validationError());
        k = sample();
        k.laserCode = null; // LGR requires a code
        assertEquals("laserCode", k.validationError());
        k.laserCode = 1789;
        assertEquals("laserCode", k.validationError());
        k.laserCode = 1191; // 3rd digit 9 not allowed
        assertEquals("laserCode", k.validationError());
        k = sample();
        k.ammoCount = -1;
        assertEquals("ammoCount", k.validationError());
        k = sample();
        k.restrictedAzimuths.add(new AzimuthArc(10, 400));
        assertEquals("restrictedAzimuths", k.validationError());
        k = sample();
        k.munitionSpeedMps = 0;
        assertFalse(k.isValid());

        assertTrue(KineticEffector.isValidLaserCode(1111));
        assertTrue(KineticEffector.isValidLaserCode(1788));
        assertFalse(KineticEffector.isValidLaserCode(1110));
        assertFalse(KineticEffector.isValidLaserCode(1811));
        assertFalse(KineticEffector.isValidLaserCode(null));

        KineticEffector rifle = new KineticEffector();
        rifle.laserCode = null; // non-LGR: code optional
        assertTrue(rifle.isValid());
    }

    @Test
    public void azimuthArcParsing() {
        assertEquals(new AzimuthArc(30, 45), AzimuthArc.parse("30-45"));
        assertEquals(new AzimuthArc(350, 10), AzimuthArc.parse(" 350.0:10.0 "));
        assertNull(AzimuthArc.parse("abc"));
        assertNull(AzimuthArc.parse("10-400"));
        assertEquals(20.0, new AzimuthArc(350, 10).sweepDeg(), 1e-9);
        assertTrue(new AzimuthArc(350, 10).contains(5));
        assertFalse(new AzimuthArc(350, 10).contains(20));
    }
}
