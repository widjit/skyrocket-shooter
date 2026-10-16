/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package com.atakmap.android.ipc;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

public class AtakBroadcast {
    private static final AtakBroadcast INSTANCE = new AtakBroadcast();

    public static AtakBroadcast getInstance() {
        return INSTANCE;
    }

    public boolean sendBroadcast(Intent intent) {
        return false;
    }

    public void registerReceiver(BroadcastReceiver receiver, DocumentedIntentFilter filter) {
    }

    public void unregisterReceiver(BroadcastReceiver receiver) {
    }

    public static class DocumentedIntentFilter extends IntentFilter {
        public DocumentedIntentFilter() {
            super();
        }

        public DocumentedIntentFilter(String action) {
            super(action);
        }

        public void addAction(String action, String description) {
            addAction(action);
        }
    }
}
