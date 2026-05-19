package com.wheregoes.petmode;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public class SettingsActivity extends Activity {

    private SharedPreferences prefs;
    private TextView unitCelsius;
    private TextView unitFahrenheit;
    private View auroraBg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE);
        auroraBg = findViewById(R.id.aurora_bg);
        setupPetName();
        setupAvatarSelection();
        setupTempUnit();
        setupThemeToggle();
        setupBackButton();
        applyTheme();
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
        View pawBorder = findViewById(R.id.avatar_paw_border);
        View dogBorder = findViewById(R.id.avatar_dog_border);
        View catBorder = findViewById(R.id.avatar_cat_border);

        pawBorder.setBackgroundResource("paw".equals(selected)
                ? R.drawable.glass_avatar_tile_selected : R.drawable.glass_avatar_tile);
        dogBorder.setBackgroundResource("dog".equals(selected)
                ? R.drawable.glass_avatar_tile_selected : R.drawable.glass_avatar_tile);
        catBorder.setBackgroundResource("cat".equals(selected)
                ? R.drawable.glass_avatar_tile_selected : R.drawable.glass_avatar_tile);
    }

    private void setupTempUnit() {
        unitCelsius = findViewById(R.id.unit_celsius);
        unitFahrenheit = findViewById(R.id.unit_fahrenheit);

        String current = prefs.getString(PetModeService.KEY_TEMP_UNIT, getDefaultUnit());
        updateSegmentedState(PetModeService.UNIT_FAHRENHEIT.equals(current));

        unitCelsius.setOnClickListener(v -> {
            prefs.edit().putString(PetModeService.KEY_TEMP_UNIT, PetModeService.UNIT_CELSIUS).apply();
            updateSegmentedState(false);
        });

        unitFahrenheit.setOnClickListener(v -> {
            prefs.edit().putString(PetModeService.KEY_TEMP_UNIT, PetModeService.UNIT_FAHRENHEIT).apply();
            updateSegmentedState(true);
        });
    }

    private void updateSegmentedState(boolean isFahrenheit) {
        boolean isDark = prefs.getBoolean(PetModeService.KEY_DARK_MODE, false);
        int activeBg = isDark ? R.drawable.glass_segmented_active_dark : R.drawable.glass_segmented_active;
        int activeColor = isDark ? 0xFFFFFFFF : 0xFF1A1A2E;
        int inactiveColor = isDark ? 0xB8FFFFFF : 0xFF636E7B;

        if (isFahrenheit) {
            unitFahrenheit.setBackgroundResource(activeBg);
            unitFahrenheit.setTextColor(activeColor);
            unitCelsius.setBackgroundResource(0);
            unitCelsius.setTextColor(inactiveColor);
        } else {
            unitCelsius.setBackgroundResource(activeBg);
            unitCelsius.setTextColor(activeColor);
            unitFahrenheit.setBackgroundResource(0);
            unitFahrenheit.setTextColor(inactiveColor);
        }
    }

    private void setupThemeToggle() {
        Switch themeSwitch = findViewById(R.id.theme_toggle);
        themeSwitch.setChecked(prefs.getBoolean(PetModeService.KEY_DARK_MODE, false));
        themeSwitch.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(PetModeService.KEY_DARK_MODE, checked).apply();
            applyTheme();
        });
    }

    private void setupBackButton() {
        findViewById(R.id.back_btn).setOnClickListener(v -> finish());
    }

    private void applyTheme() {
        boolean isDark = prefs.getBoolean(PetModeService.KEY_DARK_MODE, false);
        if (isDark) {
            auroraBg.setBackgroundResource(R.drawable.aurora_dark);
        } else {
            auroraBg.setBackgroundResource(R.drawable.aurora_light);
        }
        String current = prefs.getString(PetModeService.KEY_TEMP_UNIT, getDefaultUnit());
        updateSegmentedState(PetModeService.UNIT_FAHRENHEIT.equals(current));
    }

    private String getDefaultUnit() {
        String country = Locale.getDefault().getCountry();
        return ("US".equals(country) || "LR".equals(country) || "MM".equals(country))
                ? PetModeService.UNIT_FAHRENHEIT : PetModeService.UNIT_CELSIUS;
    }
}
