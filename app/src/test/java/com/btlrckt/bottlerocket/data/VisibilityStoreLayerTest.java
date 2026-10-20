package com.btlrckt.bottlerocket.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.btlrckt.bottlerocket.data.VisibilityStore.Layer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class VisibilityStoreLayerTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** uids may contain dots (ATAK style). */
    private static final String UID = "sensor.1.abc";

    @Test
    public void layerKeyIsNotAnAsset() {
        VisibilityStore v = new VisibilityStore();
        v.setLayerHidden(UID, Layer.COVERAGE, true);
        assertTrue(v.isLayerHidden(UID, Layer.COVERAGE));
        assertTrue(v.isHidden(VisibilityStore.layerKey(UID, Layer.COVERAGE)));
        assertFalse(v.isHidden(UID));
        assertFalse(v.isLayerHidden(UID, Layer.MARKER));
        assertEquals(0, v.hiddenCount());
        assertTrue(v.hiddenUids().isEmpty());
        assertEquals(1, v.hiddenLayerCount(UID));
        assertEquals(UID, VisibilityStore.ownerOf(VisibilityStore.layerKey(UID, Layer.COVERAGE)));
        assertNull(VisibilityStore.ownerOf(UID));
    }

    @Test
    public void retainOnlyKeepsLayersOfLiveUids() {
        VisibilityStore v = new VisibilityStore();
        v.setLayerHidden(UID, Layer.TERRAIN_MASK, true);
        v.setHidden("other", true);
        v.retainOnly(Collections.singletonList(UID));
        assertTrue(v.isLayerHidden(UID, Layer.TERRAIN_MASK));
        assertFalse(v.isHidden("other"));
        v.retainOnly(Collections.singletonList("x"));
        assertFalse(v.isLayerHidden(UID, Layer.TERRAIN_MASK));
    }

    @Test
    public void showAllClearsAssetsAndLayers() {
        VisibilityStore v = new VisibilityStore();
        v.setHidden("a", true);
        v.setLayerHidden(UID, Layer.MARKER, true);
        v.showAll();
        assertFalse(v.isHidden("a"));
        assertFalse(v.isLayerHidden(UID, Layer.MARKER));
    }

    @Test
    public void soloKeepsLayersAndPersists() throws Exception {
        File f = new File(tmp.getRoot(), VisibilityStore.FILE_NAME);
        VisibilityStore v = new VisibilityStore();
        v.attach(f);
        v.setLayerHidden(UID, Layer.ENGAGE_RING, true);
        v.toggleSolo("b", Arrays.asList(UID, "b"));
        assertTrue(v.isHidden(UID));
        assertTrue(v.isLayerHidden(UID, Layer.ENGAGE_RING));
        v.toggleSolo("b", Arrays.asList(UID, "b"));
        assertFalse(v.isHidden(UID));
        assertTrue(v.isLayerHidden(UID, Layer.ENGAGE_RING));
        VisibilityStore r = new VisibilityStore();
        r.attach(f);
        assertTrue(r.isLayerHidden(UID, Layer.ENGAGE_RING));
        assertEquals(0, r.hiddenCount());
    }
}
