package com.wheregoes.petmode;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.bydauto.BYDAutoEventValue;
import android.hardware.bydauto.ac.AbsBYDAutoAcListener;
import android.hardware.bydauto.ac.BYDAutoAcDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

class ClimateMonitor implements SensorEventListener {
    private static final String TAG = "ClimateMonitor";

    private static final int[] TEMP_FEATURE_IDS = {
        0x3D800030, // AC_TEMP_INSIDE_FILTERING
        0x1DE00030, // AC_TEMP_INSIDE
        0x1DE00010, // AC_SET_TEMP_DRIVER
        0x1DE00018, // AC_SET_TEMP_PASSENGER
        0x1DE00008,
        0x1DE00012,
        0x1DE00020,
        0x1DE00022,
        0x1DE00024,
        0x1DE00028,
    };

    interface Listener {
        void onTemperatureChanged(int tempCelsius);
        void onSetTempChanged(int tempCelsius);
        void onAcStatusChanged(boolean acOn);
        void onClimateUnavailable();
    }

    private final Context context;
    private Listener listener;
    private Handler handler;
    private BYDAutoAcDevice acDevice;
    private AcListenerHandler acListenerHandler;
    private SensorManager sensorManager;
    private Sensor ambientSensor;
    private boolean listenerRegistered = false;
    private boolean sensorRegistered = false;
    private int lastInsideTemp = Integer.MIN_VALUE;
    private int lastSetTemp = Integer.MIN_VALUE;
    private int lastSensorTemp = Integer.MIN_VALUE;
    private boolean insideTempAvailable = false;
    private boolean gotInsideTemp = false;
    private boolean gotSetTemp = false;

    ClimateMonitor(Context context) {
        this.context = context;
        handler = new Handler(Looper.getMainLooper());
    }

    void start(Listener cb) {
        listener = cb;
        initAcDevice();
        if (acDevice != null) {
            checkInitialAcState();
            tryRegisterListener();
            tryReadTemperature();
            schedulePoll();
        }
        startAmbientSensor();
        if (acDevice == null && ambientSensor == null) {
            if (listener != null) listener.onClimateUnavailable();
        }
    }

    void stop() {
        unregisterAcListener();
        stopAmbientSensor();
        handler.removeCallbacksAndMessages(null);
    }

    private void schedulePoll() {
        handler.postDelayed(() -> {
            pollTemperature();
            schedulePoll();
        }, 30000);
    }

    private void pollTemperature() {
        if (acDevice == null) return;
        new Thread(() -> {
            try {
                Method m = acDevice.getClass().getMethod("getTemprature", int.class);
                m.setAccessible(true);
                int inside = (int) m.invoke(acDevice, 4);
                if (isValidTemp(inside)) handler.post(() -> onInsideTempEvent(inside));
                int setTemp = (int) m.invoke(acDevice, 1);
                if (isValidSetTemp(setTemp)) handler.post(() -> onSetTempEvent(setTemp));
                int acState = acDevice.getAcStartState();
                handler.post(() -> { if (listener != null) listener.onAcStatusChanged(acState == 1); });
            } catch (Exception e) {
                Log.d(TAG, "Poll failed: " + e.getMessage());
            }
        }).start();
    }

    private void initAcDevice() {
        try {
            acDevice = BYDAutoAcDevice.getInstance(new BydPermissionContext(context));
            Log.i(TAG, "BYDAutoAcDevice initialized");
        } catch (Exception e) {
            Log.w(TAG, "BYDAutoAcDevice init failed: " + e.getMessage());
        }
    }

    private void checkInitialAcState() {
        try {
            int state = acDevice.getAcStartState();
            Log.i(TAG, "AC start state: " + state);
            if (listener != null) listener.onAcStatusChanged(state == 1);
        } catch (Exception e) {
            Log.w(TAG, "getAcStartState failed: " + e.getMessage());
        }
    }

    private void tryRegisterListener() {
        new Thread(() -> {
            try {
                acListenerHandler = new AcListenerHandler(this);
                acDevice.registerListener(acListenerHandler, TEMP_FEATURE_IDS);
                listenerRegistered = true;
                Log.i(TAG, "AC listener registered for " + TEMP_FEATURE_IDS.length + " features");
            } catch (Exception e) {
                Log.w(TAG, "AC listener registration failed: " + e.getMessage());
                listenerRegistered = false;
            }
        }).start();
    }

    private void unregisterAcListener() {
        if (listenerRegistered && acDevice != null && acListenerHandler != null) {
            try {
                acDevice.unregisterListener(acListenerHandler);
                listenerRegistered = false;
            } catch (Exception ignored) {}
        }
    }

    private static boolean isValidTemp(int val) {
        return val > 0 && val < 80;
    }

