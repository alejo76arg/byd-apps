package com.wheregoes.petmode;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

public class PetModeServiceConnection implements ServiceConnection {
    private final MainActivity activity;

    PetModeServiceConnection(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        activity.onServiceBound(((PetModeBinder) binder).getService());
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        activity.onServiceUnbound();
    }
}
