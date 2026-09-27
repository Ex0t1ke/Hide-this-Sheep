package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

public class GamePrefs {
    private static final String PREFS_NAME = "sheep_wolves_prefs";
    private static final String KEY_MAX_LEVEL = "max_unlocked_level";
    private static final String KEY_ENDLESS_HIGH_SCORE = "endless_high_score";
    private static final String KEY_BGM_VOLUME = "bgm_volume";
    private static final String KEY_SFX_VOLUME = "sfx_volume";
    private static final String KEY_VIBRATION = "vibration_enabled";
    
    private final SharedPreferences prefs;
    
    public GamePrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    // Level progress — default is 2 (levels 1 and 2 unlocked initially)
    public int getMaxUnlockedLevel() { return Math.max(2, prefs.getInt(KEY_MAX_LEVEL, 2)); }
    public void setMaxUnlockedLevel(int level) { prefs.edit().putInt(KEY_MAX_LEVEL, Math.max(level, getMaxUnlockedLevel())).apply(); }
    
    // Endless mode
    public int getEndlessHighScore() { return prefs.getInt(KEY_ENDLESS_HIGH_SCORE, 0); }
    public void setEndlessHighScore(int score) {
        if (score > getEndlessHighScore()) {
            prefs.edit().putInt(KEY_ENDLESS_HIGH_SCORE, score).apply();
        }
    }
    
    // Audio settings
    public float getBgmVolume() { return prefs.getFloat(KEY_BGM_VOLUME, 0.7f); }
    public void setBgmVolume(float volume) { prefs.edit().putFloat(KEY_BGM_VOLUME, Math.max(0f, Math.min(1f, volume))).apply(); }
    
    public float getSfxVolume() { return prefs.getFloat(KEY_SFX_VOLUME, 0.8f); }
    public void setSfxVolume(float volume) { prefs.edit().putFloat(KEY_SFX_VOLUME, Math.max(0f, Math.min(1f, volume))).apply(); }
    
    // Vibration
    public boolean isVibrationEnabled() { return prefs.getBoolean(KEY_VIBRATION, true); }
    public void setVibrationEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply(); }
    
    // Level stars (0..3)
    public int getLevelStars(int level) {
        return prefs.getInt("stars_level_" + level, 0);
    }
    public void setLevelStars(int level, int stars) {
        int current = getLevelStars(level);
        if (stars > current) {
            prefs.edit().putInt("stars_level_" + level, Math.min(3, stars)).apply();
        }
    }
    public int getTotalStars() {
        int total = 0;
        for (int i = 1; i <= 30; i++) {
            total += getLevelStars(i);
        }
        return total;
    }

    // Reset progress (keep audio settings)
    public void resetProgress() {
        SharedPreferences.Editor editor = prefs.edit()
            .remove(KEY_MAX_LEVEL)
            .remove(KEY_ENDLESS_HIGH_SCORE);
        for (int i = 1; i <= 30; i++) {
            editor.remove("stars_level_" + i);
        }
        editor.apply();
    }
}
