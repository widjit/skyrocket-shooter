/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atak.plugins.impl;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public final class PluginLayoutInflater {
    private PluginLayoutInflater() {
    }

    public static View inflate(Context pluginContext, int resource, ViewGroup root) {
        return LayoutInflater.from(pluginContext).inflate(resource, root);
    }
}
