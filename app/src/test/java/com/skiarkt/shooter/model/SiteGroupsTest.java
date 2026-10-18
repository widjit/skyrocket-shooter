package com.skiarkt.shooter.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.skiarkt.shooter.util.GeoUtils;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class SiteGroupsTest {

    private static PointDefensePlan plan() {
        PointDefensePlan p = new PointDefensePlan();
        SensorItem s1 = new SensorItem();
        s1.uid = "s1";
        s1.position = new GeoPos(35.0, -117.0);
        s1.siteUid = "Alpha-1";
        SensorItem s2 = new SensorItem();
        s2.uid = "s2";
        s2.position = new GeoPos(35.0005, -117.0);
        s2.siteUid = " alpha-1 ";
        KineticEffector k = new KineticEffector();
        k.uid = "k1";
        k.position = new GeoPos(35.0, -117.0005);
        k.siteUid = "Alpha-1";
        NonKineticEffector n = new NonKineticEffector();
        n.uid = "n1";
        n.position = new GeoPos(36, -117);
        p.sensors.add(s1);
        p.sensors.add(s2);
        p.kineticEffectors.add(k);
        p.nonKineticEffectors.add(n);
        return p;
    }

    @Test
    public void groupsCaseInsensitivelyAndSkipsUnassigned() {
        Map<String, SiteGroups.Site> sites = SiteGroups.build(plan());
        assertEquals(1, sites.size());
        SiteGroups.Site s = sites.values().iterator().next();
        assertEquals(3, s.members.size());
        assertEquals("Alpha-1: 2 sensors, 1 kinetic", s.summary());
        assertTrue(s.memberUids().contains("k1"));
    }

    @Test
    public void ringEnclosesMembers() {
        SiteGroups.Site s = SiteGroups.build(plan()).values().iterator().next();
        GeoPos c = s.center();
        double r = s.ringRadiusMeters();
        assertTrue(r >= SiteGroups.MIN_RING_RADIUS_M);
        for (PointItem p : s.members) assertTrue(GeoUtils.distanceMeters(c, p.position) < r);
    }

    @Test
    public void otherMembersExcludesSelf() {
        PointDefensePlan p = plan();
        List<PointItem> others = SiteGroups.otherMembers(p, p.sensors.get(0));
        assertEquals(2, others.size());
        assertTrue(SiteGroups.otherMembers(p, p.nonKineticEffectors.get(0)).isEmpty());
        assertNull(PointItem.normalizeSiteUid("   "));
    }

    @Test
    public void rfBandListRoundTrips() {
        java.util.List<RfBand> bands = new java.util.ArrayList<>();
        bands.add(new RfBand("Ctrl 2.4", 2437, 40, -72, 1500, true));
        bands.add(new RfBand("Video; 5.8|x", 5800, 37, -65, 0, false));
        List<RfBand> back = RfBand.decodeList(RfBand.encodeList(bands));
        assertEquals(2, back.size());
        assertEquals(bands.get(0).key(), back.get(0).key());
        assertEquals(5800, back.get(1).frequencyMhz, 1e-9);
        assertEquals(false, back.get(1).enabled);
        assertEquals("Video; 5.8|x", back.get(1).label);
        assertTrue(RfBand.decodeList(null).isEmpty());
    }
}
