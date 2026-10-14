package com.skiarkt.shooter;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;
import com.skiarkt.shooter.data.PointDefenseRepository;
import com.skiarkt.shooter.overlay.OverlayManager;
import com.skiarkt.shooter.settings.SettingsManager;
import com.skiarkt.shooter.util.DtedHelper;

import java.io.File;

/**
 * Root map component: wires settings, repository, terrain helper, overlays and the
 * drop-down UI. Created by {@link com.skiarkt.shooter.plugin.PluginLifecycle}.
 */
public class PointDefenseMapComponent extends DropDownMapComponent {
    private static final String TAG = "SkiarktPointDefense";
    /** Plans / exports live under the standard ATAK data root. */
    public static final String STORAGE_SUBDIR = "atak/tools/skiarkt_shooter";

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
        filter.addAction(PointDefenseDropDownReceiver.SHOW_PLUGIN, "Open the SKY ROCKET SHOOTER panel");
        registerDropDownReceiver(dropDown, filter);

        Log.d(TAG, "SKY ROCKET SHOOTER " + BuildConfig.VERSION_NAME + " loaded (SDK mode "
                + BuildConfig.ATAK_SDK_MODE + ")");
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (overlayManager != null) overlayManager.stop();
        // DropDownMapComponent unregisters and disposes registered drop-down receivers.
        super.onDestroyImpl(context, view);
        Log.d(TAG, "SKY ROCKET SHOOTER unloaded");
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
