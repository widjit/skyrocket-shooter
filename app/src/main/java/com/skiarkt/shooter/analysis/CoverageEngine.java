package com.skiarkt.shooter.analysis;

import com.skiarkt.shooter.model.NonKineticEffector;
import com.skiarkt.shooter.model.SensorItem;
import com.skiarkt.shooter.util.ElevationSource;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Background computation + cache for sensor terrain masks ({@link LosSweep}) and RF heat maps
 * ({@link RfHeatmap}). DTED sweeps take thousands of elevation lookups, so they run on a
 * single worker thread; callers ask for a result and get either the cached result for the
 * exact current inputs or null (computation scheduled, {@link Listener#onResultReady} fires
 * when done - typically the overlay manager then re-renders on the map thread).
 *
 * <p>Cache keys contain every input that affects the result (position, heights, range,
 * beam, steps, RF parameters), so any parameter edit triggers a recompute.
 */
public class CoverageEngine {

    public interface Listener {
        /** Called on the worker thread. */
        void onResultReady(String itemUid);
    }

    /** Settings-derived sweep parameters. */
    public static final class SweepParams {
        public final double angularStepDeg, rangeStepM, targetAglM;

        public SweepParams(double angularStepDeg, double rangeStepM, double targetAglM) {
            this.angularStepDeg = angularStepDeg;
            this.rangeStepM = rangeStepM;
            this.targetAglM = targetAglM;
        }
    }

    /** Heat map result: raster + the viewshed it used. */
    public static final class HeatmapResult {
        public final RfHeatmap.Raster raster;
        public final LosSweep.Result los;
        public final double thresholdDbm;

        HeatmapResult(RfHeatmap.Raster raster, LosSweep.Result los, double thresholdDbm) {
            this.raster = raster;
            this.los = los;
            this.thresholdDbm = thresholdDbm;
        }
    }

    private static final class Entry<T> {
        final String key;
        final T value;

        Entry(String key, T value) {
            this.key = key;
            this.value = value;
        }
    }

    private final ElevationSource source;
    private final Listener listener;
    private final ExecutorService executor;
    private final Map<String, Entry<LosSweep.Result>> sensorCache = new ConcurrentHashMap<>();
    private final Map<String, Entry<HeatmapResult>> heatCache = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    /** Most recently requested key per item; superseded queued jobs are skipped. */
    private final Map<String, String> latestKey = new ConcurrentHashMap<>();
    private volatile boolean shutdown;

    public CoverageEngine(ElevationSource source, Listener listener) {
        this.source = source;
        this.listener = listener;
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "skiarkt-coverage");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        });
    }

    public static String sensorKey(SensorItem s, SweepParams p) {
        return String.format(Locale.US, "S|%.7f|%.7f|%.2f|%.1f|%.2f|%.2f|%.3f|%.2f|%.2f",
                s.position.lat, s.position.lon, s.aglMeters, s.rangeMeters, s.beamWidthDeg, s.azimuthDeg,
                p.angularStepDeg, p.rangeStepM, p.targetAglM);
    }

    public static String heatKey(NonKineticEffector n, SweepParams p, RfHeatmap.Params rf) {
        return String.format(Locale.US, "N|%.7f|%.7f|%.2f|%.1f|%.3f|%.2f|%.2f|%.2f|%.2f|%.3f|%.2f|%.2f",
                n.position.lat, n.position.lon, n.aglMeters, rf.radiusM, rf.txDbm, rf.frequencyMhz,
                rf.thresholdDbm, rf.terrainLossDb, rf.cellM, p.angularStepDeg, p.rangeStepM, p.targetAglM);
    }

    /** Cached terrain mask for the sensor's current parameters, or null (scheduled). */
    public LosSweep.Result getSensorMask(SensorItem sensor, final SweepParams p) {
        if (sensor == null || !sensor.isValid()) return null;
        final String key = sensorKey(sensor, p);
        Entry<LosSweep.Result> e = sensorCache.get(sensor.uid);
        if (e != null && e.key.equals(key)) return e.value;
        final SensorItem s = sensor.copy();
        schedule(key, s.uid, new Runnable() {
            @Override
            public void run() {
                LosSweep.Result r = LosSweep.compute(source, s.position, s.aglMeters, p.targetAglM,
                        s.rangeMeters, s.azimuthDeg, s.beamWidthDeg, p.angularStepDeg, p.rangeStepM);
                sensorCache.put(s.uid, new Entry<>(key, r));
            }
        });
        return null;
    }

    /** Cached heat map for the effector's current parameters, or null (scheduled). */
    public HeatmapResult getHeatmap(NonKineticEffector effector, final SweepParams p, final RfHeatmap.Params rf) {
        if (effector == null || !effector.isValid()) return null;
        final String key = heatKey(effector, p, rf);
        Entry<HeatmapResult> e = heatCache.get(effector.uid);
        if (e != null && e.key.equals(key)) return e.value;
        final NonKineticEffector n = effector.copy();
        schedule(key, n.uid, new Runnable() {
            @Override
            public void run() {
                LosSweep.Result los = LosSweep.compute(source, n.position, n.aglMeters, p.targetAglM,
                        rf.radiusM, 0, 360, p.angularStepDeg, p.rangeStepM);
                RfHeatmap.Raster raster = RfHeatmap.compute(n.position, rf, los);
                heatCache.put(n.uid, new Entry<>(key, new HeatmapResult(raster, los, rf.thresholdDbm)));
            }
        });
        return null;
    }

    /** True while a result for {@code uid} is being computed. */
    public boolean isPending(String uid) {
        for (String k : pending) if (k.endsWith("#" + uid)) return true;
        return false;
    }

    /** Drops cached results for items no longer in the plan. */
    public void retainOnly(Collection<String> uids) {
        sensorCache.keySet().retainAll(uids);
        heatCache.keySet().retainAll(uids);
        latestKey.keySet().retainAll(uids);
    }

    public void shutdown() {
        shutdown = true;
        executor.shutdownNow();
        sensorCache.clear();
        heatCache.clear();
        pending.clear();
    }

    private void schedule(final String key, final String uid, final Runnable work) {
        if (shutdown) return;
        final String pk = key + "#" + uid;
        latestKey.put(uid, key);
        if (!pending.add(pk)) return;
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!key.equals(latestKey.get(uid))) return; // superseded by a newer edit
                        work.run();
                    } catch (RuntimeException ex) {
                        // keep the worker alive; the item simply renders without terrain data
                        System.err.println("SKIARKT coverage computation failed for " + uid + ": " + ex);
                    } finally {
                        pending.remove(pk);
                    }
                    if (!shutdown && listener != null) listener.onResultReady(uid);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            pending.remove(pk);
        }
    }
}
