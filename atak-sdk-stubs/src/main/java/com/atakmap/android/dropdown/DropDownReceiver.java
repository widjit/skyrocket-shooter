/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.dropdown;

import android.content.BroadcastReceiver;
import android.view.View;

import com.atakmap.android.maps.MapView;

public abstract class DropDownReceiver extends BroadcastReceiver {
    public static final double FULL_WIDTH = 1d;
    public static final double HALF_WIDTH = 0.5d;
    public static final double THIRD_WIDTH = 1d / 3d;
    public static final double THREE_EIGHTHS_WIDTH = 0.375d;
    public static final double FULL_HEIGHT = 1d;
    public static final double HALF_HEIGHT = 0.5d;
    public static final double THIRD_HEIGHT = 1d / 3d;

    private final MapView mapView;

    protected DropDownReceiver(MapView mapView) {
        this.mapView = mapView;
    }

    public MapView getMapView() {
        return mapView;
    }

    public void showDropDown(View contentView, double lwFraction, double lhFraction,
            double pwFraction, double phFraction, DropDown.OnStateListener stateListener) {
    }

    public void closeDropDown() {
    }

    public boolean isClosed() {
        return true;
    }

    public boolean isVisible() {
        return false;
    }

    public final void dispose() {
        disposeImpl();
    }

    protected abstract void disposeImpl();
}
