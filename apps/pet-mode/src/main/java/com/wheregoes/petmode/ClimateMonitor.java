package com.wheregoes.petmode;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

class ClimateMonitor {
    private static final String TAG = "ClimateMonitor";
    private static final String KEY_AC_DEVICE_TYPE = "ac_device_type";
    private static final String KEY_AC_TEMP_FID = "ac_temp_feature_id";
    private static final String KEY_AC_STATUS_FID = "ac_status_feature_id";

    interface Listener {
        void onTemperatureChanged(int tempCelsius);
        void onAcStatusChanged(boolean acOn);
        void onClimateUnavailable();
    }

    private final Context context;
    private Listener listener;
    private Handler handler;
    private Object autoManager;
    private Method getIntMethod;
    private int acDeviceType = -1;
    private int tempFeatureId = -1;
    private int acStatusFeatureId = -1;
    private boolean available = false;
    private boolean polling = false;
    private int lastTemp = Integer.MIN_VALUE;

    ClimateMonitor(Context context) {
        this.context = context;
        handler = new Handler(Looper.getMainLooper());
    }

    void start(Listener cb) {
        listener = cb;
        loadCachedSignals();
        if (acDeviceType > 0 && tempFeatureId > 0) {
            initAutoManager();
            if (available) {
                startPolling();
                return;
            }
        }
        discoverAcDevice();
    }

    void stop() {
        polling = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void loadCachedSignals() {
        SharedPreferences prefs = context.getSharedPreferences(PetModeService.PREF_NAME, Context.MODE_PRIVATE);
        acDeviceType = prefs.getInt(KEY_AC_DEVICE_TYPE, -1);
        tempFeatureId = prefs.getInt(KEY_AC_TEMP_FID, -1);
        acStatusFeatureId = prefs.getInt(KEY_AC_STATUS_FID, -1);
    }

    private void cacheSignals() {
        context.getSharedPreferences(PetModeService.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_AC_DEVICE_TYPE, acDeviceType)
                .putInt(KEY_AC_TEMP_FID, tempFeatureId)
                .putInt(KEY_AC_STATUS_FID, acStatusFeatureId)
                .apply();
    }

    private void initAutoManager() {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            Object binder = getService.invoke(null, "auto");
            if (binder == null) {
                Log.w(TAG, "auto service not found");
                return;
            }
            Class<?> managerClass = Class.forName("android.hardware.bydauto.BYDAutoManager");
            getIntMethod = managerClass.getMethod("getInt", int.class, int.class);
            autoManager = managerClass.getConstructor(Context.class)
                    .newInstance(new BydPermissionContext(context));
            available = true;
            Log.i(TAG, "BYDAutoManager initialized");
        } catch (Exception e) {
            Log.w(TAG, "BYDAutoManager init failed: " + e.getMessage());
            tryAlternateInit();
        }
    }

    private void tryAlternateInit() {
        try {
            Class<?> managerClass = Class.forName("android.hardware.bydauto.BYDAutoManager");
            for (Method m : managerClass.getMethods()) {
                if (m.getName().equals("getInt") && m.getParameterCount() == 2) {
                    getIntMethod = m;
                    break;
                }
            }
            if (getIntMethod != null) {
                autoManager = managerClass.getConstructor(Context.class)
                        .newInstance(new BydPermissionContext(context));
                available = true;
                Log.i(TAG, "Alternate init succeeded");
            }
        } catch (Exception e) {
            Log.w(TAG, "Alternate init failed: " + e.getMessage());
        }
    }

    private void discoverAcDevice() {
        if (!available) initAutoManager();
        if (!available) {
            Log.w(TAG, "Cannot discover — manager unavailable");
            if (listener != null) listener.onClimateUnavailable();
            return;
        }

        new Thread(() -> {
            // Scan device types 1004-1020 for AC-related signals
            // Common temperature feature ID patterns in BYD CAN bus
            int[] candidateDevices = {1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012};
            int[] candidateTempFids = {
                0x99000010, 0x99000011, 0x99000012, 0x99000020, 0x99000030,
                0x48000010, 0x48000011, 0x48000020, 0x48000030,
                0x99100010, 0x99100020, 0x99100030
            };

            for (int dev : candidateDevices) {
                for (int fid : candidateTempFids) {
                    try {
                        int val = (int) getIntMethod.invoke(autoManager, dev, fid);
                        if (val > 0 && val < 100) {
                            Log.i(TAG, "FOUND temp candidate: dev=" + dev + " fid=0x" +
                                    Integer.toHexString(fid) + " val=" + val);
                            acDeviceType = dev;
                            tempFeatureId = fid;
                            cacheSignals();
                            handler.post(() -> {
                                if (listener != null) listener.onTemperatureChanged(val);
                                startPolling();
                            });
                            return;
                        }
                    } catch (Exception ignored) {}
                }
            }

            Log.i(TAG, "AC signals not found — temperature unavailable");
            handler.post(() -> { if (listener != null) listener.onClimateUnavailable(); });
        }).start();
    }

    private void startPolling() {
        polling = true;
        pollTemperature();
    }

    private void pollTemperature() {
        if (!polling || !available || tempFeatureId < 0) return;

        try {
            int val = (int) getIntMethod.invoke(autoManager, acDeviceType, tempFeatureId);
            if (val != lastTemp && val > -50 && val < 100) {
                lastTemp = val;
                if (listener != null) listener.onTemperatureChanged(val);
            }
        } catch (Exception e) {
            Log.w(TAG, "Poll failed: " + e.getMessage());
        }

        if (acStatusFeatureId > 0) {
            try {
                int status = (int) getIntMethod.invoke(autoManager, acDeviceType, acStatusFeatureId);
                if (listener != null) listener.onAcStatusChanged(status > 0);
            } catch (Exception ignored) {}
        }

        handler.postDelayed(this::pollTemperature, 5000);
    }

    void setManualSignals(int deviceType, int tempFid, int statusFid) {
        acDeviceType = deviceType;
        tempFeatureId = tempFid;
        acStatusFeatureId = statusFid;
        cacheSignals();
        if (!available) initAutoManager();
        if (available) startPolling();
    }
}
