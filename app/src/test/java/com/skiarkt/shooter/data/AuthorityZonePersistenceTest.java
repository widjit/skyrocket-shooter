package com.skiarkt.shooter.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.skiarkt.shooter.analysis.AuthorityEvaluator;
import com.skiarkt.shooter.analysis.AuthorityTest;
import com.skiarkt.shooter.model.AuthorityType;
import com.skiarkt.shooter.model.EngagementAuthorityZone;
import com.skiarkt.shooter.model.PointDefensePlan;

import org.junit.After;
import org.junit.Test;

public class AuthorityZonePersistenceTest {
    @After
    public void tearDown() {
        PointDefenseRepository.resetForTests();
    }

    @Test
    public void authorityZonesSurviveJsonRoundTrip() {
        PointDefenseRepository.resetForTests();
        PointDefenseRepository r = PointDefenseRepository.getInstance();
        EngagementAuthorityZone z = new EngagementAuthorityZone();
        z.authorityType = AuthorityType.NON_KINETIC;
        z.applyTypeColors();
        z.points = AuthorityTest.square(100, true);
        z.bufferMeters = 750;
        r.upsert(z);

        PointDefensePlan back = r.parseJson(r.toJson());
        assertEquals(1, back.authorityZones.size());
        EngagementAuthorityZone b = back.authorityZones.get(0);
        assertEquals(AuthorityType.NON_KINETIC, b.authorityType);
        assertEquals(750, b.bufferMeters, 1e-9);
        assertTrue(AuthorityEvaluator.inside(AuthorityTest.east(800), b, back));

        PointDefensePlan old = r.parseJson("{\"name\":\"old\"}");
        assertNotNull(old.authorityZones);
    }
}
