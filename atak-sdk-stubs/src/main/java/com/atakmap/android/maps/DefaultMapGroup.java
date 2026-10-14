/* Compile-only stub mirroring the ATAK 4.8 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

public class DefaultMapGroup extends MapGroup {
    private final String name;

    public DefaultMapGroup(String friendlyName) {
        this.name = friendlyName;
    }

    @Override
    public String getFriendlyName() {
        return name;
    }

    @Override
    public void addGroup(MapGroup group) {
    }

    @Override
    public boolean removeGroup(MapGroup group) {
        return false;
    }

    @Override
    public void addItem(MapItem item) {
    }

    @Override
    public boolean removeItem(MapItem item) {
        return false;
    }

    @Override
    public void clearItems() {
    }

    @Override
    public void clearGroups() {
    }

    @Override
    public void setVisible(boolean visible) {
    }
}
