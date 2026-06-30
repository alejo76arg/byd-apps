package com.alejo.enginesound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(
                EngineSoundService.PREFS_NAME, Context.MODE_PRIVATE);
        boolean autostart = prefs.getBoolean(EngineSoundService.PREF_AUTOSTART, false);
        if (!autostart) return;

        Intent serviceIntent = new Intent(context, EngineSoundService.class);
        serviceIntent.setAction(EngineSoundService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
