package com.wheregoes.petmode;

import android.os.Binder;

public class PetModeBinder extends Binder {
    private final PetModeService service;

    PetModeBinder(PetModeService service) {
        this.service = service;
    }

    PetModeService getService() { return service; }
}
