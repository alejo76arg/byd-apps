package com.wheregoes.petmode;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public class SettingsActivity extends Activity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE);
        setupPetName();
        setupAvatarSelection();
        setupTempUnit();
        setupThemeToggle();
        setupBackButton();
    }

    private void setupPetName() {
        EditText nameInput = findViewById(R.id.pet_name_input);
        String name = prefs.getString(PetModeService.KEY_PET_NAME, "");
        nameInput.setText(name);
        nameInput.setHint(R.string.default_pet_name);
        nameInput.addTextChangedListener(new PetNameWatcher(prefs));
    }

    private void setupAvatarSelection() {
        String current = prefs.getString(PetModeService.KEY_AVATAR, "paw");

        View pawOption = findViewById(R.id.avatar_paw);
        View dogOption = findViewById(R.id.avatar_dog);
        View catOption = findViewById(R.id.avatar_cat);

        highlightAvatar(current);

        pawOption.setOnClickListener(v -> selectAvatar("paw"));
        dogOption.setOnClickListener(v -> selectAvatar("dog"));
        catOption.setOnClickListener(v -> selectAvatar("cat"));
    }

    private void selectAvatar(String avatar) {
        prefs.edit().putString(PetModeService.KEY_AVATAR, avatar).apply();
        highlightAvatar(avatar);
    }

    private void highlightAvatar(String selected) {
        int active = 0xFF00B894;
        int inactive = 0xFFE0E0E0;

        findViewById(R.id.avatar_paw_border).setBackgroundColor("paw".equals(selected) ? active : inactive);
        findViewById(R.id.avatar_dog_border).setBackgroundColor("dog".equals(selected) ? active : inactive);
        findViewById(R.id.avatar_cat_border).setBackgroundColor("cat".equals(selected) ? active : inactive);
    }

    private void setupTempUnit() {
        RadioGroup unitGroup = findViewById(R.id.temp_unit_group);
        RadioButton celsius = findViewById(R.id.unit_celsius);
        RadioButton fahrenheit = findViewById(R.id.unit_fahrenheit);

        String current = prefs.getString(PetModeService.KEY_TEMP_UNIT, getDefaultUnit());
        if (PetModeService.UNIT_FAHRENHEIT.equals(current)) {
            fahrenheit.setChecked(true);
        } else {
            celsius.setChecked(true);
        }

        unitGroup.setOnCheckedChangeListener((group, id) -> {
            String unit = (id == R.id.unit_fahrenheit)
                    ? PetModeService.UNIT_FAHRENHEIT : PetModeService.UNIT_CELSIUS;
            prefs.edit().putString(PetModeService.KEY_TEMP_UNIT, unit).apply();
        });
    }

    private void setupThemeToggle() {
        Switch themeSwitch = findViewById(R.id.theme_toggle);
        themeSwitch.setChecked(prefs.getBoolean(PetModeService.KEY_DARK_MODE, false));
        themeSwitch.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(PetModeService.KEY_DARK_MODE, checked).apply());
    }

    private void setupBackButton() {
        findViewById(R.id.back_btn).setOnClickListener(v -> finish());
    }

    private String getDefaultUnit() {
        String country = Locale.getDefault().getCountry();
        return ("US".equals(country) || "LR".equals(country) || "MM".equals(country))
                ? PetModeService.UNIT_FAHRENHEIT : PetModeService.UNIT_CELSIUS;
    }
}
