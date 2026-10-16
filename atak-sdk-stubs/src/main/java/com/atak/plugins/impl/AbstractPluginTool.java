/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atak.plugins.impl;

import android.content.Context;
import android.graphics.drawable.Drawable;

public abstract class AbstractPluginTool implements IToolbarItem {
    private final String shortDescription;
    private final String description;
    private final Drawable icon;

    public AbstractPluginTool(Context context, String shortDescription, String description,
            Drawable icon, String action) {
        this.shortDescription = shortDescription;
        this.description = description;
        this.icon = icon;
    }

    @Override
    public final String getShortDescription() { return shortDescription; }

    @Override
    public final String getDescription() { return description; }

    @Override
    public final Drawable getIcon() { return icon; }
}
