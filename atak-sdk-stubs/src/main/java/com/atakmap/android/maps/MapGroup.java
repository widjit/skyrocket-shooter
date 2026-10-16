/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

public abstract class MapGroup {
    public abstract String getFriendlyName();

    public abstract void addGroup(MapGroup group);

    public abstract boolean removeGroup(MapGroup group);

    public abstract void addItem(MapItem item);

    public abstract boolean removeItem(MapItem item);

    public abstract void clearItems();

    public abstract void clearGroups();

    public abstract void setVisible(boolean visible);
}
