package com.btlrckt.bottlerocket.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;

public class VisibilityStoreTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final List<String> ALL = Arrays.asList("a", "b", "c", "d");

    @Test
    public void toggleAndCounts() {
        VisibilityStore v = new VisibilityStore();
        final int[] calls = {0};
        v.setListener(new VisibilityStore.Listener() {
            @Override
            public void onVisibilityChanged() {
                calls[0]++;
            }
        });
        v.toggle("a");
        assertTrue(v.isHidden("a"));
        v.setHidden("a", true); // no-op, no callback
        v.setHiddenAll(Arrays.asList("b", "c"), true);
        assertEquals(3, v.hiddenCount(ALL));
        assertEquals(1, v.hiddenCount(Arrays.asList("a", "zzz")));
        v.toggle("a");
        assertFalse(v.isHidden("a"));
        assertEquals(3, calls[0]);
    }

    @Test
    public void soloRestoresPreviousView() {
        VisibilityStore v = new VisibilityStore();
        v.setHidden("d", true);
        assertTrue(v.toggleSolo("b", ALL));
        assertEquals("b", v.soloUid());
        assertFalse(v.isHidden("b"));
        assertEquals(3, v.hiddenCount(ALL));
        // Switching solo keeps the original snapshot.
        assertTrue(v.toggleSolo("c", ALL));
        assertFalse(v.toggleSolo("c", ALL));
        assertNull(v.soloUid());
        assertTrue(v.isHidden("d"));
        assertEquals(1, v.hiddenCount(ALL));
    }

    @Test
    public void showAllAndRetainOnly() {
        VisibilityStore v = new VisibilityStore();
        v.setHiddenAll(ALL, true);
        v.retainOnly(Arrays.asList("a", "b"));
        assertEquals(2, v.hiddenCount());
        v.showAll();
        assertEquals(0, v.hiddenCount());
    }

    @Test
    public void persistsAcrossAttach() throws Exception {
        File f = new File(tmp.getRoot(), VisibilityStore.FILE_NAME);
        VisibilityStore v = new VisibilityStore();
        v.attach(f);
        v.setHidden("a", true);
        v.toggleSolo("c", ALL);
        assertTrue(f.exists());

        VisibilityStore w = new VisibilityStore();
        w.attach(f);
        assertEquals("c", w.soloUid());
        assertEquals(3, w.hiddenCount(ALL));
        assertFalse(w.toggleSolo("c", ALL));
        assertEquals(1, w.hiddenCount(ALL));
        assertTrue(w.isHidden("a"));
    }

    @Test
    public void corruptFileMeansEverythingVisible() throws Exception {
        File f = tmp.newFile("bad.json");
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write("{not json".getBytes("UTF-8"));
        }
        VisibilityStore v = new VisibilityStore();
        v.attach(f);
        assertEquals(0, v.hiddenCount());
    }
}
