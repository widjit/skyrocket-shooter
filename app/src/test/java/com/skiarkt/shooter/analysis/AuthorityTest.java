package com.skiarkt.shooter.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.atakmap.coremap.cot.event.CotEvent;
import com.skiarkt.shooter.cot.CotSerializer;
import com.skiarkt.shooter.cot.CotTypes;
import com.skiarkt.shooter.model.AuthorityType;
import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.DefendedAsset;
import com.skiarkt.shooter.model.EngagementAuthorityZone;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.KineticEffector;
import com.skiarkt.shooter.model.PointDefensePlan;
import com.skiarkt.shooter.util.GeoUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AuthorityTest {
    private static final double LAT = 35.0, LON = -117.0;

    /** Square of half-side {@code half} meters centered on (LAT, LON), CW or CCW. */
    public static List<GeoPos> square(double half, boolean ccw) {
        GeoPos c = new GeoPos(LAT, LON);
        GeoPos n = GeoUtils.destination(c, 0, half), s = GeoUtils.destination(c, 180, half);
        GeoPos e = GeoUtils.destination(c, 90, half), w = GeoUtils.destination(c, 270, half);
        List<GeoPos> pts = new ArrayList<>();
        pts.add(new GeoPos(s.lat, w.lon));
        pts.add(new GeoPos(s.lat, e.lon));
        pts.add(new GeoPos(n.lat, e.lon));
        pts.add(new GeoPos(n.lat, w.lon));
        if (!ccw) java.util.Collections.reverse(pts);
        return pts;
    }

    public static GeoPos east(double m) {
        return GeoUtils.destination(new GeoPos(LAT, LON), 90, m);
    }

    /** Planar area (m^2) using a local equirectangular projection. */
    private static double area(List<GeoPos> ring) {
        double k = Math.cos(Math.toRadians(LAT)) * 111320.0, a = 0;
        for (int i = 0; i < ring.size(); i++) {
            GeoPos p = ring.get(i), q = ring.get((i + 1) % ring.size());
            a += (p.lon * k) * (q.lat * 110574.0) - (q.lon * k) * (p.lat * 110574.0);
        }
        return Math.abs(a / 2);
    }

    private static EngagementAuthorityZone zone(AuthorityType t, List<GeoPos> base, double buffer) {
        EngagementAuthorityZone z = new EngagementAuthorityZone();
        z.authorityType = t;
        z.applyTypeColors();
        z.label = t.shortTag;
        z.points = base;
        z.bufferMeters = buffer;
        return z;
    }

    // ---------------------------------------------------------------- PolygonBuffer

    @Test
    public void signedDistanceInsideNegativeOutsidePositive() {
        List<GeoPos> sq = square(100, true);
        assertEquals(-100, PolygonBuffer.signedDistance(new GeoPos(LAT, LON), sq), 2.0);
        assertEquals(100, PolygonBuffer.signedDistance(east(200), sq), 2.0);
        assertEquals(PolygonBuffer.signedDistance(east(200), sq),
                PolygonBuffer.signedDistance(east(200), square(100, false)), 1e-6);
    }

    @Test
    public void containsHonoursOutwardAndInwardBuffers() {
        List<GeoPos> sq = square(100, false);
        assertFalse(PolygonBuffer.contains(east(150), sq, 0));
        assertTrue(PolygonBuffer.contains(east(150), sq, 60));
        assertFalse(PolygonBuffer.contains(east(170), sq, 60));
        assertTrue(PolygonBuffer.contains(east(40), sq, -50));
        assertFalse(PolygonBuffer.contains(east(60), sq, -50));
        assertFalse(PolygonBuffer.contains(new GeoPos(LAT, LON), sq, -150)); // collapsed
    }

    @Test
    public void offsetRingAreaGrowsOutwardShrinksInwardAndCollapses() {
        for (boolean ccw : new boolean[]{true, false}) {
            List<GeoPos> sq = square(100, ccw);
            double base = area(sq);
            List<GeoPos> out = PolygonBuffer.offsetRing(sq, 50);
            List<GeoPos> in = PolygonBuffer.offsetRing(sq, -50);
            // outward square + rounded corners: 200x200 + 4*200*50 + pi*50^2
            assertEquals(40000 + 40000 + Math.PI * 2500, area(out), 1500);
            assertEquals(10000, area(in), 400);
            assertTrue(area(out) > base && area(in) < base);
            assertTrue(PolygonBuffer.offsetRing(sq, -120).isEmpty());
            assertEquals(sq.size(), PolygonBuffer.offsetRing(sq, 0).size());
            // every outward vertex is ~50 m from the base
            for (GeoPos p : out) assertEquals(50, PolygonBuffer.signedDistance(p, sq), 3.0);
        }
    }

    // ---------------------------------------------------------------- evaluator rule

    @Test
    public void nonHostileOverridesAuthority() {
        PointDefensePlan plan = new PointDefensePlan();
        plan.authorityZones.add(zone(AuthorityType.KINETIC, square(1000, true), 0));
        plan.authorityZones.add(zone(AuthorityType.NON_KINETIC, square(500, true), 500));
        plan.authorityZones.add(zone(AuthorityType.NON_HOSTILE, square(100, true), 0));

        AuthorityEvaluator.PointStatus mid = AuthorityEvaluator.evaluatePoint(east(300), plan);
        assertTrue(mid.kineticAuthorized());
        assertTrue(mid.nonKineticAuthorized());

        AuthorityEvaluator.PointStatus hold = AuthorityEvaluator.evaluatePoint(east(50), plan);
        assertTrue(hold.inKineticZone && hold.inNonHostileZone);
        assertFalse(hold.kineticAuthorized());
        assertFalse(hold.nonKineticAuthorized());

        AuthorityEvaluator.PointStatus far = AuthorityEvaluator.evaluatePoint(east(1500), plan);
        assertFalse(far.kineticAuthorized());
        assertFalse(far.nonKineticAuthorized());
        assertEquals(0, AuthorityEvaluator.evaluatePoint(east(5000), plan).zoneLabels.size());
    }

    @Test
    public void linkedBaseIsLiveAndSnapshotFallsBack() {
        PointDefensePlan plan = new PointDefensePlan();
        DefendedAsset a = new DefendedAsset();
        a.points = square(100, true);
        plan.defendedAssets.add(a);
        EngagementAuthorityZone z = zone(AuthorityType.NON_HOSTILE, new ArrayList<GeoPos>(), 200);
        z.baseUid = a.uid;
        z.syncSnapshot(plan);
        assertEquals(4, z.points.size());
        assertSame(a.points, z.resolveBase(plan));
        assertTrue(AuthorityEvaluator.inside(east(250), z, plan));
        a.points = square(400, true); // base edited: zone follows
        assertTrue(AuthorityEvaluator.inside(east(550), z, plan));
        plan.defendedAssets.clear(); // base deleted: snapshot
        assertTrue(z.isBaseMissing(plan));
        assertTrue(AuthorityEvaluator.inside(east(250), z, plan));
        assertFalse(AuthorityEvaluator.inside(east(550), z, plan));
    }

    // ---------------------------------------------------------------- effector check

    private static KineticEffector ke(double range) {
        KineticEffector k = new KineticEffector();
        k.label = "Rifle";
        k.position = new GeoPos(LAT, LON);
        k.maxEngagementDistMeters = range;
        k.minEngagementRange = 0;
        return k;
    }

    @Test
    public void effectorChecksFlagViolations() {
        PointDefensePlan plan = new PointDefensePlan();
        KineticEffector k = ke(300);
        plan.kineticEffectors.add(k);

        AuthorityEvaluator.EffectorCheck none = AuthorityEvaluator.checkEffector(k, plan);
        assertTrue(none.flags.contains(AuthorityEvaluator.Flag.NO_AUTHORITY_ZONE));

        plan.authorityZones.add(zone(AuthorityType.KINETIC, square(100, true), 400));
        AuthorityEvaluator.EffectorCheck ok = AuthorityEvaluator.checkEffector(k, plan);
        assertFalse(ok.summary(), ok.hasViolation());
        assertEquals(1.0, ok.authorizedFraction, 1e-9);

        k.maxEngagementDistMeters = 1000;
        AuthorityEvaluator.EffectorCheck big = AuthorityEvaluator.checkEffector(k, plan);
        assertTrue(big.flags.contains(AuthorityEvaluator.Flag.ENVELOPE_EXCEEDS_AUTHORITY));
        assertTrue(big.unauthorizedFraction > 0.5);

        EngagementAuthorityZone hold = zone(AuthorityType.NON_HOSTILE, square(50, true), 0);
        plan.authorityZones.add(hold);
        AuthorityEvaluator.EffectorCheck h = AuthorityEvaluator.checkEffector(k, plan);
        assertTrue(h.flags.contains(AuthorityEvaluator.Flag.EFFECTOR_IN_NON_HOSTILE));
        assertTrue(h.flags.contains(AuthorityEvaluator.Flag.ENVELOPE_IN_NON_HOSTILE));
        assertEquals(1.0, h.authorizedFraction + h.nonHostileFraction + h.unauthorizedFraction, 1e-9);

        // linked to a non-kinetic zone -> wrong class
        EngagementAuthorityZone nk = zone(AuthorityType.NON_KINETIC, square(100, true), 5000);
        plan.authorityZones.add(nk);
        k.engagementAuthorityPerimeterUid = nk.uid;
        assertTrue(AuthorityEvaluator.checkEffector(k, plan).flags
                .contains(AuthorityEvaluator.Flag.LINKED_ZONE_WRONG_CLASS));
        assertEquals(1, AuthorityEvaluator.checkAll(plan).size());
    }

    // ---------------------------------------------------------------- persistence

    @Test
    public void cotRoundTrip() {
        EngagementAuthorityZone z = zone(AuthorityType.NON_HOSTILE, square(100, true), -25.5);
        z.baseUid = "skiarkt-defasset-1";
        z.fillOpacity = 0.4f;
        z.strokeDashed = true;
        z.label = "Hospital <hold>";
        CotEvent ev = CotSerializer.toCot(z, "plan-1", 1);
        assertEquals(CotTypes.AUTHORITY_ZONE, ev.getType());
        BasePlanItem back = CotSerializer.fromCot(ev);
        assertTrue(back instanceof EngagementAuthorityZone);
        EngagementAuthorityZone b = (EngagementAuthorityZone) back;
        assertEquals(z.uid, b.uid);
        assertEquals(z.label, b.label);
        assertEquals(AuthorityType.NON_HOSTILE, b.authorityType);
        assertEquals(-25.5, b.bufferMeters, 1e-6);
        assertEquals("skiarkt-defasset-1", b.baseUid);
        assertEquals(0.4f, b.fillOpacity, 1e-3);
        assertTrue(b.strokeDashed);
        assertEquals(z.fillColor & 0xFFFFFF, b.fillColor & 0xFFFFFF);
        assertEquals(4, b.points.size());
        assertEquals(ItemKind.AUTHORITY_ZONE, b.getKind());
    }
}
