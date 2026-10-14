package com.skiarkt.shooter.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.skiarkt.shooter.PointDefenseMapComponent;

import gov.tak.api.plugin.IServiceController;

/**
 * ATAK 4.8 plugin entry point (declared in assets/plugin.xml). {@link AbstractPlugin}
 * registers the toolbar tool and the {@link PointDefenseMapComponent} when ATAK loads
 * the plugin, and unregisters / destroys them when it is unloaded or uninstalled.
 */
public class PluginLifecycle extends AbstractPlugin {
    public PluginLifecycle(IServiceController serviceController) {
        super(serviceController,
                new PointDefenseTool(serviceController.getService(PluginContextProvider.class).getPluginContext()),
                new PointDefenseMapComponent());
    }
}
