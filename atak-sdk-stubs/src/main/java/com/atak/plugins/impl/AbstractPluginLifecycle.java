/* Compile-only stub mirroring the CivTAK 4.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atak.plugins.impl;

import android.content.Context;

import com.atakmap.android.maps.MapComponent;

/**
 * Real SDK: {@code public abstract class AbstractPluginLifecycle implements
 * transapps.maps.plugin.lifecycle.Lifecycle} with lifecycle callbacks forwarded to the
 * supplied {@link MapComponent}. Only the constructor is needed for compilation.
 */
public abstract class AbstractPluginLifecycle {
    public AbstractPluginLifecycle(Context context, MapComponent mapComponent) {
    }
}
