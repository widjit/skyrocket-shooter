/* Compile-only stub mirroring the CivTAK 4.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.dropdown;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

public abstract class DropDownMapComponent extends AbstractMapComponent {
    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
    }

    public void registerDropDownReceiver(DropDownReceiver ddr, DocumentedIntentFilter filter) {
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
    }
}
