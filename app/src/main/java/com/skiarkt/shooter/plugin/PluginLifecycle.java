package com.skiarkt.shooter.plugin;

import android.content.Context;

import com.atak.plugins.impl.AbstractPluginLifecycle;
import com.skiarkt.shooter.PointDefenseMapComponent;

/**
 * ATAK plugin lifecycle entry point (declared in assets/plugin.xml as a
 * {@code transapps.maps.plugin.lifecycle.Lifecycle} extension). ATAK instantiates it
 * reflectively with the plugin {@link Context}; {@link AbstractPluginLifecycle} then
 * forwards create/start/stop/destroy to {@link PointDefenseMapComponent}. The toolbar
 * button ({@link PointDefenseTool}) is registered separately via its own
 * {@code ToolDescriptor} extension in plugin.xml.
 */
public class PluginLifecycle extends AbstractPluginLifecycle {
    public PluginLifecycle(Context pluginContext) {
        super(pluginContext, new PointDefenseMapComponent());
    }
}
