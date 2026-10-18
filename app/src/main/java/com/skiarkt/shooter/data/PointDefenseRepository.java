package com.skiarkt.shooter.data;

import com.atakmap.coremap.cot.event.CotEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.skiarkt.shooter.cot.CotDispatchHelper;
import com.skiarkt.shooter.cot.CotSerializer;
import com.skiarkt.shooter.cot.CotTypes;
import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.PointDefensePlan;

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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Singleton holding the active {@link PointDefensePlan}. Single source of truth for
 * the UI, overlays and analysis engines. Provides CRUD with change notification,
 * JSON persistence (local working copy) and CoT import/export (sharing).
 *
 * Persistence: once {@link #init} has been given a storage directory, the working plan is
 * restored from {@link #AUTOSAVE_FILE_NAME} and every change is written back to it
 * (debounced, off the calling thread). {@link #flushAutosave()} forces a synchronous write
 * and is called when the plugin unloads.
 *
 * Thread-safety: mutators are synchronized; listeners are invoked on the calling
 * thread, so UI listeners should hop to the main thread if needed.
 */
public final class PointDefenseRepository {
    public enum ChangeType {
        ITEM_ADDED, ITEM_UPDATED, ITEM_REMOVED, PLAN_REPLACED, PLAN_METADATA,
        /** R9: local per-asset visibility changed (plan content unchanged, nothing shared). */
        VISIBILITY
    }

    public interface PlanChangeListener {
        /** @param item affected item, or null for plan-level changes. */
        void onPlanChanged(ChangeType type, BasePlanItem item);
    }

    public static final String PLAN_FILE_EXTENSION = ".skiarktplan.json";
    /** Working copy that survives ATAK restarts and plugin reloads. */
    public static final String AUTOSAVE_FILE_NAME = "autosave.skiarkt.json";
    private static final String TAG = "SkiarktRepository";
    private static final long DEFAULT_AUTOSAVE_DELAY_MS = 1000;

    private static volatile PointDefenseRepository instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    private final List<PlanChangeListener> listeners = new CopyOnWriteArrayList<>();
    private PointDefensePlan plan = new PointDefensePlan();
    private File storageDir;
    private int cotStaleDays = 30;

    private final Object autosaveLock = new Object();
    private ScheduledExecutorService autosaveExecutor;
    private ScheduledFuture<?> pendingAutosave;
    private long autosaveDelayMs = DEFAULT_AUTOSAVE_DELAY_MS;

    private final VisibilityStore visibility = new VisibilityStore();

    private PointDefenseRepository() {
        visibility.setListener(new VisibilityStore.Listener() {
            @Override
            public void onVisibilityChanged() {
                notifyListeners(ChangeType.VISIBILITY, null);
            }
        });
    }

    /** R9 per-device visibility (never shared, never part of the plan JSON / CoT). */
    public VisibilityStore getVisibility() {
        return visibility;
    }

    public static PointDefenseRepository getInstance() {
        if (instance == null) {
            synchronized (PointDefenseRepository.class) {
                if (instance == null) instance = new PointDefenseRepository();
            }
        }
        return instance;
    }

    /** For unit tests only: discard the singleton. */
    static void resetForTests() {
        PointDefenseRepository old = instance;
        if (old != null) old.shutdownAutosave();
        instance = null;
    }

    /**
     * Sets the storage directory and restores the autosaved working plan, if any.
     * Safe to call again (e.g. after a plugin reload in the same process): the in-memory plan
     * is kept when it already has content.
     */
    public void init(File storageDir, int cotStaleDays) {
        boolean restore;
        synchronized (this) {
            this.storageDir = storageDir;
            this.cotStaleDays = cotStaleDays;
            if (storageDir != null && !storageDir.exists() && !storageDir.mkdirs()) {
                android.util.Log.w(TAG, "Could not create " + storageDir);
            }
            restore = storageDir != null && plan.totalItems() == 0;
        }
        visibility.attach(storageDir == null ? null : new File(storageDir, VisibilityStore.FILE_NAME));
        if (restore) restoreAutosave();
    }

    // -------------------------------------------------------------- autosave

    public synchronized File autosaveFile() {
        return storageDir == null ? null : new File(storageDir, AUTOSAVE_FILE_NAME);
    }

    /**
     * Loads the autosave file into the repository. Falls back to the leftover temp file if
     * the process died between delete and rename. A corrupt file is moved aside (never
     * silently overwritten) so it can be recovered by hand.
     *
     * @return true if a plan was restored.
     */
    boolean restoreAutosave() {
        File file = autosaveFile();
        if (file == null) return false;
        File tmp = new File(file.getPath() + ".tmp");
        File source = file.exists() ? file : (tmp.exists() ? tmp : null);
        if (source == null) return false;
        try {
            loadFromFile(source);
            android.util.Log.i(TAG, "Restored plan from " + source + " (" + getPlan().totalItems() + " items)");
            return true;
        } catch (IOException | RuntimeException e) {
            File aside = new File(source.getPath() + ".corrupt-" + System.currentTimeMillis());
            boolean moved = source.renameTo(aside);
            android.util.Log.e(TAG, "Could not restore " + source + (moved ? "; moved to " + aside : ""), e);
            return false;
        }
    }

    /** For tests: shorten the debounce. 0 = write on the next executor tick. */
    void setAutosaveDelayMs(long ms) {
        autosaveDelayMs = Math.max(0, ms);
    }

    private void scheduleAutosave() {
        if (autosaveFile() == null) return;
        synchronized (autosaveLock) {
            if (autosaveExecutor == null) {
                autosaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "skiarkt-autosave");
                    t.setDaemon(true);
                    return t;
                });
            }
            if (pendingAutosave != null) pendingAutosave.cancel(false);
            pendingAutosave = autosaveExecutor.schedule(this::writeAutosave, autosaveDelayMs,
                    TimeUnit.MILLISECONDS);
        }
    }

    private void writeAutosave() {
        File file = autosaveFile();
        if (file == null) return;
        // Serialise writers: a debounced background write may overlap a synchronous flush,
        // and both use the same .tmp file.
        synchronized (autosaveWriteLock) {
            try {
                saveToFile(file);
            } catch (IOException | RuntimeException e) {
                android.util.Log.e(TAG, "Autosave to " + file + " failed", e);
            }
        }
    }

    private final Object autosaveWriteLock = new Object();

    /** Cancels any pending debounced write and saves synchronously (call on unload). */
    public void flushAutosave() {
        synchronized (autosaveLock) {
            if (pendingAutosave == null) return;
            pendingAutosave.cancel(false);
            pendingAutosave = null;
        }
        writeAutosave();
    }

    /** Flushes pending changes and stops the autosave thread. */
    public void shutdownAutosave() {
        flushAutosave();
        synchronized (autosaveLock) {
            if (autosaveExecutor != null) autosaveExecutor.shutdown();
            autosaveExecutor = null;
        }
    }

    public synchronized File getStorageDir() {
        return storageDir;
    }

    public synchronized void setCotStaleDays(int days) {
        this.cotStaleDays = Math.max(1, days);
    }

    // ------------------------------------------------------------- listeners

    public void addListener(PlanChangeListener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(PlanChangeListener l) {
        listeners.remove(l);
    }

    private void notifyListeners(ChangeType type, BasePlanItem item) {
        if (type != ChangeType.VISIBILITY) scheduleAutosave();
        for (PlanChangeListener l : listeners) {
            try {
                l.onPlanChanged(type, item);
            } catch (RuntimeException e) {
                android.util.Log.e(TAG, "Listener failed", e);
            }
        }
    }

    // ------------------------------------------------------------------ plan

    public synchronized PointDefensePlan getPlan() {
        return plan;
    }

    /**
     * Starts an empty plan. If the current plan has content it is first archived to its named
     * plan file, so "New plan" never destroys work that was only in the autosave.
     */
    public void newPlan(String name, String author, String classification) {
        File archive = getPlan().totalItems() > 0 ? defaultPlanFile() : null;
        if (archive != null) {
            try {
                saveToFile(archive);
            } catch (IOException e) {
                android.util.Log.w(TAG, "Could not archive current plan to " + archive, e);
            }
        }
        PointDefensePlan p = new PointDefensePlan();
        if (name != null && !name.isEmpty()) p.name = name;
        p.author = author == null ? "" : author;
        if (classification != null && !classification.isEmpty()) p.classification = classification;
        replacePlan(p);
    }

    public void replacePlan(PointDefensePlan newPlan) {
        synchronized (this) {
            newPlan.ensureLists();
            plan = newPlan;
        }
        notifyListeners(ChangeType.PLAN_REPLACED, null);
    }

    public void updateMetadata(String name, String author, String classification, String description) {
        synchronized (this) {
            if (name != null) plan.name = name;
            if (author != null) plan.author = author;
            if (classification != null) plan.classification = classification;
            if (description != null) plan.description = description;
            plan.touch();
        }
        notifyListeners(ChangeType.PLAN_METADATA, null);
    }

    // ------------------------------------------------------------------ CRUD

    /** Adds the item, or replaces an existing item with the same UID. */
    public void upsert(BasePlanItem item) {
        if (item == null || item.uid == null) return;
        ChangeType change;
        synchronized (this) {
            List<BasePlanItem> list = plan.listFor(item.getKind());
            int idx = indexOf(list, item.uid);
            item.touch();
            if (idx >= 0) {
                list.set(idx, item);
                change = ChangeType.ITEM_UPDATED;
            } else {
                list.add(item);
                change = ChangeType.ITEM_ADDED;
            }
            plan.touch();
        }
        notifyListeners(change, item);
    }

    public boolean remove(String uid) {
        BasePlanItem removed = null;
        synchronized (this) {
            for (ItemKind k : ItemKind.values()) {
                List<BasePlanItem> list = plan.listFor(k);
                int idx = indexOf(list, uid);
                if (idx >= 0) {
                    removed = list.remove(idx);
                    plan.touch();
                    break;
                }
            }
        }
        if (removed != null) notifyListeners(ChangeType.ITEM_REMOVED, removed);
        return removed != null;
    }

    public synchronized BasePlanItem find(String uid) {
        return plan.findByUid(uid);
    }

    /** Snapshot copy of the items of one kind (safe to iterate off-lock). */
    public synchronized <T extends BasePlanItem> List<T> getItems(ItemKind kind) {
        return new ArrayList<>(plan.<T>listFor(kind));
    }

    private static int indexOf(List<? extends BasePlanItem> list, String uid) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uid.equals(uid)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ JSON

    public synchronized String toJson() {
        return gson.toJson(plan);
    }

    public PointDefensePlan parseJson(String json) {
        try {
            PointDefensePlan p = gson.fromJson(json, PointDefensePlan.class);
            if (p == null) throw new IOException("Empty plan document");
            p.ensureLists();
            return p;
        } catch (JsonParseException | IOException e) {
            throw new IllegalArgumentException("Invalid SKIARKT plan JSON: " + e.getMessage(), e);
        }
    }

    public File defaultPlanFile() {
        File dir = getStorageDir();
        if (dir == null) return null;
        String safe = getPlan().name.replaceAll("[^A-Za-z0-9_-]+", "_");
        return new File(dir, (safe.isEmpty() ? "plan" : safe) + PLAN_FILE_EXTENSION);
    }

    public void saveToFile(File file) throws IOException {
        String json = toJson();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        File tmp = new File(file.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write(json);
        }
        if (file.exists() && !file.delete()) throw new IOException("Cannot replace " + file);
        if (!tmp.renameTo(file)) throw new IOException("Cannot rename " + tmp + " -> " + file);
    }

    public void loadFromFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        }
        try {
            replacePlan(parseJson(sb.toString()));
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------- CoT

    public synchronized CotEvent toCot(BasePlanItem item) {
        return CotSerializer.toCot(item, plan.uid, cotStaleDays);
    }

    public synchronized List<CotEvent> toCotEvents() {
        return CotSerializer.planToCotEvents(plan, cotStaleDays);
    }

    /**
     * Ingests a received CoT event (from TAK Server, mesh, WinTAK or a data package).
     * Plan header events update metadata; item events are upserted.
     *
     * @return true if the event was a SKIARKT event and was applied.
     */
    public boolean ingestCot(CotEvent ev) {
        if (ev == null || !CotTypes.isSkiarktType(ev.getType())) return false;
        if (CotTypes.PLAN.equals(ev.getType())) {
            PointDefensePlan meta = CotSerializer.planFromCot(ev);
            if (meta == null) return false;
            synchronized (this) {
                if (!meta.uid.equals(plan.uid) && plan.totalItems() == 0) {
                    plan.uid = meta.uid; // adopt the incoming plan identity
                }
            }
            if (meta.uid.equals(getPlan().uid)) {
                updateMetadata(meta.name, meta.author, meta.classification, meta.description);
            }
            return true;
        }
        BasePlanItem item = CotSerializer.fromCot(ev);
        if (item == null) return false;
        long incomingModified = item.modifiedMillis;
        BasePlanItem existing = find(item.uid);
        if (existing != null && existing.modifiedMillis > incomingModified) {
            return false; // local copy is newer
        }
        upsert(item);
        item.modifiedMillis = incomingModified; // keep the originator's timestamp
        return true;
    }

    /** Shares a single item with the network. */
    public boolean share(BasePlanItem item) {
        return CotDispatchHelper.dispatchExternal(toCot(item));
    }

    /** Shares the entire plan (header + every item). @return events sent. */
    public int sharePlan() {
        return CotDispatchHelper.broadcastAll(toCotEvents());
    }
}
