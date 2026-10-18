package com.skiarkt.shooter.overlay;

import android.content.SharedPreferences;
import android.util.Log;

import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.skiarkt.shooter.analysis.CoverageEngine;
import com.skiarkt.shooter.analysis.InterceptAltitudeResult;
import com.skiarkt.shooter.analysis.LosSweep;
import com.skiarkt.shooter.analysis.RfHeatmap;
import com.skiarkt.shooter.data.PointDefenseRepository;
import com.skiarkt.shooter.model.BasePlanItem;
import com.skiarkt.shooter.model.EngagementAuthorityZone;
import com.skiarkt.shooter.model.ExclusionZone;
import com.skiarkt.shooter.model.ItemKind;
import com.skiarkt.shooter.model.KineticEffector;
import com.skiarkt.shooter.model.NonKineticEffector;
import com.skiarkt.shooter.model.SensorItem;
import com.skiarkt.shooter.model.SiteGroups;
import com.skiarkt.shooter.analysis.CoverageGeometry;
import com.skiarkt.shooter.data.VisibilityStore;
import com.skiarkt.shooter.settings.SettingsManager;
import com.skiarkt.shooter.util.AtakElevationSource;
import com.skiarkt.shooter.util.ElevationSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the plugin's map layers. Creates a root "SKY ROCKET SHOOTER" {@link MapGroup}
 * with one child group per item kind; ATAK's GL pipeline renders every map item placed
 * in these groups (GLMapItem2 renderers are bound automatically), and the groups appear
 * in Overlay Manager for visibility toggling.
 *
 * Two extra child groups are never cleared by {@link #refreshAll()}: "Drawing" (live
 * preview of the modal draw tool) and "Analysis" (intercept altitude annotation).
 *
 * Phase 2: exclusion zones render via {@link ExclusionZoneOverlay}.
 * Phase 3: sensors ({@link SensorOverlay}, DTED-masked coverage) and NK effectors
 * ({@link RfHeatmapOverlay}); terrain sweeps / heat maps are computed off the map thread by
 * {@link CoverageEngine} and the affected layer is redrawn when a result arrives.
 */
