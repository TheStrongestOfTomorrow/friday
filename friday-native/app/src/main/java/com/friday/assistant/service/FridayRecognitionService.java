package com.friday.assistant.service;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.util.Log;

/**
 * Friday — Recognition Service
 *
 * REQUIRED for Friday to appear in Android's assistant picker on
 * Android 13+ (API 33). The system requires VoiceInteractionService
 * to reference a RecognitionService via the
 * android:recognitionService attribute in voice_interaction.xml.
 *
 * This service delegates to the device's default speech recognizer
 * (Google Speech Services) so Friday works with whatever recognizer
 * the user has installed — no custom ASR engine needed.
 */
public class FridayRecognitionService extends RecognitionService {

    private static final String TAG = "Friday/RecognitionSvc";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Recognition service created");
    }

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        Log.d(TAG, "onStartListening called — delegating to system recognizer");

        if (listener == null) {
            Log.w(TAG, "Listener callback is null");
            return;
        }

        // We delegate to the system's own speech recognizer.
        // This is the standard pattern for VoiceInteraction apps that
        // don't implement their own ASR engine.
        try {
            // Signal that we're ready for speech
            listener.readyForSpeech(new Bundle());

            // The actual speech recognition is handled by FridaySession
            // which uses FridaySpeechRecognizer (wrapping Google Speech Services).
            // This service's existence is what makes Android show Friday in the
            // assistant picker — the real recognition happens in the session layer.
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartListening", e);
            try {
                listener.error(SpeechRecognizer.ERROR_CLIENT);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onCancel(Callback listener) {
        Log.d(TAG, "onCancel called");
    }

    @Override
    protected void onStopListening(Callback listener) {
        Log.d(TAG, "onStopListening called");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Recognition service destroyed");
    }
}
