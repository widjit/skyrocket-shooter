/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

import android.content.Context;
import android.view.View;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class MapView extends View {
    private static MapView instance;

    public MapView(Context context) {
        super(context);
        instance = this;
    }

    public static MapView getMapView() {
        return instance;
    }

    /** Inherited from AtakMapView in the real SDK. */
    public com.atakmap.map.opengl.GLMapSurface getGLSurface() {
        return null;
    }

    public MapGroup getRootGroup() {
        return null;
    }

    public MapEventDispatcher getMapEventDispatcher() {
        return null;
    }

    /** Screen pixel -> geodetic point (with terrain elevation when available). */
    public GeoPointMetaData inverseWithElevation(float x, float y) {
        return null;
    }
}
