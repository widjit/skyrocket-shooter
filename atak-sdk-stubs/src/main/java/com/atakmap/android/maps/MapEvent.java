/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

import android.graphics.PointF;

public class MapEvent {
    public static final String MAP_CLICK = "map_click";
    public static final String MAP_LONG_PRESS = "map_long_press";
    public static final String MAP_CONFIRMED_CLICK = "map_confirmed_click";
    public static final String ITEM_CLICK = "item_click";
    public static final String ITEM_LONG_PRESS = "item_lngprss";

    private final String type;
    private final PointF point;
    private final MapItem item;

    public MapEvent(String type, PointF point, MapItem item) {
        this.type = type;
        this.point = point;
        this.item = item;
    }

    public String getType() {
        return type;
    }

    public PointF getPointF() {
        return point;
    }

    public MapItem getItem() {
        return item;
    }
}
