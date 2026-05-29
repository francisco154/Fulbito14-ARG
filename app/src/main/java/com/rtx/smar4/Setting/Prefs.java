package com.rtx.smar4.Setting;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Minimal Prefs wrapper for native library compatibility
 */
public final class Prefs {
    private static SharedPreferences mPrefs;

    public static void initPrefs(Context context, String prefsName, int mode) {
        mPrefs = context.getSharedPreferences(prefsName, mode);
    }

    public static String getString(String key) {
        if (mPrefs == null) return "";
        return mPrefs.getString(key, "");
    }

    public static String getString(String key, String defValue) {
        if (mPrefs == null) return defValue;
        return mPrefs.getString(key, defValue);
    }

    public static void putString(String key, String value) {
        if (mPrefs == null) return;
        mPrefs.edit().putString(key, value).apply();
    }
}
