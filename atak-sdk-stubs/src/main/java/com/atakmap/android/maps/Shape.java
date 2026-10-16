/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

public abstract class Shape extends MapItem {
    public static final int STYLE_STROKE_MASK = 1;
    public static final int STYLE_FILLED_MASK = 2;

    private int style = STYLE_STROKE_MASK;
    private int strokeColor = 0xFFFFFFFF;
    private int fillColor;
    private double strokeWeight = 1d;

    protected Shape(String uid) {
        super(MapItem.createSerialId(), uid);
    }

    public int getStyle() {
        return style;
    }

    public void setStyle(int style) {
        this.style = style;
    }

    public int getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(int color) {
        this.strokeColor = color;
    }

    public int getFillColor() {
        return fillColor;
    }

    public void setFillColor(int color) {
        this.fillColor = color;
    }

    public double getStrokeWeight() {
        return strokeWeight;
    }

    public void setStrokeWeight(double weight) {
        this.strokeWeight = weight;
    }
}
