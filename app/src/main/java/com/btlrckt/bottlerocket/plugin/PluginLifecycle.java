package com.btlrckt.bottlerocket.plugin;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapComponent;
import com.btlrckt.bottlerocket.PointDefenseDropDownReceiver;
import com.btlrckt.bottlerocket.PointDefenseMapComponent;
import com.btlrckt.bottlerocket.R;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/**
 * ATAK 5.x plugin entry point, declared in assets/plugin.xml as the single
 * {@code gov.tak.api.plugin.IPlugin} extension.
 *
 * Mirrors the ATAK-CIV 5.6 SDK plugintemplate: the menu/toolbar button is published through
 * {@link IHostUIService#addToolbarItem(ToolbarItem)} with a stable identifier (the package name),
 * so it appears in ATAK's tool menu and the toolbar editor can find it again after the user moves
 * it. The map component (side panel, overlays, CoT) is registered with the service controller.
 */
public class PluginLifecycle implements IPlugin {
    private static final String TAG = "BTLRCKT.Plugin";

    private final IServiceController serviceController;
    private final IHostUIService uiService;
    private final ToolbarItem toolbarItem;
    private final MapComponent mapComponent = new PointDefenseMapComponent();

    public PluginLifecycle(IServiceController serviceController) {
        this.serviceController = serviceController;

        Context pluginContext = serviceController
                .getService(PluginContextProvider.class).getPluginContext();
        uiService = serviceController.getService(IHostUIService.class);

        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_btlrckt_toolbar, null),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        AtakBroadcast.getInstance().sendBroadcast(
                                new Intent(PointDefenseDropDownReceiver.SHOW_PLUGIN));
                    }
                })
                .setIdentifier(pluginContext.getPackageName())
                .build();
        Log.d(TAG, "constructed; uiService=" + (uiService != null));
    }

    @Override
    public void onStart() {
        serviceController.registerComponent(MapComponent.class, mapComponent);
        if (uiService != null) {
            uiService.addToolbarItem(toolbarItem);
        } else {
            Log.w(TAG, "IHostUIService unavailable; tool menu entry not added");
        }
        Log.d(TAG, "started");
    }

    @Override
    public void onStop() {
        if (uiService != null) {
            uiService.removeToolbarItem(toolbarItem);
        }
        serviceController.unregisterComponent(MapComponent.class, mapComponent);
        Log.d(TAG, "stopped");
    }
}
