/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atak.plugins.impl;

import com.atakmap.android.maps.MapComponent;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;

public class AbstractPlugin implements IPlugin {
    public AbstractPlugin(IServiceController serviceController, MapComponent mapComponent) {
    }

    public AbstractPlugin(IServiceController serviceController, IToolbarItem tool,
            MapComponent mapComponent) {
    }

    public AbstractPlugin(IServiceController serviceController, IToolbarItem[] tools,
            MapComponent mapComponent) {
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onStop() {
    }
}
