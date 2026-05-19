package com.wheregoes.petmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("PetModeBoot", "Boot received: " + intent.getAction());
        SharedPreferences prefs = context.getSharedPreferences(
                PetModeService.PREF_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PetModeService.KEY_ENABLED, false)) {
            context.startForegroundService(new Intent(context, PetModeService.class));
            Log.i("PetModeBoot", "Service started on boot");
        }
    }
}