public class OverlayManager implements PointDefenseRepository.PlanChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "SkiarktOverlayManager";
    public static final String ROOT_GROUP_NAME = "SKY ROCKET SHOOTER";
    public static final String DRAWING_GROUP_NAME = "Drawing";
    public static final String ANALYSIS_GROUP_NAME = "Analysis";
    public static final String SITES_GROUP_NAME = "Sites";
    public static final String SITE_RING_PREFIX = "skiarkt.site.";
    static final int SITE_RING_COLOR = 0xFFB0BEC5;

    private final MapView mapView;
    private final PointDefenseRepository repository;
    private final SettingsManager settings;
    private final Map<ItemKind, MapGroup> kindGroups = new EnumMap<>(ItemKind.class);
    private final ExclusionZoneOverlay zoneOverlay;
    private final SensorOverlay sensorOverlay;
    private final RfHeatmapOverlay heatmapOverlay;
    private final KineticEffectorOverlay kineticOverlay;
    private final AuthorityZoneOverlay authorityOverlay;
    private final CoverageEngine coverageEngine;
    private MapGroup rootGroup;
    private MapGroup drawingGroup;
    private MapGroup analysisGroup;
    private MapGroup sitesGroup;
    private InterceptAltitudeResult interceptResult;
    private boolean started;

    public OverlayManager(MapView mapView, PointDefenseRepository repository, SettingsManager settings) {
        this(mapView, repository, settings, new AtakElevationSource());
    }

    public OverlayManager(MapView mapView, PointDefenseRepository repository, SettingsManager settings,
            ElevationSource elevationSource) {
        this.mapView = mapView;
        this.repository = repository;
        this.settings = settings;
        this.zoneOverlay = new ExclusionZoneOverlay(settings);
        this.sensorOverlay = new SensorOverlay(settings);
        this.heatmapOverlay = new RfHeatmapOverlay(settings);
        this.kineticOverlay = new KineticEffectorOverlay(settings);
        this.authorityOverlay = new AuthorityZoneOverlay(settings);
        this.coverageEngine = new CoverageEngine(elevationSource, new CoverageEngine.Listener() {
            @Override
            public void onResultReady(final String itemUid) {
                OverlayManager.this.mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshItem(itemUid);
                    }
                });
            }
        });
    }

    public void start() {
        if (started) return;
        started = true;
        repository.addListener(this);
        settings.registerListener(this);
        mapView.post(new Runnable() {
            @Override
            public void run() {
                attachGroups();
                refreshAll();
            }
        });
    }

    public void stop() {
        if (!started) return;
        started = false;
        repository.removeListener(this);
        settings.unregisterListener(this);
        coverageEngine.shutdown();
        mapView.post(new Runnable() {
            @Override
            public void run() {
                detachGroups();
            }
        });
    }

    private void attachGroups() {
        MapGroup mapRoot = mapView.getRootGroup();
        if (mapRoot == null) {
            Log.w(TAG, "Map root group unavailable; overlays disabled");
            return;
        }
        rootGroup = new DefaultMapGroup(ROOT_GROUP_NAME);
        for (ItemKind kind : ItemKind.values()) {
            MapGroup g = new DefaultMapGroup(kind.displayName + "s");
            kindGroups.put(kind, g);
            rootGroup.addGroup(g);
        }
        drawingGroup = new DefaultMapGroup(DRAWING_GROUP_NAME);
        analysisGroup = new DefaultMapGroup(ANALYSIS_GROUP_NAME);
        sitesGroup = new DefaultMapGroup(SITES_GROUP_NAME);
        rootGroup.addGroup(sitesGroup);
        rootGroup.addGroup(analysisGroup);
        rootGroup.addGroup(drawingGroup);
        mapRoot.addGroup(rootGroup);
        Log.d(TAG, "Overlay groups attached");
    }

    private void detachGroups() {
        MapGroup mapRoot = mapView.getRootGroup();
        if (rootGroup != null) {
            rootGroup.clearGroups();
            rootGroup.clearItems();
            if (mapRoot != null) mapRoot.removeGroup(rootGroup);
        }
        kindGroups.clear();
        rootGroup = null;
        drawingGroup = null;
        analysisGroup = null;
        sitesGroup = null;
    }

    public MapGroup getGroup(ItemKind kind) {
        return kindGroups.get(kind);
    }

    /** Group for transient draw-tool preview items (null until attached). */
    public MapGroup getDrawingGroup() {
        return drawingGroup;
    }

    public MapGroup getAnalysisGroup() {
        return analysisGroup;
    }

    /** Shows (replacing any previous) intercept altitude annotation. Map thread only. */
    public void showInterceptResult(InterceptAltitudeResult result) {
        interceptResult = result;
        redrawIntercept();
    }

    public void clearInterceptResult() {
        interceptResult = null;
        redrawIntercept();
    }

    private void redrawIntercept() {
        if (analysisGroup == null) return;
        analysisGroup.clearItems();
        if (interceptResult != null) zoneOverlay.renderIntercept(analysisGroup, interceptResult);
    }

    public void setVisible(boolean visible) {
        if (rootGroup != null) rootGroup.setVisible(visible);
    }

    @Override
    public void onPlanChanged(PointDefenseRepository.ChangeType type, BasePlanItem item) {
        mapView.post(new Runnable() {
            @Override
            public void run() {
                refreshAll();
            }
        });
    }

    /** Preference keys whose change alters rendered layers. */
    static final Set<String> RENDER_KEYS = new HashSet<>(Arrays.asList(
            SettingsManager.KEY_UNIT_SYSTEM, SettingsManager.KEY_PRECISION,
            SettingsManager.KEY_HEATMAP_TRANSPARENCY, SettingsManager.KEY_RF_FREQUENCY_MHZ,
            SettingsManager.KEY_RF_TERRAIN_LOSS_DB, SettingsManager.KEY_RF_THRESHOLD_DBM,
            SettingsManager.KEY_LOS_RANGE_STEP_M, SettingsManager.KEY_LOS_ANGULAR_STEP_DEG,
            SettingsManager.KEY_LOS_TARGET_AGL_M));

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        // Units / precision alter callouts; RF / LOS keys alter coverage + heat maps.
        if (key == null || RENDER_KEYS.contains(key)) {
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    refreshAll();
                    redrawIntercept();
                }
            });
        }
    }

    /** Rebuilds every layer from the repository (must run on the map thread). */
    public void refreshAll() {
        if (rootGroup == null) return;
        Set<String> uids = new HashSet<>();
        for (ItemKind kind : ItemKind.values()) {
            for (BasePlanItem item : repository.getItems(kind)) uids.add(item.uid);
        }
        coverageEngine.retainOnly(uids);
        repository.getVisibility().retainOnly(uids);
        for (ItemKind kind : ItemKind.values()) {
            MapGroup g = kindGroups.get(kind);
            if (g == null) continue;
            g.clearItems();
            for (BasePlanItem item : repository.getItems(kind)) {
                renderItem(g, item);
            }
        }
        renderSites();
    }

    /**
     * R0: a subtle dashed ring around every site group's members, labelled with the site summary.
     * Drawn while at least one member is visible (R9).
     */
    void renderSites() {
        if (sitesGroup == null) return;
        sitesGroup.clearItems();
        VisibilityStore vis = repository.getVisibility();
        for (SiteGroups.Site site : SiteGroups.build(repository.getPlan()).values()) {
            if (vis.hiddenCount(site.memberUids()) >= site.members.size()) continue;
            com.skiarkt.shooter.model.GeoPos c = site.center();
            if (c == null) continue;
            double r = site.ringRadiusMeters();
            try {
                sitesGroup.addItem(OverlayItems.polygon(SITE_RING_PREFIX + site.uid, null,
                        CoverageGeometry.circle(c, r, CoverageGeometry.DEFAULT_ARC_STEP_DEG), 0,
                        OverlayItems.withAlpha(SITE_RING_COLOR, 0.8f), 1.5d, true));
                sitesGroup.addItem(OverlayItems.label(SITE_RING_PREFIX + site.uid + ".label", null,
                        com.skiarkt.shooter.util.GeoUtils.destination(c, 0.0, r), site.summary(),
                        SITE_RING_COLOR, false));
            } catch (RuntimeException e) {
                Log.e(TAG, "Failed to render site " + site.uid, e);
            }
        }
    }

    /** R9: true when the item should be drawn on this device (analysis ignores this). */
    public boolean isDrawn(BasePlanItem item) {
        return item != null && repository.getVisibility().isVisible(item.uid);
    }

    /** Re-renders the layer holding {@code uid} after a background result (map thread). */
    void refreshItem(String uid) {
        if (rootGroup == null) return;
        BasePlanItem item = repository.find(uid);
        if (item == null) return;
        MapGroup g = kindGroups.get(item.getKind());
        if (g == null) return;
        g.clearItems();
        List<BasePlanItem> items = repository.getItems(item.getKind());
        for (BasePlanItem it : items) renderItem(g, it);
    }

    public CoverageEngine.SweepParams sweepParams() {
        return new CoverageEngine.SweepParams(settings.getLosAngularStepDeg(), settings.getLosRangeStepMeters(),
                settings.getLosTargetAglMeters());
    }

    public RfHeatmap.Params heatmapParams(NonKineticEffector n) {
        return RfHeatmap.forEffector(n, settings.getRfFrequencyMhz(), settings.getRfThresholdDbm(),
                settings.getRfTerrainLossDb());
    }

    /** Cached sensor terrain mask (null while computing); used by the sensor panel stats. */
    public LosSweep.Result getSensorMask(SensorItem s) {
        return coverageEngine.getSensorMask(s, sweepParams());
    }

    /** Cached heat map (null while computing); used by the NK effector panel stats. */
    public CoverageEngine.HeatmapResult getHeatmap(NonKineticEffector n) {
        return coverageEngine.getHeatmap(n, sweepParams(), heatmapParams(n));
    }

    /** Creates the ATAK map items for {@code item} inside {@code group}. */
    protected void renderItem(MapGroup group, BasePlanItem item) {
        // R9: hidden assets are not drawn, but stay in the repository and in every analysis.
        if (!isDrawn(item)) return;
        try {
            if (item instanceof ExclusionZone) {
                zoneOverlay.render(group, (ExclusionZone) item);
            } else if (item instanceof SensorItem) {
                SensorItem s = (SensorItem) item;
                LosSweep.Result mask = getSensorMask(s);
                sensorOverlay.render(group, s, mask, mask == null && coverageEngine.isPending(s.uid),
                        repository.getVisibility());
            } else if (item instanceof NonKineticEffector) {
                NonKineticEffector n = (NonKineticEffector) item;
                CoverageEngine.HeatmapResult heat = getHeatmap(n);
                heatmapOverlay.render(group, n, heat, heat == null && coverageEngine.isPending(n.uid),
                        repository.getVisibility());
            } else if (item instanceof KineticEffector) {
                kineticOverlay.render(group, (KineticEffector) item, repository.getVisibility());
            } else if (item instanceof EngagementAuthorityZone) {
                authorityOverlay.render(group, (EngagementAuthorityZone) item, repository.getPlan());
            }
            // Other kinds: later phases.
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to render " + item, e);
        }
    }
}
