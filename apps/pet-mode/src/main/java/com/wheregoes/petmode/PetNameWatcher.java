package com.wheregoes.petmode;

import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;

public class PetNameWatcher implements TextWatcher {
    private final SharedPreferences prefs;

    PetNameWatcher(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        prefs.edit().putString(PetModeService.KEY_PET_NAME, s.toString().trim()).apply();
    }
}
