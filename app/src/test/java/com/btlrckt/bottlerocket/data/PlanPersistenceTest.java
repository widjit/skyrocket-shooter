package com.btlrckt.bottlerocket.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.btlrckt.bottlerocket.model.ExclusionZone;
import com.btlrckt.bottlerocket.model.GeoPos;
import com.btlrckt.bottlerocket.model.ItemKind;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** The working plan must survive a plugin reload / ATAK restart (simulated by a fresh singleton). */
public class PlanPersistenceTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @After
    public void tearDown() {
        PointDefenseRepository.resetForTests();
    }

    private PointDefenseRepository freshRepo(File dir) {
        PointDefenseRepository.resetForTests();
        PointDefenseRepository r = PointDefenseRepository.getInstance();
        r.setAutosaveDelayMs(0);
        r.init(dir, 30);
        return r;
    }

    private static ExclusionZone zone(double h) {
        ExclusionZone z = new ExclusionZone();
        z.points = Arrays.asList(new GeoPos(35, -117), new GeoPos(35.001, -117), new GeoPos(35.001, -116.999));
        z.heightAglMeters = h;
        return z;
    }

    @Test
    public void planSurvivesReload() {
        File dir = tmp.getRoot();
        PointDefenseRepository r = freshRepo(dir);
        r.updateMetadata("FOB Bravo", null, null, null);
        ExclusionZone z = zone(20);
        r.upsert(z);
        r.upsert(zone(30));
        r.remove(z.uid);
        r.flushAutosave();
        String uid = r.getPlan().uid;
        assertTrue(new File(dir, PointDefenseRepository.AUTOSAVE_FILE_NAME).exists());

        PointDefenseRepository r2 = freshRepo(dir);
        assertEquals(uid, r2.getPlan().uid);
        assertEquals("FOB Bravo", r2.getPlan().name);
        assertEquals(1, r2.getPlan().count(ItemKind.EXCLUSION_ZONE));
        assertEquals(30, r2.getPlan().exclusionZones.get(0).heightAglMeters, 0);
    }

    @Test
    public void shutdownFlushesPendingWrite() {
        File dir = tmp.getRoot();
        PointDefenseRepository r = freshRepo(dir);
        r.setAutosaveDelayMs(60_000); // would not fire during the test on its own
        r.upsert(zone(5));
        r.shutdownAutosave();
        assertEquals(1, freshRepo(dir).getPlan().totalItems());
    }

    @Test
    public void debouncedWriteHappensWithoutFlush() throws Exception {
        File dir = tmp.getRoot();
        PointDefenseRepository r = freshRepo(dir);
        r.upsert(zone(5));
        File f = new File(dir, PointDefenseRepository.AUTOSAVE_FILE_NAME);
        for (int i = 0; i < 100 && !f.exists(); i++) Thread.sleep(20);
        assertTrue(f.exists());
    }

    @Test
    public void recoversFromLeftoverTempFile() throws Exception {
        File dir = tmp.getRoot();
        PointDefenseRepository r = freshRepo(dir);
        r.upsert(zone(7));
        r.flushAutosave();
        File f = new File(dir, PointDefenseRepository.AUTOSAVE_FILE_NAME);
        assertTrue(f.renameTo(new File(f.getPath() + ".tmp"))); // crash between delete and rename
        assertEquals(1, freshRepo(dir).getPlan().totalItems());
    }

    @Test
    public void corruptAutosaveIsMovedAsideNotLost() throws Exception {
        File dir = tmp.getRoot();
        File f = new File(dir, PointDefenseRepository.AUTOSAVE_FILE_NAME);
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write("{ not json".getBytes(StandardCharsets.UTF_8));
        }
        PointDefenseRepository r = freshRepo(dir);
        assertEquals(0, r.getPlan().totalItems());
        assertFalse(f.exists());
        String[] aside = dir.list((d, n) -> n.startsWith(PointDefenseRepository.AUTOSAVE_FILE_NAME + ".corrupt-"));
        assertEquals(1, aside.length);
    }

    @Test
    public void newPlanArchivesNonEmptyPlan() {
        File dir = tmp.getRoot();
        PointDefenseRepository r = freshRepo(dir);
        r.updateMetadata("Alpha Site", null, null, null);
        r.upsert(zone(9));
        r.newPlan("Next", "", null);
        assertTrue(new File(dir, "Alpha_Site" + PointDefenseRepository.PLAN_FILE_EXTENSION).exists());
        r.flushAutosave();
        assertEquals("Next", freshRepo(dir).getPlan().name);
    }

    @Test
    public void noStorageDirMeansNoAutosave() {
        PointDefenseRepository.resetForTests();
        PointDefenseRepository r = PointDefenseRepository.getInstance();
        r.upsert(zone(1));
        r.flushAutosave(); // must not throw
        assertEquals(null, r.autosaveFile());
    }
}
