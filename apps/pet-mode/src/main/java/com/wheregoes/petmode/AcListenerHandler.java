package com.wheregoes.petmode;

import android.hardware.bydauto.BYDAutoEventValue;
import android.hardware.bydauto.ac.AbsBYDAutoAcListener;
import android.util.Log;

public class AcListenerHandler extends AbsBYDAutoAcListener {
    private static final String TAG = "AcListener";

    static final int AC_TEMP_INSIDE = 0x1DE00030;
    static final int AC_TEMP_INSIDE_FILTERING = 0x3D800030;
    static final int AC_SET_TEMP_DRIVER = 0x1DE00010;
    static final int AC_SET_TEMP_PASSENGER = 0x1DE00018;

    private final ClimateMonitor monitor;

    AcListenerHandler(ClimateMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void onDataEventChanged(int featureId, BYDAutoEventValue value) {
        if (value == null) return;
        int val = value.intValue;
        String hex = "0x" + Integer.toHexString(featureId);
        Log.i(TAG, "onDataEvent " + hex + " = " + val);

        switch (featureId) {
            case AC_TEMP_INSIDE:
            case AC_TEMP_INSIDE_FILTERING:
                if (val > -50 && val < 100) {
                    monitor.onInsideTempEvent(val);
                }
                break;
            case AC_SET_TEMP_DRIVER:
                if (val >= 16 && val <= 32) {
                    monitor.onSetTempEvent(val);
                }
                break;
            case AC_SET_TEMP_PASSENGER:
                Log.i(TAG, "Passenger set temp: " + val);
                break;
            default:
                Log.d(TAG, "Unhandled feature " + hex + " = " + val);
                break;
        }
    }

    @Override
    public void onAcStarted() {
        Log.i(TAG, "AC STARTED (listener)");
        monitor.onAcStartedEvent();
    }

    @Override
    public void onAcStoped() {
        Log.i(TAG, "AC STOPPED (listener)");
        monitor.onAcStoppedEvent();
    }
}
