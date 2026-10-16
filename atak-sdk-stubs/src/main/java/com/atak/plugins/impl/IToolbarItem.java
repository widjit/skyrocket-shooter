/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atak.plugins.impl;

import android.graphics.drawable.Drawable;

public interface IToolbarItem {
    String getShortDescription();

    String getDescription();

    Drawable getIcon();
}
