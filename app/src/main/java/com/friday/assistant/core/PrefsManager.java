package com.friday.assistant.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Friday — Preferences Manager
 *
 * Centralized SharedPreferences wrapper for all app settings.
 * Every setting is persisted immediately and read with type safety.
 */
public class PrefsManager {

    private static final String PREFS_NAME = "friday_prefs";

    // Keys
    public static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    public static final String KEY_WAKE_WORD = "wake_word";
    public static final String KEY_WAKE_WORD_ENABLED = "wake_word_enabled";
    public static final String KEY_CONFIDENCE_THRESHOLD = "confidence_threshold";
    public static final String KEY_TTS_RATE = "tts_rate";
    public static final String KEY_TTS_PITCH = "tts_pitch";
    public static final String KEY_PEEK_GUI_ENABLED = "peek_gui_enabled";
    public static final String KEY_STEALTH_MODE = "stealth_mode";
    public static final String KEY_START_ON_BOOT = "start_on_boot";
    public static final String KEY_BATTERY_OPTIMIZED = "battery_optimized";
    public static final String KEY_ASSISTANT_CONFIGURED = "assistant_configured";
    public static final String KEY_FIRST_LAUNCH = "first_launch";
    public static final String KEY_WAKE_WORD_SAMPLES = "wake_word_samples";
    public static final String KEY_AUDIO_TEST_PASSED = "audio_test_passed";
    public static final String KEY_DUCK_TESTED = "duck_tested";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Onboarding ────────────────────────────────────────────

    public boolean isOnboardingCompleted() {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    public void setOnboardingCompleted(boolean completed) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply();
    }

    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    public void setFirstLaunch(boolean first) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, first).apply();
    }

    // ─── Wake Word ─────────────────────────────────────────────

    public String getWakeWord() {
        return prefs.getString(KEY_WAKE_WORD, "Hey Friday");
    }

    public void setWakeWord(String word) {
        prefs.edit().putString(KEY_WAKE_WORD, word).apply();
    }

    public boolean isWakeWordEnabled() {
        return prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true);
    }

    public void setWakeWordEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply();
    }

    public float getConfidenceThreshold() {
        return prefs.getFloat(KEY_CONFIDENCE_THRESHOLD, 0.7f);
    }

    public void setConfidenceThreshold(float threshold) {
        prefs.edit().putFloat(KEY_CONFIDENCE_THRESHOLD, threshold).apply();
    }

    public int getWakeWordSamples() {
        return prefs.getInt(KEY_WAKE_WORD_SAMPLES, 0);
    }

    public void setWakeWordSamples(int count) {
        prefs.edit().putInt(KEY_WAKE_WORD_SAMPLES, count).apply();
    }

    // ─── TTS ───────────────────────────────────────────────────

    public float getTtsRate() {
        return prefs.getFloat(KEY_TTS_RATE, 1.0f);
    }

    public void setTtsRate(float rate) {
        prefs.edit().putFloat(KEY_TTS_RATE, rate).apply();
    }

    public float getTtsPitch() {
        return prefs.getFloat(KEY_TTS_PITCH, 1.0f);
    }

    public void setTtsPitch(float pitch) {
        prefs.edit().putFloat(KEY_TTS_PITCH, pitch).apply();
    }

    // ─── Display ───────────────────────────────────────────────

    public boolean isPeekGuiEnabled() {
        return prefs.getBoolean(KEY_PEEK_GUI_ENABLED, true);
    }

    public void setPeekGuiEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PEEK_GUI_ENABLED, enabled).apply();
    }

    public boolean isStealthMode() {
        return prefs.getBoolean(KEY_STEALTH_MODE, false);
    }

    public void setStealthMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_STEALTH_MODE, enabled).apply();
    }

    // ─── System ────────────────────────────────────────────────

    public boolean isStartOnBoot() {
        return prefs.getBoolean(KEY_START_ON_BOOT, true);
    }

    public void setStartOnBoot(boolean enabled) {
        prefs.edit().putBoolean(KEY_START_ON_BOOT, enabled).apply();
    }

    public boolean isBatteryOptimized() {
        return prefs.getBoolean(KEY_BATTERY_OPTIMIZED, false);
    }

    public void setBatteryOptimized(boolean optimized) {
        prefs.edit().putBoolean(KEY_BATTERY_OPTIMIZED, optimized).apply();
    }

    public boolean isAssistantConfigured() {
        return prefs.getBoolean(KEY_ASSISTANT_CONFIGURED, false);
    }

    public void setAssistantConfigured(boolean configured) {
        prefs.edit().putBoolean(KEY_ASSISTANT_CONFIGURED, configured).apply();
    }

    // ─── Onboarding State ──────────────────────────────────────

    public boolean isAudioTestPassed() {
        return prefs.getBoolean(KEY_AUDIO_TEST_PASSED, false);
    }

    public void setAudioTestPassed(boolean passed) {
        prefs.edit().putBoolean(KEY_AUDIO_TEST_PASSED, passed).apply();
    }

    public boolean isDuckTested() {
        return prefs.getBoolean(KEY_DUCK_TESTED, false);
    }

    public void setDuckTested(boolean tested) {
        prefs.edit().putBoolean(KEY_DUCK_TESTED, tested).apply();
    }

    // ─── Reset ─────────────────────────────────────────────────

    public void resetAll() {
        prefs.edit().clear().apply();
        // Restore first launch so onboarding runs
        setFirstLaunch(true);
    }
}
