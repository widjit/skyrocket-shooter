/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class Polyline extends Shape {
    public static final int STYLE_CLOSED_MASK = 4;
    public static final int STYLE_OUTLINE_STROKE_MASK = 8;
    public static final int BASIC_LINE_STYLE_SOLID = 0;
    public static final int BASIC_LINE_STYLE_DASHED = 1;
    public static final int BASIC_LINE_STYLE_DOTTED = 2;
    public static final int BASIC_LINE_STYLE_OUTLINED = 3;

    private GeoPointMetaData[] points = new GeoPointMetaData[0];
    private int basicLineStyle = BASIC_LINE_STYLE_SOLID;

    public Polyline(String uid) {
        super(uid);
    }

    public void setPoints(GeoPointMetaData[] pts) {
        this.points = pts == null ? new GeoPointMetaData[0] : pts.clone();
    }

    public GeoPointMetaData[] getMetaDataPoints() {
        return points.clone();
    }

    public int getNumPoints() {
        return points.length;
    }

    public int getBasicLineStyle() {
        return basicLineStyle;
    }

    public void setBasicLineStyle(int style) {
        this.basicLineStyle = style;
    }
}
