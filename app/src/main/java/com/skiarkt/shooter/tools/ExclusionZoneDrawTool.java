package com.skiarkt.shooter.tools;

import android.graphics.PointF;
import android.util.Log;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.skiarkt.shooter.model.GeoPos;
import com.skiarkt.shooter.model.PlanColors;

import java.util.List;
import java.util.UUID;

/**
 * Modal map draw tool for exclusion-zone footprints ({@link Mode#POLYGON}) and ad-hoc
 * ingress lines for the intercept altitude calculator ({@link Mode#LINE}).
 *
 * <p>While active, the tool pushes the {@link MapEventDispatcher} listener stack and takes
 * over single-tap events only ({@code MAP_CLICK} / {@code ITEM_CLICK}); pan, pinch-zoom and
 * rotate gestures never reach these listeners, so normal map navigation keeps working.
 * Default tap / long-press behaviour (radial menus, marker placement) is suspended until
 * {@link #closePolygon()}, {@link #cancel()} or line completion pops the stack again.
 *
 * <p>Each tap adds a vertex; a live preview polyline (closed and lightly filled once three
 * vertices exist) shows the shape. Tapping the first vertex or calling {@link #closePolygon()}
 * finishes a polygon; a line finishes automatically on its second tap. Must be used on the
 * UI thread (ATAK dispatches map events there).
 */
public class ExclusionZoneDrawTool implements MapEventDispatcher.MapEventDispatchListener {
    private static final String TAG = "SkiarktDrawTool";

    public enum Mode { POLYGON, LINE }

    public interface Listener {
        /** Vertex added or removed; {@code canFinish} = enough vertices to close / finish. */
        void onVerticesChanged(int count, boolean canFinish);

        /** Polygon finished (open ring of >= 3 vertices). */
        void onPolygonClosed(List<GeoPos> vertices);

        /** Line finished. */
        void onLineComplete(GeoPos start, GeoPos end);

        void onDrawCancelled();
    }

    /** Taken-over map events (defaults restored by popListeners). */
    private static final String[] SUSPENDED_EVENTS = {
            MapEvent.MAP_CLICK, MapEvent.MAP_LONG_PRESS, MapEvent.MAP_CONFIRMED_CLICK,
            MapEvent.ITEM_CLICK, MapEvent.ITEM_LONG_PRESS
    };

    private final MapView mapView;
    private final MapGroup previewGroup;
    private Mode mode;
    private PolygonDrawState state;
    private Listener listener;
    private int color = PlanColors.EXCLUSION_ZONE;
    private boolean active;
    private Polyline preview;
    private Marker firstVertexMarker;

    public ExclusionZoneDrawTool(MapView mapView, MapGroup previewGroup) {
        this.mapView = mapView;
        this.previewGroup = previewGroup;
    }

    public boolean isActive() {
        return active;
    }

    public Mode getMode() {
        return mode;
    }

    public int getVertexCount() {
        return state == null ? 0 : state.size();
    }

