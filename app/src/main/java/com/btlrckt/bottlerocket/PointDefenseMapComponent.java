package com.btlrckt.bottlerocket;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;
import com.btlrckt.bottlerocket.data.PointDefenseRepository;
import com.btlrckt.bottlerocket.overlay.OverlayManager;
import com.btlrckt.bottlerocket.settings.SettingsManager;
import com.btlrckt.bottlerocket.util.DtedHelper;

import java.io.File;

/**
 * Root map component: wires settings, repository, terrain helper, overlays and the
 * drop-down UI. Created by {@link com.btlrckt.bottlerocket.plugin.PluginLifecycle}.
 */
public class PointDefenseMapComponent extends DropDownMapComponent {
    private static final String TAG = "BtlrcktPointDefense";
    /** Plans / exports live under the standard ATAK data root. */
    public static final String STORAGE_SUBDIR = "atak/tools/btlrckt";

    private Context pluginContext;
    private SettingsManager settings;
    private PointDefenseRepository repository;
    private DtedHelper dtedHelper;
    private OverlayManager overlayManager;
    private PointDefenseDropDownReceiver dropDown;

    @Override
    public void onCreate(final Context context, Intent intent, final MapView view) {
        context.setTheme(R.style.ATAKPluginTheme);
        super.onCreate(context, intent, view);
        pluginContext = context;

        settings = new SettingsManager(view.getContext());
        repository = PointDefenseRepository.getInstance();
        repository.init(new File(Environment.getExternalStorageDirectory(), STORAGE_SUBDIR),
                settings.getCotStaleDays());
        if (repository.getPlan().author.isEmpty()) {
            repository.getPlan().author = settings.getDefaultAuthor();
        }
        dtedHelper = new DtedHelper();

        overlayManager = new OverlayManager(view, repository, settings);
        overlayManager.start();

        dropDown = new PointDefenseDropDownReceiver(view, pluginContext, settings, repository, dtedHelper,
                overlayManager);
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(PointDefenseDropDownReceiver.SHOW_PLUGIN, "Open the BOTTLE ROCKET (BTL RCKT) panel");
        registerDropDownReceiver(dropDown, filter);

        Log.d(TAG, "BTL RCKT " + BuildConfig.VERSION_NAME + " loaded (SDK mode "
                + BuildConfig.ATAK_SDK_MODE + ")");
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (overlayManager != null) overlayManager.stop();
        // Write any pending change now; the debounced autosave may not have fired yet.
        if (repository != null) repository.flushAutosave();
        // DropDownMapComponent unregisters and disposes registered drop-down receivers.
        super.onDestroyImpl(context, view);
        Log.d(TAG, "BTL RCKT unloaded");
    }

    public SettingsManager getSettings() {
        return settings;
    }

    public DtedHelper getDtedHelper() {
        return dtedHelper;
    }

    public OverlayManager getOverlayManager() {
        return overlayManager;
    }
}
