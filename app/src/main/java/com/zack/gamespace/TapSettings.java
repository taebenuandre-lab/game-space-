package com.zack.gamespace;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Penyimpanan pengaturan auto tap dan daftar favorit game.
 */
public final class TapSettings {

    public static final int MIN_INTERVAL_MS = 50;
    public static final int MAX_INTERVAL_MS = 5000;
    public static final int DEFAULT_INTERVAL_MS = 200;

    public static final int MIN_HOLD_MS = 10;
    public static final int MAX_HOLD_MS = 300;
    public static final int DEFAULT_HOLD_MS = 40;

    /** Radius acak (piksel) bila mode jitter aktif. */
    public static final int JITTER_RADIUS_PX = 6;

    private static final String PREFS = "zack_game_space_prefs";
    private static final String KEY_INTERVAL = "interval_ms";
    private static final String KEY_HOLD = "hold_ms";
    private static final String KEY_REPEAT = "repeat_count";
    private static final String KEY_JITTER = "jitter";
    private static final String KEY_TARGET_X = "target_x";
    private static final String KEY_TARGET_Y = "target_y";
    private static final String KEY_AUTO_OVERLAY = "auto_overlay";
    private static final String KEY_FAVORITES = "favorites";

    private final SharedPreferences prefs;

    public TapSettings(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getIntervalMs() {
        return clamp(prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_MS), MIN_INTERVAL_MS, MAX_INTERVAL_MS);
    }

    public void setIntervalMs(int value) {
        prefs.edit().putInt(KEY_INTERVAL, clamp(value, MIN_INTERVAL_MS, MAX_INTERVAL_MS)).apply();
    }

    public int getHoldMs() {
        return clamp(prefs.getInt(KEY_HOLD, DEFAULT_HOLD_MS), MIN_HOLD_MS, MAX_HOLD_MS);
    }

    public void setHoldMs(int value) {
        prefs.edit().putInt(KEY_HOLD, clamp(value, MIN_HOLD_MS, MAX_HOLD_MS)).apply();
    }

    /** 0 berarti tanpa batas. */
    public int getRepeatCount() {
        return Math.max(0, prefs.getInt(KEY_REPEAT, 0));
    }

    public void setRepeatCount(int value) {
        prefs.edit().putInt(KEY_REPEAT, Math.max(0, value)).apply();
    }

    public boolean isJitter() {
        return prefs.getBoolean(KEY_JITTER, false);
    }

    public void setJitter(boolean value) {
        prefs.edit().putBoolean(KEY_JITTER, value).apply();
    }

    public boolean hasTarget() {
        return prefs.contains(KEY_TARGET_X) && prefs.contains(KEY_TARGET_Y);
    }

    public int getTargetX() {
        return prefs.getInt(KEY_TARGET_X, 0);
    }

    public int getTargetY() {
        return prefs.getInt(KEY_TARGET_Y, 0);
    }

    public void setTarget(int x, int y) {
        prefs.edit().putInt(KEY_TARGET_X, x).putInt(KEY_TARGET_Y, y).apply();
    }

    public boolean isAutoOverlay() {
        return prefs.getBoolean(KEY_AUTO_OVERLAY, true);
    }

    public void setAutoOverlay(boolean value) {
        prefs.edit().putBoolean(KEY_AUTO_OVERLAY, value).apply();
    }

    public Set<String> getFavorites() {
        Set<String> stored = prefs.getStringSet(KEY_FAVORITES, null);
        // Salin: set bawaan SharedPreferences tidak boleh dimodifikasi.
        return stored == null ? new HashSet<String>() : new HashSet<String>(stored);
    }

    /** @return true bila setelah toggle paket menjadi favorit. */
    public boolean toggleFavorite(String packageName) {
        Set<String> favorites = getFavorites();
        boolean nowFavorite;
        if (favorites.contains(packageName)) {
            favorites.remove(packageName);
            nowFavorite = false;
        } else {
            favorites.add(packageName);
            nowFavorite = true;
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
        return nowFavorite;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
