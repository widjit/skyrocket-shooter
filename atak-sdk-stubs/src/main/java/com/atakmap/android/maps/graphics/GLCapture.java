/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.maps.graphics;

import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;

/** Off-screen (FBO) capture: queues {@link #draw()} on the GL thread and reads the pixels back. */
public abstract class GLCapture {
    protected final GLSurfaceView view;
    protected final int x, y, width, height;

    public GLCapture(GLSurfaceView view) {
        this(view, 0, 0, view.getWidth(), view.getHeight());
    }

    public GLCapture(GLSurfaceView view, int x, int y, int width, int height) {
        this.view = view;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    protected abstract void draw();

    public final void capture(CaptureCallback callback) {
    }

    public interface CaptureCallback {
        void onCaptureStarted(GLCapture capture);

        void onCaptureComplete(GLCapture capture, Bitmap bitmap);

        void onCaptureError(GLCapture capture, Throwable error);
    }
}
