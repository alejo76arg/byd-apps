package com.wheregoes.petmode;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

class ClimateMonitor {
    private static final String TAG = "ClimateMonitor";
    private static final int AC_DEVICE_TYPE = 1000;
    private static final int AC_TEMP_INSIDE = 0x1DE00030;
    private static final int AC_TEMP_INSIDE_FILTERING = 0x3D800030;

    interface Listener {
        void onTemperatureChanged(int tempCelsius);
        void onAcStatusChanged(boolean acOn);
        void onClimateUnavailable();
    }

    private final Context context;
    private Listener listener;
    private Handler handler;
    private Object acDevice;
    private Method getMethod;
    private Method getAcStartStateMethod;
    private boolean tempAvailable = false;
    private boolean polling = false;
    private int lastTemp = Integer.MIN_VALUE;

    ClimateMonitor(Context context) {
        this.context = context;
        handler = new Handler(Looper.getMainLooper());
    }

    void start(Listener cb) {
        listener = cb;
        initAcDevice();
        if (acDevice != null) {
            checkInitialAcState();
            tryReadTemperature();
        } else {
            if (listener != null) listener.onClimateUnavailable();
        }
    }

    void stop() {
        polling = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void initAcDevice() {
        try {
            Class<?> acClass = Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
            Method getInstance = acClass.getMethod("getInstance", Context.class);
            acDevice = getInstance.invoke(null, new BydPermissionContext(context));
            getMethod = acClass.getMethod("get", int[].class, Class.class);
            getAcStartStateMethod = acClass.getMethod("getAcStartState");
            Log.i(TAG, "BYDAutoAcDevice initialized");
        } catch (Exception e) {
            Log.w(TAG, "BYDAutoAcDevice init failed: " + e.getMessage());
        }
    }

    private void checkInitialAcState() {
        if (getAcStartStateMethod == null) return;
        try {
            int state = (int) getAcStartStateMethod.invoke(acDevice);
            Log.i(TAG, "AC start state: " + state);
            if (listener != null) listener.onAcStatusChanged(state == 1);
        } catch (Exception e) {
            Log.w(TAG, "getAcStartState failed: " + e.getMessage());
        }
    }

    private void tryReadTemperature() {
        new Thread(() -> {
            int[] featureIds = {AC_TEMP_INSIDE_FILTERING, AC_TEMP_INSIDE};
            for (int fid : featureIds) {
                try {
                    Object result = getMethod.invoke(acDevice, new int[]{fid}, Integer.class);
                    if (result != null) {
                        int val = extractIntValue(result);
                        if (val > -50 && val < 100) {
                            Log.i(TAG, "Temp from 0x" + Integer.toHexString(fid) + ": " + val + "°C");
                            tempAvailable = true;
                            lastTemp = val;
                            handler.post(() -> {
                                if (listener != null) listener.onTemperatureChanged(val);
                                startPolling(fid);
                            });
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Feature 0x" + Integer.toHexString(fid) + ": " + e.getMessage());
                }
            }
            Log.i(TAG, "Temperature not available (requires system permission)");
            handler.post(() -> { if (listener != null) listener.onClimateUnavailable(); });
        }).start();
    }

    private void startPolling(int featureId) {
        polling = true;
        schedulePoll(featureId);
    }

    private void schedulePoll(int featureId) {
        handler.postDelayed(() -> {
            if (!polling) return;
            try {
                Object result = getMethod.invoke(acDevice, new int[]{featureId}, Integer.class);
                if (result != null) {
                    int val = extractIntValue(result);
                    if (val > -50 && val < 100 && val != lastTemp) {
                        lastTemp = val;
                        if (listener != null) listener.onTemperatureChanged(val);
                    }
                }
            } catch (Exception ignored) {}
            schedulePoll(featureId);
        }, 5000);
    }

    private int extractIntValue(Object eventValue) {
        try {
            Method getValue = eventValue.getClass().getMethod("getValue");
            Object val = getValue.invoke(eventValue);
            if (val instanceof Integer) return (int) val;
            if (val instanceof Number) return ((Number) val).intValue();
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            try {
                Method getInt = eventValue.getClass().getMethod("getIntValue");
                return (int) getInt.invoke(eventValue);
            } catch (Exception e2) {
                return Integer.MIN_VALUE;
            }
        }
    }
}
