/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps;

import android.content.Context;
import android.content.Intent;

public interface MapComponent {
    void onCreate(Context context, Intent intent, MapView view);

    void onDestroy(Context context, MapView view);

    void onStart(Context context, MapView view);

    void onStop(Context context, MapView view);

    void onPause(Context context, MapView view);

    void onResume(Context context, MapView view);
}
