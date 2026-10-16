package com.skiarkt.shooter.plugin;

import android.content.Context;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.skiarkt.shooter.PointDefenseMapComponent;

import gov.tak.api.plugin.IServiceController;

/**
 * ATAK 5.x plugin entry point, declared in assets/plugin.xml as the single
 * {@code gov.tak.api.plugin.IPlugin} extension. ATAK instantiates it reflectively with its
 * {@link IServiceController}; {@link AbstractPlugin} then registers the toolbar button
 * ({@link PointDefenseTool}) and drives the {@link PointDefenseMapComponent} lifecycle.
 */
public class PluginLifecycle extends AbstractPlugin {
    public PluginLifecycle(IServiceController serviceController) {
        super(serviceController,
                new PointDefenseTool(pluginContext(serviceController)),
                new PointDefenseMapComponent());
    }

    private static Context pluginContext(IServiceController serviceController) {
        return serviceController.getService(PluginContextProvider.class).getPluginContext();
    }
}
