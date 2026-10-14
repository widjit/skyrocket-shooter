/* Compile-only stub mirroring the ATAK 4.8 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoPoint;

public class Marker extends MapItem {
    public static final int ICON_VISIBLE = 0;
    public static final int ICON_INVISIBLE = 1;
    public static final int ICON_GONE = 2;

    private GeoPoint point;
    private int iconVisibility = ICON_VISIBLE;
    private int textColor = 0xFFFFFFFF;

    public Marker(GeoPoint point, String uid) {
        super(MapItem.createSerialId(), uid);
        this.point = point;
    }

    public GeoPoint getPoint() {
        return point;
    }

    public void setPoint(GeoPoint point) {
        this.point = point;
    }

    public int getIconVisibility() {
        return iconVisibility;
    }

    public void setIconVisibility(int visibility) {
        this.iconVisibility = visibility;
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int color) {
        this.textColor = color;
    }
}
