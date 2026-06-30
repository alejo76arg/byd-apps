package com.alejo.enginesound;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * Lee velocidad por GPS y modula pitch/volumen de un loop de audio para
 * simular el sonido de un motor a combustión en función de la velocidad
 * y la aceleración instantánea.
 *
 * No tiene ningún vínculo con el bus CAN del auto: es 100% Android estándar
 * (LocationManager + MediaPlayer), así que solo puede sonar por los
 * parlantes internos. El parlante exterior (AVAS) está bloqueado por
 * firmware para audio custom, ver limitaciones del proyecto door-sound.
 */
public class EngineSoundService extends Service implements LocationListener {

    private static final String TAG = "EngineSoundService";
    private static final String CHANNEL_ID = "engine_sound_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "com.alejo.enginesound.START";
    public static final String ACTION_STOP = "com.alejo.enginesound.STOP";

    public static final String PREFS_NAME = "engine_sound_prefs";
    public static final String PREF_MAX_SPEED_KMH = "max_speed_kmh";
    public static final String PREF_MAX_PITCH = "max_pitch_x100";
    public static final String PREF_MIN_VOLUME = "min_volume_pct";
    public static final String PREF_AUTOSTART = "autostart_enabled";
    public static final String PREF_RUNNING = "is_running";

    // Defaults, sobreescritos por SharedPreferences si el usuario los calibró
    private float maxSpeedKmh = 120f;
    private float maxPitch = 1.6f;
    private float minPitch = 0.85f;
    private float minVolume = 0.30f;
    private float maxVolume = 1.0f;

    // Suavizado exponencial de velocidad para que el motor no "tartamudee"
    private static final float SMOOTHING_ALPHA = 0.25f;
    private float smoothedSpeedKmh = 0f;

    // Detección de aceleración brusca para un "kick" de revolución extra
    private long lastUpdateTimeMs = 0L;
    private float lastSpeedKmh = 0f;
    private static final float KICK_ACCEL_THRESHOLD_KMH_S = 8f; // km/h por segundo
    private static final float KICK_PITCH_BOOST = 0.15f;
    private long kickUntilMs = 0L;
    private static final long KICK_DURATION_MS = 350L;

    private LocationManager locationManager;
    private MediaPlayer mediaPlayer;

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        loadCalibration();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopEngine();
            stopSelf();
            return START_NOT_STICKY;
        }

        // Default: arrancar (cubre ACTION_START y el caso de reinicio por sistema)
        loadCalibration();
        startForeground(NOTIFICATION_ID, buildNotification());
        startEngine();
        setRunningFlag(true);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopEngine();
        setRunningFlag(false);
        super.onDestroy();
    }

    private void loadCalibration() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        maxSpeedKmh = prefs.getInt(PREF_MAX_SPEED_KMH, 120);
        maxPitch = prefs.getInt(PREF_MAX_PITCH, 160) / 100f;
        minVolume = prefs.getInt(PREF_MIN_VOLUME, 30) / 100f;
    }

    private void setRunningFlag(boolean running) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_RUNNING, running)
                .apply();
    }

    private void startEngine() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(this, R.raw.engine_loop);
                if (mediaPlayer == null) {
                    Log.e(TAG, "No se pudo cargar res/raw/engine_loop. Reemplazá el placeholder por tu sample de motor.");
                    return;
                }
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(minVolume, minVolume);
            }
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }

            if (locationManager != null) {
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 200L, 0f, this);
                } catch (SecurityException se) {
                    Log.e(TAG, "Falta permiso de ubicación", se);
                }
                try {
                    // Fallback con red/celda mientras el GPS puro fija señal
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, 200L, 0f, this);
                } catch (Exception ignored) {
                    // No todos los head units tienen NETWORK_PROVIDER habilitado, no es crítico
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error iniciando el motor simulado", e);
        }
    }

    private void stopEngine() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {
            }
        }
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        smoothedSpeedKmh = 0f;
        lastSpeedKmh = 0f;
    }

    @Override
    public void onLocationChanged(Location location) {
        long now = System.currentTimeMillis();

        float rawSpeedKmh;
        if (location.hasSpeed()) {
            rawSpeedKmh = location.getSpeed() * 3.6f; // m/s -> km/h
        } else if (lastUpdateTimeMs != 0) {
            // Fallback si el dispositivo no reporta speed directamente
            return;
        } else {
            rawSpeedKmh = 0f;
        }

        // Suavizado exponencial
        smoothedSpeedKmh = smoothedSpeedKmh + SMOOTHING_ALPHA * (rawSpeedKmh - smoothedSpeedKmh);

        // Detección de aceleración brusca (kick de revolución, simula bajar un cambio / pisar fondo)
        if (lastUpdateTimeMs != 0) {
            float dtSeconds = (now - lastUpdateTimeMs) / 1000f;
            if (dtSeconds > 0.05f) {
                float accelKmhPerSec = (rawSpeedKmh - lastSpeedKmh) / dtSeconds;
                if (accelKmhPerSec > KICK_ACCEL_THRESHOLD_KMH_S) {
                    kickUntilMs = now + KICK_DURATION_MS;
                }
            }
        }
        lastUpdateTimeMs = now;
        lastSpeedKmh = rawSpeedKmh;

        applyEngineSound(smoothedSpeedKmh, now < kickUntilMs);
    }

    private void applyEngineSound(float speedKmh, boolean kicking) {
        if (mediaPlayer == null) return;

        float t = clamp(speedKmh / maxSpeedKmh, 0f, 1f);

        float pitch = minPitch + t * (maxPitch - minPitch);
        float volume = minVolume + t * (maxVolume - minVolume);

        if (kicking) {
            pitch += KICK_PITCH_BOOST;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PlaybackParams params = mediaPlayer.getPlaybackParams();
                params.setPitch(pitch);
                params.setSpeed(1.0f); // mantenemos tempo, solo cambiamos el tono
                mediaPlayer.setPlaybackParams(params);
            }
            mediaPlayer.setVolume(volume, volume);
        } catch (Exception e) {
            // Algunos decoders no soportan cambios de pitch en caliente; no es fatal
            Log.w(TAG, "No se pudo ajustar pitch en este frame", e);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}
