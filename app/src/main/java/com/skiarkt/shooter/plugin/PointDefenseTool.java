package com.skiarkt.shooter.plugin;

import android.content.Context;

import com.atak.plugins.impl.AbstractPluginTool;
import com.skiarkt.shooter.PointDefenseDropDownReceiver;
import com.skiarkt.shooter.R;

import gov.tak.api.util.Disposable;

/**
 * Toolbar / overflow-menu button. Tapping it broadcasts
 * {@link PointDefenseDropDownReceiver#SHOW_PLUGIN}, which opens the side panel.
 */
public class PointDefenseTool extends AbstractPluginTool implements Disposable {
    public PointDefenseTool(Context pluginContext) {
        super(pluginContext,
                pluginContext.getString(R.string.app_name),
                pluginContext.getString(R.string.app_desc),
                pluginContext.getResources().getDrawable(R.drawable.ic_skiarkt_toolbar, null),
                PointDefenseDropDownReceiver.SHOW_PLUGIN);
    }

    @Override
    public void dispose() {
        // Nothing to release: the tool holds no listeners or native resources.
    }
}
