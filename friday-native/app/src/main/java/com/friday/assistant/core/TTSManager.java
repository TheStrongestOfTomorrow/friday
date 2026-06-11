package com.friday.assistant.core;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Friday — TTS Manager
 *
 * Wraps Android's native TextToSpeech engine.
 * Real API — no mocks. Friday speaks back to the user.
 * Uses the modern speak() API (non-deprecated).
 *
 * FIX: Added a pending queue so that speak() calls made before
 * TTS initialization completes will still be spoken once ready.
 * This fixes the "Test Voice doesn't work" bug where the user
 * clicks Test Voice before onInit fires.
 */
public class TTSManager {

    private static final String TAG = "Friday/TTS";

    public interface TTSCallback {
        void onSpeakStart();
        void onSpeakComplete();
        void onSpeakError(String error);
    }

    private TextToSpeech tts;
    private boolean isReady = false;
    private TTSCallback callback;
    private float rate = 1.0f;
    private float pitch = 1.0f;

    // FIX: Queue for speak() calls made before TTS is ready
    private final Queue<String> pendingSpeech = new LinkedList<>();

    public TTSManager(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.getDefault());
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Language not supported, falling back to US English");
                    tts.setLanguage(Locale.US);
                }
                tts.setSpeechRate(rate);
                tts.setPitch(pitch);
                isReady = true;
                Log.d(TAG, "TTS initialized successfully");

                // FIX: Flush any pending speech that was requested before init
                flushPendingSpeech();
            } else {
                Log.e(TAG, "TTS initialization failed: " + status);
                isReady = false;
                // Notify callback about all pending speech that failed
                if (callback != null) {
                    while (!pendingSpeech.isEmpty()) {
                        pendingSpeech.poll();
                        callback.onSpeakError("TTS engine failed to initialize");
                    }
                }
            }
        });

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                if (callback != null) callback.onSpeakStart();
            }

            @Override
            public void onDone(String utteranceId) {
                if (callback != null) callback.onSpeakComplete();
            }

            @Override
            public void onError(String utteranceId) {
                if (callback != null) callback.onSpeakError("TTS playback error");
            }
        });
    }

    public void setCallback(TTSCallback callback) {
        this.callback = callback;
    }

    /**
     * Speak the given text aloud using the modern non-deprecated API.
     *
     * FIX: If TTS is not ready yet, queue the text and speak it
     * once initialization completes. This prevents silent failures
     * when the user clicks "Test Voice" right after opening settings.
     */
    public void speak(String text) {
        if (text == null || text.isEmpty()) return;

        if (!isReady || tts == null) {
            Log.w(TAG, "TTS not ready yet, queueing speech: " + text);
            pendingSpeech.add(text);
            return;
        }

        // Stop any current speech
        tts.stop();

        // Use the modern speak() API with Bundle (non-deprecated since API 21)
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "friday_utterance");

        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "friday_utterance");
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS speak() returned error");
            if (callback != null) callback.onSpeakError("speak() failed");
        }
    }

    /**
     * FIX: Flush any speech that was queued before TTS was ready.
     */
    private void flushPendingSpeech() {
        if (pendingSpeech.isEmpty()) return;

        Log.d(TAG, "Flushing " + pendingSpeech.size() + " pending speech items");

        // Speak the last one (most recent) — discard older ones
        String lastText = null;
        while (!pendingSpeech.isEmpty()) {
            lastText = pendingSpeech.poll();
        }

        if (lastText != null) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "friday_utterance");
            int result = tts.speak(lastText, TextToSpeech.QUEUE_FLUSH, params, "friday_utterance");
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "TTS speak() returned error for flushed speech");
                if (callback != null) callback.onSpeakError("speak() failed");
            }
        }
    }

    /**
     * Stop current speech immediately.
     */
    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void setRate(float rate) {
        this.rate = rate;
        if (tts != null && isReady) {
            tts.setSpeechRate(rate);
        }
    }

    public float getRate() {
        return rate;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
        if (tts != null && isReady) {
            tts.setPitch(pitch);
        }
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isReady() {
        return isReady;
    }

    /**
     * Release TTS resources. Call in Activity.onDestroy().
     */
    public void destroy() {
        pendingSpeech.clear();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            isReady = false;
        }
    }
}
