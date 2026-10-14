/* Compile-only stub mirroring the ATAK 4.8 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.coremap.maps.coords;

public class GeoPointMetaData {
    private GeoPoint point;

    public GeoPointMetaData() {
        this(new GeoPoint(0, 0));
    }

    public GeoPointMetaData(GeoPoint point) {
        this.point = point;
    }

    public static GeoPointMetaData wrap(GeoPoint point) {
        return new GeoPointMetaData(point);
    }

    public GeoPoint get() {
        return point;
    }

    public GeoPointMetaData set(GeoPoint point) {
        this.point = point;
        return this;
    }
}
