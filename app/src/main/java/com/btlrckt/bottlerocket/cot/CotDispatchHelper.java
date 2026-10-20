package com.btlrckt.bottlerocket.cot;

import android.util.Log;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.comms.CotDispatcher;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.List;

/**
 * Sends CoT through ATAK's dispatchers. Internal dispatch places the object on the
 * local map; external dispatch sends it to TAK Server / mesh peers (incl. WinTAK).
 */
public final class CotDispatchHelper {
    private static final String TAG = "BtlrcktCotDispatch";

    private CotDispatchHelper() {
    }

    public static boolean dispatchInternal(CotEvent ev) {
        return dispatch(CotMapComponent.getInternalDispatcher(), ev, "internal");
    }

    public static boolean dispatchExternal(CotEvent ev) {
        return dispatch(CotMapComponent.getExternalDispatcher(), ev, "external");
    }

    /** Sends every event externally; returns the number successfully handed off. */
    public static int broadcastAll(List<CotEvent> events) {
        int sent = 0;
        for (CotEvent ev : events) {
            if (dispatchExternal(ev)) sent++;
        }
        return sent;
    }

    private static boolean dispatch(CotDispatcher dispatcher, CotEvent ev, String which) {
        if (ev == null || !ev.isValid()) {
            Log.w(TAG, "Refusing to dispatch invalid CoT event (" + which + ")");
            return false;
        }
        if (dispatcher == null) {
            Log.w(TAG, "No " + which + " CoT dispatcher available");
            return false;
        }
        try {
            dispatcher.dispatch(ev);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "CoT " + which + " dispatch failed for " + ev.getUID(), e);
            return false;
        }
    }
}
