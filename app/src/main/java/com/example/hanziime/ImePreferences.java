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

    private ImePreferences() {}

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean enabled(SharedPreferences preferences, String key) {
        return preferences.getBoolean(key, true);
    }
}
