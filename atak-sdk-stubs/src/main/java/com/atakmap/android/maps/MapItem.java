/* Compile-only stub mirroring the ATAK 4.8 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

import java.util.HashMap;
import java.util.Map;

public abstract class MapItem {
    private final String uid;
    private final Map<String, Object> meta = new HashMap<>();
    private String title;
    private String type = "";
    private boolean visible = true;
    private boolean clickable = true;
    private double height = Double.NaN;
    private double zOrder;

    protected MapItem(long serialId, String uid) {
        this.uid = uid;
    }

    public final String getUID() {
        return uid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean getVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean getClickable() {
        return clickable;
    }

    public void setClickable(boolean clickable) {
        this.clickable = clickable;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getZOrder() {
        return zOrder;
    }

    public void setZOrder(double zOrder) {
        this.zOrder = zOrder;
    }

    public void setMetaString(String key, String value) {
        meta.put(key, value);
    }

    public String getMetaString(String key, String fallback) {
        Object v = meta.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    public void setMetaBoolean(String key, boolean value) {
        meta.put(key, value);
    }

    public boolean getMetaBoolean(String key, boolean fallback) {
        Object v = meta.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    public void setMetaDouble(String key, double value) {
        meta.put(key, value);
    }

    public static long createSerialId() {
        return 0L;
    }
}
