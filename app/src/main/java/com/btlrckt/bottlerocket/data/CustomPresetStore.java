package com.btlrckt.bottlerocket.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.btlrckt.bottlerocket.model.SensorPreset;
import com.btlrckt.bottlerocket.model.SensorPresetDef;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * R10 sensor preset catalog: local edits of the built-in radar presets (overrides keyed by
 * {@link SensorPreset#name()}) plus user-added presets. Saved to {@link #FILE_NAME} beside the
 * autosave; a device setting, never part of the plan JSON or CoT. Thread-safe.
 */
public final class CustomPresetStore {
    public static final String FILE_NAME = "sensor_presets.btlrckt.json";

    /** Serialised form. */
    static final class State {
        Map<String, SensorPresetDef> overrides = new LinkedHashMap<>();
        List<SensorPresetDef> customs = new ArrayList<>();
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    private final Map<String, SensorPresetDef> overrides = new LinkedHashMap<>();
    private final List<SensorPresetDef> customs = new ArrayList<>();
    private File file;

    /** Sets the backing file and loads it (missing / corrupt = built-ins only). */
    public synchronized void attach(File f) {
        file = f;
        overrides.clear();
        customs.clear();
        if (f == null || !f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            State s = gson.fromJson(r, State.class);
            if (s == null) return;
            if (s.overrides != null) {
                for (Map.Entry<String, SensorPresetDef> e : s.overrides.entrySet()) {
                    SensorPresetDef d = e.getValue();
                    if (d == null || builtIn(e.getKey()) == null) continue;
                    d.id = e.getKey();
                    if (d.isValid()) overrides.put(e.getKey(), d);
                }
            }
            if (s.customs != null) {
                for (SensorPresetDef d : s.customs) if (d != null && d.isValid() && !d.isBuiltIn()) customs.add(d);
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            overrides.clear();
            customs.clear();
        }
    }

    /** Built-ins in enum order (with local overrides applied), then custom presets. Copies. */
    public synchronized List<SensorPresetDef> getMergedList() {
        List<SensorPresetDef> out = new ArrayList<>();
        for (SensorPreset p : SensorPreset.values()) {
            SensorPresetDef o = overrides.get(p.name());
            out.add(o != null ? o.copy() : SensorPresetDef.fromEnum(p));
        }
        for (SensorPresetDef d : customs) out.add(d.copy());
        return out;
    }

    public synchronized boolean isOverridden(String id) {
        return id != null && overrides.containsKey(id);
    }

    public synchronized int customCount() {
        return customs.size();
    }

    /** Stores an edit of a built-in ({@code id} = enum name) or updates an existing custom preset. */
    public boolean saveOverride(String id, SensorPresetDef def) {
        if (id == null || def == null) return false;
        SensorPresetDef d = def.copy();
        d.id = id;
        if (!d.isValid()) return false;
        synchronized (this) {
            if (builtIn(id) != null) {
                overrides.put(id, d);
            } else {
                int i = indexOfCustom(id);
                if (i < 0) return false;
                customs.set(i, d);
            }
        }
        save();
        return true;
    }

    public void removeOverride(String id) {
        boolean changed;
        synchronized (this) {
            changed = id != null && overrides.remove(id) != null;
        }
        if (changed) save();
    }

    /** Built-in back to its data-sheet values (drops the local override). */
    public void resetToDefault(String id) {
        removeOverride(id);
    }

    /** Adds a user preset; assigns a new "custom-..." id when missing / clashing. Returns the id or null. */
    public String addCustom(SensorPresetDef def) {
        if (def == null) return null;
        SensorPresetDef d = def.copy();
        synchronized (this) {
            if (d.id == null || d.isBuiltIn() || indexOfCustom(d.id) >= 0) {
                d.id = SensorPresetDef.CUSTOM_PREFIX + UUID.randomUUID().toString().substring(0, 8);
            }
            if (!d.isValid()) return null;
            customs.add(d);
        }
        save();
        return d.id;
    }

    public void removeCustom(String id) {
        boolean changed;
        synchronized (this) {
            int i = indexOfCustom(id);
            changed = i >= 0;
            if (changed) customs.remove(i);
        }
        if (changed) save();
    }

    private int indexOfCustom(String id) {
        if (id == null) return -1;
        for (int i = 0; i < customs.size(); i++) if (id.equals(customs.get(i).id)) return i;
        return -1;
    }

    private static SensorPreset builtIn(String id) {
        if (id == null) return null;
        for (SensorPreset p : SensorPreset.values()) if (p.name().equals(id)) return p;
        return null;
    }

    /** Writes the state atomically (tmp + rename); failures are non-fatal. */
    synchronized void save() {
        if (file == null) return;
        State s = new State();
        s.overrides = new LinkedHashMap<>(overrides);
        s.customs = new ArrayList<>(customs);
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
