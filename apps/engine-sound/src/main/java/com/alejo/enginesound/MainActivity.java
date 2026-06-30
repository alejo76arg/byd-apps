package com.alejo.enginesound;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_LOCATION = 100;

    private TextView textStatus;
    private TextView textSpeed;
    private SeekBar seekMaxSpeed;
    private SeekBar seekMaxPitch;
    private SeekBar seekMinVolume;
    private CheckBox checkAutostart;

    private final BroadcastReceiver speedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            float speed = intent.getFloatExtra(EngineSoundService.EXTRA_SPEED_KMH, 0f);
            String source = intent.getStringExtra(EngineSoundService.EXTRA_SOURCE);
            textSpeed.setText(String.format("%.0f km/h (%s)", speed, source));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textStatus = findViewById(R.id.text_status);
        textSpeed = findViewById(R.id.text_speed);
        seekMaxSpeed = findViewById(R.id.seek_max_speed);
        seekMaxPitch = findViewById(R.id.seek_max_pitch);
        seekMinVolume = findViewById(R.id.seek_min_volume);
        checkAutostart = findViewById(R.id.check_autostart);

        Button btnStart = findViewById(R.id.btn_start);
        Button btnStop = findViewById(R.id.btn_stop);

        loadPrefsIntoUi();

        btnStart.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                startEngineService();
            } else {
                requestLocationPermission();
            }
        });

        btnStop.setOnClickListener(v -> stopEngineService());

        checkAutostart.setOnCheckedChangeListener((buttonView, isChecked) ->
                savePref(EngineSoundService.PREF_AUTOSTART, isChecked));

        seekMaxSpeed.setOnSeekBarChangeListener(new SimpleSeekListener(
                value -> savePref(EngineSoundService.PREF_MAX_SPEED_KMH, Math.max(20, value))));
        seekMaxPitch.setOnSeekBarChangeListener(new SimpleSeekListener(
                value -> savePref(EngineSoundService.PREF_MAX_PITCH, Math.max(100, value))));
        seekMinVolume.setOnSeekBarChangeListener(new SimpleSeekListener(
                value -> savePref(EngineSoundService.PREF_MIN_VOLUME, value)));

        updateStatusLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(EngineSoundService.ACTION_SPEED_UPDATE);
        registerReceiver(speedReceiver, filter);
        updateStatusLabel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(speedReceiver);
        } catch (IllegalArgumentException ignored) {
            // no estaba registrado, no pasa nada
        }
    }

    private void loadPrefsIntoUi() {
        SharedPreferences prefs = getSharedPreferences(EngineSoundService.PREFS_NAME, MODE_PRIVATE);
        seekMaxSpeed.setProgress(prefs.getInt(EngineSoundService.PREF_MAX_SPEED_KMH, 120));
        seekMaxPitch.setProgress(prefs.getInt(EngineSoundService.PREF_MAX_PITCH, 160));
        seekMinVolume.setProgress(prefs.getInt(EngineSoundService.PREF_MIN_VOLUME, 30));
        checkAutostart.setChecked(prefs.getBoolean(EngineSoundService.PREF_AUTOSTART, false));
    }

    private void savePref(String key, int value) {
        getSharedPreferences(EngineSoundService.PREFS_NAME, MODE_PRIVATE)
                .edit().putInt(key, value).apply();
    }

    private void savePref(String key, boolean value) {
        getSharedPreferences(EngineSoundService.PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(key, value).apply();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startEngineService();
        }
    }

    private void startEngineService() {
        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean locationEnabled = lm != null && (
                lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                        || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER));

        if (!locationEnabled) {
            Toast.makeText(this, "Activá la ubicación del sistema primero", Toast.LENGTH_LONG).show();
            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        Intent intent = new Intent(this, EngineSoundService.class);
        intent.setAction(EngineSoundService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        updateStatusLabel();
    }

    private void stopEngineService() {
        Intent intent = new Intent(this, EngineSoundService.class);
        intent.setAction(EngineSoundService.ACTION_STOP);
        startService(intent);
        updateStatusLabel();
    }

    private void updateStatusLabel() {
        boolean running = getSharedPreferences(EngineSoundService.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(EngineSoundService.PREF_RUNNING, false);
        textStatus.setText(running ? getString(R.string.status_running) : getString(R.string.status_stopped));
    }

    /** Listener mínimo para no repetir los 3 métodos vacíos de SeekBar en cada caso de uso. */
    private interface OnValueChanged {
        void onChanged(int value);
    }

    private static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        private final OnValueChanged callback;

        SimpleSeekListener(OnValueChanged callback) {
            this.callback = callback;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) callback.onChanged(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
