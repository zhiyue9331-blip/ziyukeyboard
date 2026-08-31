package com.example.hanziime;

import android.content.Context;
import android.content.SharedPreferences;

public final class ImePreferences {
    public static final String FILE = "ime_preferences";
    public static final String FUZZY = "fuzzy_pinyin";
    public static final String LEARNING = "local_learning";
    public static final String VIBRATION = "key_vibration";
    public static final String SOUND = "key_sound";
    public static final String THEME = "theme";
    public static final String CUSTOM_PANEL_COLOR = "custom_panel_color";
    public static final String CUSTOM_KEY_COLOR = "custom_key_color";
    public static final String CUSTOM_TEXT_COLOR = "custom_text_color";
    public static final String CUSTOM_ACCENT_COLOR = "custom_accent_color";
    public static final String CUSTOM_CORNER_RADIUS = "custom_corner_radius";
    public static final String CUSTOM_SKIN_NAME = "custom_skin_name";

    private ImePreferences() {}

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean enabled(SharedPreferences preferences, String key) {
        return preferences.getBoolean(key, true);
    }
}
