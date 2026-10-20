package com.btlrckt.bottlerocket.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.btlrckt.bottlerocket.model.SensorPreset;
import com.btlrckt.bottlerocket.model.SensorPresetDef;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class CustomPresetStoreTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static SensorPresetDef custom() {
        SensorPresetDef d = new SensorPresetDef();
        d.displayName = "Site radar";
        d.shortName = "SR";
        d.description = "Local";
        d.panelAzimuthDeg = 120;
        d.minRangeMeters = 25;
        d.elevationMaxDeg = 40;
        d.targetClasses = new String[] {"Group 1", "Group 2"};
        d.rangesMeters = new double[] {1200, 2500};
        return d;
    }

    @Test
    public void defaultMergedListEqualsBuiltIns() {
        List<SensorPresetDef> l = new CustomPresetStore().getMergedList();
        assertEquals(SensorPreset.values().length, l.size());
        for (int i = 0; i < l.size(); i++) {
            assertTrue(l.get(i).sameContentAs(SensorPresetDef.fromEnum(SensorPreset.values()[i])));
            assertTrue(l.get(i).isValid());
        }
    }

    @Test
    public void overrideAndReset() {
        CustomPresetStore s = new CustomPresetStore();
        String id = SensorPreset.TRUST_SUADS.name();
        SensorPresetDef d = SensorPresetDef.fromEnum(SensorPreset.TRUST_SUADS);
        d.rangesMeters[0] = 3100;
        d.minRangeMeters = 20;
        assertTrue(s.saveOverride(id, d));
        assertTrue(s.isOverridden(id));
        SensorPresetDef m = s.getMergedList().get(SensorPreset.TRUST_SUADS.ordinal());
        assertEquals(3100, m.rangeMeters(0), 0);
        assertEquals(20, m.minRangeMeters, 0);
        s.resetToDefault(id);
        assertFalse(s.isOverridden(id));
        assertTrue(s.getMergedList().get(SensorPreset.TRUST_SUADS.ordinal())
                .sameContentAs(SensorPresetDef.fromEnum(SensorPreset.TRUST_SUADS)));
    }

    @Test
    public void invalidOverrideRejected() {
        CustomPresetStore s = new CustomPresetStore();
        SensorPresetDef d = SensorPresetDef.fromEnum(SensorPreset.RADA_RPS42);
        d.rangesMeters = new double[0];
        d.targetClasses = new String[0];
        assertFalse(s.saveOverride(SensorPreset.RADA_RPS42.name(), d));
        assertFalse(s.saveOverride("unknown", custom()));
    }

    @Test
    public void addEditAndRemoveCustom() {
        CustomPresetStore s = new CustomPresetStore();
        String id = s.addCustom(custom());
        assertNotNull(id);
        assertTrue(id.startsWith(SensorPresetDef.CUSTOM_PREFIX));
        List<SensorPresetDef> l = s.getMergedList();
        assertEquals(SensorPreset.values().length + 1, l.size());
        assertEquals("Site radar", l.get(l.size() - 1).displayName);
        SensorPresetDef e = l.get(l.size() - 1);
        e.displayName = "Renamed";
        assertTrue(s.saveOverride(id, e));
        assertEquals("Renamed", s.getMergedList().get(l.size() - 1).displayName);
        s.removeCustom(id);
        assertEquals(SensorPreset.values().length, s.getMergedList().size());
        SensorPresetDef bad = custom();
        bad.displayName = " ";
        assertNull(s.addCustom(bad));
    }

    @Test
    public void jsonRoundTrip() {
        File f = new File(tmp.getRoot(), CustomPresetStore.FILE_NAME);
        CustomPresetStore s = new CustomPresetStore();
        s.attach(f);
        SensorPresetDef o = SensorPresetDef.fromEnum(SensorPreset.BLIGHTER_B202_MK2);
        o.description = "edited";
        s.saveOverride(o.id, o);
        String id = s.addCustom(custom());
        assertTrue(f.exists());

        CustomPresetStore r = new CustomPresetStore();
        r.attach(f);
        List<SensorPresetDef> a = s.getMergedList(), b = r.getMergedList();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) assertTrue(a.get(i).sameContentAs(b.get(i)));
        assertTrue(r.isOverridden(SensorPreset.BLIGHTER_B202_MK2.name()));
        assertEquals(id, b.get(b.size() - 1).id);
        assertTrue(Double.isNaN(b.get(b.size() - 1).elevationMinDeg));
    }

    @Test
    public void corruptFileFallsBackToBuiltIns() throws Exception {
        File f = new File(tmp.getRoot(), CustomPresetStore.FILE_NAME);
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write("{not json".getBytes("UTF-8"));
        }
        CustomPresetStore s = new CustomPresetStore();
        s.attach(f);
        assertEquals(SensorPreset.values().length, s.getMergedList().size());
    }
}
