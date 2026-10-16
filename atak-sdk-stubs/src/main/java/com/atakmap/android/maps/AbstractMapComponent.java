/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

import android.content.BroadcastReceiver;
import android.content.Context;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

public abstract class AbstractMapComponent implements MapComponent {
    protected abstract void onDestroyImpl(Context context, MapView view);

    @Override
    public final void onDestroy(Context context, MapView view) {
        onDestroyImpl(context, view);
    }

    public void registerReceiver(Context context, BroadcastReceiver receiver, DocumentedIntentFilter filter) {
    }

    public void unregisterReceiver(Context context, BroadcastReceiver receiver) {
    }

    @Override
    public void onStart(Context context, MapView view) {
    }

    @Override
    public void onStop(Context context, MapView view) {
    }

    @Override
    public void onPause(Context context, MapView view) {
    }

    @Override
    public void onResume(Context context, MapView view) {
    }
}
