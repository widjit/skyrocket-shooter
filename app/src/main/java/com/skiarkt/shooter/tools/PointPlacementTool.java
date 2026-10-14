package com.skiarkt.shooter.tools;

import android.graphics.PointF;
import android.util.Log;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.skiarkt.shooter.model.GeoPos;

/**
 * Modal single-tap placement tool (sensor / NK effector placement mode). Pushes the map
 * event listener stack, takes over taps only (pan / zoom keep working) and reports the first
 * tapped location, then restores ATAK's default tap handling. UI thread only.
 */
public class PointPlacementTool implements MapEventDispatcher.MapEventDispatchListener {
    private static final String TAG = "SkiarktPlacementTool";

    public interface Listener {
        void onPlaced(GeoPos position);

        void onPlacementCancelled();
    }

    private static final String[] SUSPENDED_EVENTS = {
            MapEvent.MAP_CLICK, MapEvent.MAP_LONG_PRESS, MapEvent.MAP_CONFIRMED_CLICK,
            MapEvent.ITEM_CLICK, MapEvent.ITEM_LONG_PRESS
    };

    private final MapView mapView;
    private Listener listener;
    private boolean active;

    public PointPlacementTool(MapView mapView) {
        this.mapView = mapView;
    }

    public boolean isActive() {
        return active;
    }

    public boolean begin(Listener l) {
        if (active) cancel();
        MapEventDispatcher dispatcher = mapView == null ? null : mapView.getMapEventDispatcher();
        if (dispatcher == null) {
            Log.w(TAG, "Map event dispatcher unavailable; cannot start placement mode");
            return false;
        }
        listener = l;
        dispatcher.pushListeners();
        for (String type : SUSPENDED_EVENTS) dispatcher.clearListeners(type);
        dispatcher.addMapEventListener(MapEvent.MAP_CLICK, this);
        dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, this);
        active = true;
        return true;
    }

    public void cancel() {
        if (!active) return;
        Listener l = listener;
        finish();
        if (l != null) l.onPlacementCancelled();
    }

    private void finish() {
        active = false;
        MapEventDispatcher dispatcher = mapView == null ? null : mapView.getMapEventDispatcher();
        if (dispatcher != null) {
            dispatcher.removeMapEventListener(MapEvent.MAP_CLICK, this);
            dispatcher.removeMapEventListener(MapEvent.ITEM_CLICK, this);
            dispatcher.popListeners();
        }
        listener = null;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (!active || event == null) return;
        String type = event.getType();
        if (!MapEvent.MAP_CLICK.equals(type) && !MapEvent.ITEM_CLICK.equals(type)) return;
        GeoPos p = toGeo(event.getPointF());
        if (p == null) return;
        Listener l = listener;
        finish();
        if (l != null) l.onPlaced(p);
    }

    private GeoPos toGeo(PointF pt) {
        if (pt == null) return null;
        GeoPointMetaData gpm = mapView.inverseWithElevation(pt.x, pt.y);
        GeoPoint gp = gpm == null ? null : gpm.get();
        if (gp == null || !gp.isValid()) return null;
        return new GeoPos(gp.getLatitude(), gp.getLongitude());
    }
}
