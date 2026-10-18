package com.skiarkt.shooter.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * R9 per-asset visibility: a personal view setting on this device.
 *
 * <p>Holds the set of hidden item uids plus the snapshot taken when an item is soloed. Saved to
 * its own local file ({@link #FILE_NAME}) beside the autosave, restored after restart, and
 * <b>never</b> written to CoT or to the shared plan JSON, so hiding an asset here never hides it on
 * a teammate's map. Hiding only affects drawing: analysis code must not consult this store.
 *
 * <p>Thread-safe; the change callback runs on the calling thread.
 */
public final class VisibilityStore {
    public static final String FILE_NAME = "visibility.skiarkt.json";

    /** Notified after every change. */
    public interface Listener {
        void onVisibilityChanged();
    }

    /** Serialised form. */
    static final class State {
        List<String> hidden = new ArrayList<>();
        /** uid currently soloed (null = no solo). */
        String soloUid;
        /** Hidden set before the solo started (restored when solo ends). */
        List<String> beforeSolo;
    }

    private final Gson gson = new Gson();
    private final Set<String> hidden = new LinkedHashSet<>();
    private String soloUid;
    private Set<String> beforeSolo;
    private File file;
    private Listener listener;

    public synchronized void setListener(Listener l) {
        listener = l;
    }

    /** Sets the backing file and loads it (missing / corrupt file = everything visible). */
    public void attach(File f) {
        synchronized (this) {
            file = f;
            hidden.clear();
            soloUid = null;
            beforeSolo = null;
            if (f == null || !f.exists()) return;
            try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                State s = gson.fromJson(r, State.class);
                if (s != null) {
                    if (s.hidden != null) hidden.addAll(s.hidden);
                    soloUid = s.soloUid;
                    beforeSolo = s.beforeSolo == null ? null : new LinkedHashSet<>(s.beforeSolo);
                    if (soloUid == null) beforeSolo = null;
                }
            } catch (IOException | JsonParseException | IllegalStateException e) {
                hidden.clear();
                soloUid = null;
                beforeSolo = null;
            }
        }
    }

    public synchronized boolean isHidden(String uid) {
        return uid != null && hidden.contains(uid);
    }

    public synchronized boolean isVisible(String uid) {
        return !isHidden(uid);
    }

    public synchronized int hiddenCount() {
        return hidden.size();
    }

    /** Number of {@code uids} that are hidden. */
    public synchronized int hiddenCount(Collection<String> uids) {
        int n = 0;
        if (uids != null) for (String u : uids) if (u != null && hidden.contains(u)) n++;
        return n;
    }

    public synchronized Set<String> hiddenUids() {
        return new LinkedHashSet<>(hidden);
    }

    public synchronized String soloUid() {
        return soloUid;
    }

    public void setHidden(String uid, boolean hide) {
        if (uid == null) return;
        boolean changed;
        synchronized (this) {
            changed = hide ? hidden.add(uid) : hidden.remove(uid);
        }
        if (changed) changed();
    }

    public void toggle(String uid) {
        setHidden(uid, !isHidden(uid));
    }

    /** Hides or shows every uid in {@code uids} (per-type "hide all" / per-site toggle). */
    public void setHiddenAll(Collection<String> uids, boolean hide) {
        if (uids == null) return;
        boolean changed = false;
        synchronized (this) {
            for (String u : uids) {
                if (u == null) continue;
                changed |= hide ? hidden.add(u) : hidden.remove(u);
            }
        }
        if (changed) changed();
    }

    /**
     * Solo toggle (long-press): if {@code uid} is not soloed, snapshot the hidden set and hide every
     * uid in {@code allUids} except {@code uid}; if it is already soloed, restore the snapshot.
     *
     * @return true when the item is now soloed, false when the previous view was restored.
     */
    public boolean toggleSolo(String uid, Collection<String> allUids) {
        if (uid == null) return false;
        boolean nowSolo;
        synchronized (this) {
            if (uid.equals(soloUid)) {
                hidden.clear();
                if (beforeSolo != null) hidden.addAll(beforeSolo);
                soloUid = null;
                beforeSolo = null;
                nowSolo = false;
            } else {
                if (soloUid == null) beforeSolo = new LinkedHashSet<>(hidden);
                soloUid = uid;
                hidden.clear();
                if (allUids != null) for (String u : allUids) if (u != null && !u.equals(uid)) hidden.add(u);
                hidden.remove(uid);
                nowSolo = true;
            }
        }
        changed();
        return nowSolo;
    }

    /** "Show everything": clears every hidden uid and any solo snapshot. */
    public void showAll() {
        boolean changed;
        synchronized (this) {
            changed = !hidden.isEmpty() || soloUid != null;
            hidden.clear();
            soloUid = null;
            beforeSolo = null;
        }
        if (changed) changed();
    }

    /** Drops uids no longer in the plan (keeps the file tidy). */
    public void retainOnly(Collection<String> liveUids) {
        boolean changed;
        synchronized (this) {
            changed = hidden.retainAll(new LinkedHashSet<>(liveUids));
            if (soloUid != null && !liveUids.contains(soloUid)) {
                soloUid = null;
                beforeSolo = null;
                changed = true;
            }
        }
        if (changed) changed();
    }

    private void changed() {
        save();
        Listener l;
        synchronized (this) {
            l = listener;
        }
        if (l != null) l.onVisibilityChanged();
    }

    /** Writes the state atomically (tmp + rename); failures are non-fatal. */
    synchronized void save() {
        if (file == null) return;
        State s = new State();
        s.hidden = new ArrayList<>(hidden);
        s.soloUid = soloUid;
        s.beforeSolo = beforeSolo == null ? null : new ArrayList<>(beforeSolo);
        File tmp = new File(file.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            gson.toJson(s, w);
        } catch (IOException e) {
            return;
        }
        if (file.exists() && !file.delete()) return;
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(file);
    }
}