    /** Enters draw mode. Any draw already in progress is cancelled first. */
    public boolean begin(Mode mode, int argbColor, Listener listener) {
        if (active) cancel();
        MapEventDispatcher dispatcher = mapView == null ? null : mapView.getMapEventDispatcher();
        if (dispatcher == null) {
            Log.w(TAG, "Map event dispatcher unavailable; cannot start draw mode");
            return false;
        }
        this.mode = mode;
        this.listener = listener;
        this.color = argbColor;
        this.state = new PolygonDrawState(mode == Mode.LINE ? 2 : 3);
        dispatcher.pushListeners();
        for (String type : SUSPENDED_EVENTS) dispatcher.clearListeners(type);
        dispatcher.addMapEventListener(MapEvent.MAP_CLICK, this);
        dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, this);
        active = true;
        updatePreview();
        notifyChanged();
        return true;
    }

    /** Programmatic vertex placement (also used by the tap handler). */
    public boolean addVertex(GeoPos p) {
        if (!active || !state.add(p)) return false;
        if (mode == Mode.LINE && state.size() >= 2) {
            List<GeoPos> v = state.snapshot();
            Listener l = listener;
            finish();
            if (l != null) l.onLineComplete(v.get(0), v.get(1));
            return true;
        }
        updatePreview();
        notifyChanged();
        return true;
    }

    public boolean undoLastVertex() {
        if (!active || !state.undo()) return false;
        updatePreview();
        notifyChanged();
        return true;
    }

    /** Finishes the polygon; returns false if fewer than three vertices were placed. */
    public boolean closePolygon() {
        if (!active || mode != Mode.POLYGON || !state.canFinish()) return false;
        List<GeoPos> v = state.snapshot();
        Listener l = listener;
        finish();
        if (l != null) l.onPolygonClosed(v);
        return true;
    }

    public void cancel() {
        if (!active) return;
        Listener l = listener;
        finish();
        if (l != null) l.onDrawCancelled();
    }

    private void finish() {
        active = false;
        MapEventDispatcher dispatcher = mapView == null ? null : mapView.getMapEventDispatcher();
        if (dispatcher != null) {
            dispatcher.removeMapEventListener(MapEvent.MAP_CLICK, this);
            dispatcher.removeMapEventListener(MapEvent.ITEM_CLICK, this);
            dispatcher.popListeners();
        }
        clearPreview();
        listener = null;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (!active || event == null) return;
        String type = event.getType();
        if (MapEvent.ITEM_CLICK.equals(type)) {
            MapItem item = event.getItem();
            if (item != null && firstVertexMarker != null
                    && item.getUID().equals(firstVertexMarker.getUID()) && closePolygon()) {
                return;
            }
        }
        if (MapEvent.MAP_CLICK.equals(type) || MapEvent.ITEM_CLICK.equals(type)) {
            GeoPos p = toGeo(event.getPointF());
            if (p != null) addVertex(p);
        }
    }

    private GeoPos toGeo(PointF pt) {
        if (pt == null) return null;
        GeoPointMetaData gpm = mapView.inverseWithElevation(pt.x, pt.y);
        GeoPoint gp = gpm == null ? null : gpm.get();
        if (gp == null || !gp.isValid()) return null;
        // Keep only the horizontal position; zone heights are AGL and resolved via DTED.
        return new GeoPos(gp.getLatitude(), gp.getLongitude());
    }

    private void notifyChanged() {
        if (listener != null) listener.onVerticesChanged(state.size(), state.canFinish());
    }

    // ------------------------------------------------------------- live preview

    private void updatePreview() {
        if (previewGroup == null) return;
        List<GeoPos> v = state.getVertices();
        if (preview == null) {
            preview = new Polyline("skiarkt-draw-preview-" + UUID.randomUUID());
            preview.setMetaBoolean("nevercot", true);
            preview.setMetaBoolean("addToObjList", false);
            preview.setClickable(false);
            preview.setStrokeWeight(3d);
            preview.setBasicLineStyle(Polyline.BASIC_LINE_STYLE_DASHED);
            previewGroup.addItem(preview);
        }
        GeoPointMetaData[] pts = new GeoPointMetaData[v.size()];
        for (int i = 0; i < pts.length; i++) pts[i] = GeoPointMetaData.wrap(v.get(i).toGeoPoint());
        preview.setPoints(pts);
        preview.setStrokeColor(PlanColors.withAlpha(color, 1f));
        boolean closed = mode == Mode.POLYGON && v.size() >= 3;
        preview.setStyle(Shape.STYLE_STROKE_MASK
                | (closed ? Polyline.STYLE_CLOSED_MASK | Shape.STYLE_FILLED_MASK : 0));
        preview.setFillColor(PlanColors.withAlpha(color, 0.15f));

        if (!v.isEmpty() && mode == Mode.POLYGON) {
            if (firstVertexMarker == null) {
                firstVertexMarker = new Marker(v.get(0).toGeoPoint(), "skiarkt-draw-first-" + UUID.randomUUID());
                firstVertexMarker.setType("b-m-p-s-p-i");
                firstVertexMarker.setMetaBoolean("nevercot", true);
                firstVertexMarker.setMetaBoolean("addToObjList", false);
                firstVertexMarker.setTitle("Tap to close");
                previewGroup.addItem(firstVertexMarker);
            }
            firstVertexMarker.setPoint(v.get(0).toGeoPoint());
            firstVertexMarker.setVisible(state.canFinish());
        } else if (firstVertexMarker != null) {
            previewGroup.removeItem(firstVertexMarker);
            firstVertexMarker = null;
        }
    }

    private void clearPreview() {
        if (previewGroup == null) return;
        if (preview != null) previewGroup.removeItem(preview);
        if (firstVertexMarker != null) previewGroup.removeItem(firstVertexMarker);
        preview = null;
        firstVertexMarker = null;
    }
}
