/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.map.opengl;

import android.content.Context;
import android.opengl.GLSurfaceView;

public class GLMapSurface extends GLSurfaceView {
    public GLMapSurface(Context context) {
        super(context);
    }

    public GLMapView getGLMapView() {
        return null;
    }
}
