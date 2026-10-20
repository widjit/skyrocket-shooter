package com.btlrckt.bottlerocket.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.atakmap.coremap.cot.event.CotEvent;
import com.btlrckt.bottlerocket.model.ExclusionZone;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.model.ItemKind;
import com.btlrckt.bottlerocket.model.SensorItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PointDefenseRepositoryTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private PointDefenseRepository repo;
    private final List<PointDefenseRepository.ChangeType> changes = new ArrayList<>();

    @Before
    public void setUp() {
        PointDefenseRepository.resetForTests();
        repo = PointDefenseRepository.getInstance();
        repo.addListener((type, item) -> changes.add(type));
    }

    @After
    public void tearDown() {
        PointDefenseRepository.resetForTests();
    }

    private static ExclusionZone zone() {
        ExclusionZone z = new ExclusionZone();
        z.points = Arrays.asList(new GeoPos(35, -117), new GeoPos(35.001, -117), new GeoPos(35.001, -116.999));
        z.heightAglMeters = 12.5;
        return z;
    }

    @Test
    public void crudNotifiesListeners() {
        ExclusionZone z = zone();
        repo.upsert(z);
        repo.upsert(z);
        assertEquals(1, repo.getPlan().count(ItemKind.EXCLUSION_ZONE));
        assertTrue(repo.remove(z.uid));
        assertFalse(repo.remove(z.uid));
        assertEquals(Arrays.asList(PointDefenseRepository.ChangeType.ITEM_ADDED,
                PointDefenseRepository.ChangeType.ITEM_UPDATED,
                PointDefenseRepository.ChangeType.ITEM_REMOVED), changes);
    }

    @Test
    public void jsonSaveLoadRoundTrip() throws Exception {
        repo.updateMetadata("FOB Bravo", "CPT Roe", "UNCLASSIFIED", "test");
        repo.upsert(zone());
        SensorItem s = new SensorItem();
        s.position = new GeoPos(35.002, -117.002); // altitude NaN must survive JSON
        repo.upsert(s);
        File f = new File(tmp.getRoot(), "plan" + PointDefenseRepository.PLAN_FILE_EXTENSION);
        repo.saveToFile(f);
        String uid = repo.getPlan().uid;

        repo.newPlan("Other", "", null);
        assertEquals(0, repo.getPlan().totalItems());
        repo.loadFromFile(f);
        assertEquals(uid, repo.getPlan().uid);
        assertEquals("FOB Bravo", repo.getPlan().name);
        assertEquals(2, repo.getPlan().totalItems());
        assertEquals(12.5, repo.getPlan().exclusionZones.get(0).heightAglMeters, 0);
        assertTrue(Double.isNaN(repo.getPlan().sensors.get(0).position.altHae));
    }

    @Test
    public void cotIngestRoundTrip() {
        ExclusionZone z = zone();
        repo.upsert(z);
        List<CotEvent> events = repo.toCotEvents();
        assertEquals(2, events.size());

        PointDefenseRepository.resetForTests();
        PointDefenseRepository other = PointDefenseRepository.getInstance();
        for (CotEvent ev : events) assertTrue(other.ingestCot(ev));
        assertEquals(1, other.getPlan().count(ItemKind.EXCLUSION_ZONE));
        assertEquals(repo.getPlan().uid, other.getPlan().uid);

        CotEvent foreign = new CotEvent();
        foreign.setType("a-h-A-M-F-Q");
        assertFalse(other.ingestCot(foreign));
    }
}