    private static boolean isValidSetTemp(int val) {
        return val >= 16 && val <= 32;
    }

    private void tryReadTemperature() {
        new Thread(() -> {
            // Strategy 1: getTemprature(zone) — BYD's own method (note typo in API)
            tryGetTempratureMethod();

            // Strategy 2: AbsBYDAutoDevice.get(int, int) — 2-arg version
            if (!gotInsideTemp || !gotSetTemp) tryTwoArgGet();

            // Strategy 3: Manager.getInt(deviceType, featureId) — bypass permission layer
            if (!gotInsideTemp || !gotSetTemp) tryManagerGetInt();

            // Strategy 4: Direct get(int[], Class) — permission-checked, rarely works
            if (!gotInsideTemp || !gotSetTemp) tryDirectGet();

            if (!gotInsideTemp && !gotSetTemp) {
                Log.i(TAG, "No temperature source found via polling, relying on listener events");
            }
        }).start();
    }

    private void tryGetTempratureMethod() {
        try {
            Method m = acDevice.getClass().getMethod("getTemprature", int.class);
            m.setAccessible(true);

            // Zone 4 = inside/cabin temperature
            try {
                int val = (int) m.invoke(acDevice, 4);
                Log.i(TAG, "getTemprature(4/inside) = " + val);
                if (isValidTemp(val)) {
                    gotInsideTemp = true;
                    final int temp = val;
                    handler.post(() -> onInsideTempEvent(temp));
                }
            } catch (Exception e) {
                Log.d(TAG, "getTemprature(4) err: " +
                    (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
            }

            // Zone 1 = driver set temp
            try {
                int val = (int) m.invoke(acDevice, 1);
                Log.i(TAG, "getTemprature(1/setDriver) = " + val);
                if (isValidSetTemp(val)) {
                    gotSetTemp = true;
                    final int temp = val;
                    handler.post(() -> onSetTempEvent(temp));
                }
            } catch (Exception e) {
                Log.d(TAG, "getTemprature(1) err: " +
                    (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
            }

            // Log all zones for reference
            for (int zone = 0; zone <= 10; zone++) {
                try {
                    int val = (int) m.invoke(acDevice, zone);
                    Log.i(TAG, "getTemprature(" + zone + ") = " + val);
                } catch (Exception ignored) {}
            }
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "getTemprature method not found");
        } catch (Exception e) {
            Log.w(TAG, "getTemprature probe failed: " + e.getMessage());
        }
    }

    private void tryTwoArgGet() {
        try {
            Method getMethod = acDevice.getClass().getSuperclass().getDeclaredMethod("get", int.class, int.class);
            getMethod.setAccessible(true);
            int[][] targets = {
                {0x3D800030, 0}, // inside temp filtered → inside
                {0x1DE00030, 0}, // inside temp → inside
                {0x1DE00010, 1}, // set temp driver → set
                {0x1DE00018, 1}, // set temp passenger → set
                {0x1DE00008, 0},
                {0x1DE00012, 1},
                {0x1DE00020, 0},
                {0x1DE00022, 0},
                {0x1DE00024, 0},
                {0x1DE00028, 0},
            };
            for (int[] t : targets) {
                int fid = t[0];
                boolean isSetTemp = t[1] == 1;
                try {
                    int val = (int) getMethod.invoke(acDevice, 1000, fid);
                    String hex = "0x" + Integer.toHexString(fid);
                    Log.i(TAG, "get(1000, " + hex + ") = " + val);
                    if (isSetTemp && !gotSetTemp && isValidSetTemp(val)) {
                        gotSetTemp = true;
                        final int temp = val;
                        handler.post(() -> onSetTempEvent(temp));
                    } else if (!isSetTemp && !gotInsideTemp && isValidTemp(val)) {
                        gotInsideTemp = true;
                        final int temp = val;
                        handler.post(() -> onInsideTempEvent(temp));
                    }
                } catch (Exception e) {
                    Log.d(TAG, "get(1000, 0x" + Integer.toHexString(fid) + ") err: " +
                        (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                }
            }
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "No get(int,int) method on AbsBYDAutoDevice");
        } catch (Exception e) {
            Log.w(TAG, "2-arg get failed: " + e.getMessage());
        }
    }

    private void tryManagerGetInt() {
        try {
            Field mgrField = acDevice.getClass().getSuperclass().getDeclaredField("mDeviceManager");
            mgrField.setAccessible(true);
            Object manager = mgrField.get(acDevice);
            if (manager == null) return;

            Method getInt = null;
            for (Method m : manager.getClass().getMethods()) {
                if (m.getName().equals("getInt") && m.getParameterTypes().length == 2
                    && m.getParameterTypes()[0] == int.class && m.getParameterTypes()[1] == int.class) {
                    getInt = m;
                    break;
                }
            }
            if (getInt == null) {
                Log.d(TAG, "Manager.getInt(int,int) not found");
                return;
            }
            getInt.setAccessible(true);

            int[][] targets = {
                {0x3D800030, 0}, {0x1DE00030, 0},
                {0x1DE00010, 1}, {0x1DE00018, 1},
                {0x1DE00008, 0}, {0x1DE00012, 1},
                {0x1DE00020, 0}, {0x1DE00022, 0}, {0x1DE00024, 0}, {0x1DE00028, 0},
            };
            for (int[] t : targets) {
                int fid = t[0];
                boolean isSetTemp = t[1] == 1;
                try {
                    int val = (int) getInt.invoke(manager, 1000, fid);
                    String hex = "0x" + Integer.toHexString(fid);
                    Log.i(TAG, "Mgr.getInt(1000, " + hex + ") = " + val);
                    if (isSetTemp && !gotSetTemp && isValidSetTemp(val)) {
                        gotSetTemp = true;
                        final int temp = val;
                        handler.post(() -> onSetTempEvent(temp));
                    } else if (!isSetTemp && !gotInsideTemp && isValidTemp(val)) {
                        gotInsideTemp = true;
                        final int temp = val;
                        handler.post(() -> onInsideTempEvent(temp));
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Mgr.getInt(1000, 0x" + Integer.toHexString(fid) + ") err: " +
                        (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Manager getInt bypass failed: " + e.getMessage());
        }
    }

    private void tryDirectGet() {
        for (int fid : TEMP_FEATURE_IDS) {
            try {
                BYDAutoEventValue result = acDevice.get(new int[]{fid}, Integer.class);
                if (result != null) {
                    int val = result.intValue;
                    String hex = "0x" + Integer.toHexString(fid);
                    Log.i(TAG, "get({" + hex + "}) = " + val);
                    boolean isSetFeat = fid == 0x1DE00010 || fid == 0x1DE00018 || fid == 0x1DE00012;
                    if (isSetFeat && !gotSetTemp && isValidSetTemp(val)) {
                        gotSetTemp = true;
                        final int temp = val;
                        handler.post(() -> onSetTempEvent(temp));
                    } else if (!isSetFeat && !gotInsideTemp && isValidTemp(val)) {
                        gotInsideTemp = true;
                        final int temp = val;
                        handler.post(() -> onInsideTempEvent(temp));
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "get({0x" + Integer.toHexString(fid) + "}) err: " + e.getMessage());
            }
        }
    }

    void onInsideTempEvent(int tempCelsius) {
        handler.post(() -> {
            if (tempCelsius != lastInsideTemp && isValidTemp(tempCelsius)) {
                lastInsideTemp = tempCelsius;
                insideTempAvailable = true;
                Log.i(TAG, "Inside temp: " + tempCelsius + "°C");
                if (listener != null) listener.onTemperatureChanged(tempCelsius);
            }
        });
    }

    void onSetTempEvent(int tempCelsius) {
        handler.post(() -> {
            if (tempCelsius != lastSetTemp && isValidSetTemp(tempCelsius)) {
                lastSetTemp = tempCelsius;
                Log.i(TAG, "Set temp: " + tempCelsius + "°C");
                if (listener != null) listener.onSetTempChanged(tempCelsius);
            }
        });
    }

    void onAcStartedEvent() {
        handler.post(() -> { if (listener != null) listener.onAcStatusChanged(true); });
    }

    void onAcStoppedEvent() {
        handler.post(() -> { if (listener != null) listener.onAcStatusChanged(false); });
    }

    private void startAmbientSensor() {
        try {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            ambientSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
            if (ambientSensor != null) {
                sensorManager.registerListener(this, ambientSensor, SensorManager.SENSOR_DELAY_NORMAL);
                sensorRegistered = true;
                Log.i(TAG, "Ambient sensor: " + ambientSensor.getName());
            } else {
                Log.i(TAG, "No ambient temperature sensor");
            }
        } catch (Exception e) {
            Log.w(TAG, "Sensor init failed: " + e.getMessage());
        }
    }

    private void stopAmbientSensor() {
        if (sensorRegistered && sensorManager != null) {
            sensorManager.unregisterListener(this);
            sensorRegistered = false;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            int temp = Math.round(event.values[0]);
            if (temp != lastSensorTemp && temp > -50 && temp < 100) {
                lastSensorTemp = temp;
                Log.i(TAG, "Ambient sensor (chip): " + temp + "°C");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    int getLastInsideTemp() { return lastInsideTemp; }
    int getLastSetTemp() { return lastSetTemp; }
    int getLastSensorTemp() { return lastSensorTemp; }
    boolean isInsideTempAvailable() { return insideTempAvailable; }
}
