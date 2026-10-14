/* Compile-only stub mirroring the ATAK 4.8 SDK API. Replaced by the host ATAK app at runtime. */
package com.atak.plugins.impl;

import com.atakmap.android.maps.MapComponent;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;

public abstract class AbstractPlugin implements IPlugin {
    protected final IServiceController serviceController;

    public AbstractPlugin(IServiceController serviceController, AbstractPluginTool tool,
            MapComponent mapComponent) {
        this.serviceController = serviceController;
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onStop() {
    }
}
