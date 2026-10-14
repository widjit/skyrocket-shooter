/* Compile-only stub mirroring the ATAK 4.8 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

public class MapEventDispatcher {
    public interface MapEventDispatchListener {
        void onMapEvent(MapEvent event);
    }

    public void addMapEventListener(String type, MapEventDispatchListener l) {
    }

    public void removeMapEventListener(String type, MapEventDispatchListener l) {
    }

    public void clearListeners(String type) {
    }

    public void pushListeners() {
    }

    public void popListeners() {
    }

    public void dispatch(MapEvent event) {
    }
}
