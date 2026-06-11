package com.friday.assistant.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Friday — Speech Recognizer
 *
 * Wraps Android's native SpeechRecognizer with the Shush Protocol:
 *   - startListening() auto-ducks audio via AudioFocusManager
 *   - On completion/error, audio is automatically resumed
 *
 * Handles the "Speech recognition not available" error by:
 *   1. Checking isRecognitionAvailable() before creating the recognizer
 *   2. Providing a helper method to detect if Google app is installed
 *   3. Offering to open Google Play to install the Google app
 *
 * No mocks. Uses real Android SpeechRecognizer API.
 */
public class FridaySpeechRecognizer {

    private static final String TAG = "Friday/Speech";

    public interface SpeechCallback {
        void onPartialResult(String text);
        void onFinalResult(String text);
        void onError(String message, int errorCode);
        void onRmsChanged(float rmsdB);
        void onListeningStart();
        void onListeningEnd();
    }

    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private final AudioFocusManager audioFocusManager;
    private SpeechCallback callback;
    private boolean isListening = false;
    private boolean finishRecordingCalled = false;

    public FridaySpeechRecognizer(Context context, AudioFocusManager audioFocusManager) {
        this.context = context;
        this.audioFocusManager = audioFocusManager;
    }

    /**
     * Set the callback to receive speech recognition events.
     */
    public void setCallback(SpeechCallback callback) {
        this.callback = callback;
    }

    /**
     * Check if speech recognition is available on this device.
     * This checks the Android SpeechRecognizer.isRecognitionAvailable() API.
     */
    public boolean isAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    /**
     * Check if the Google app (which provides the speech recognition service)
     * is installed on this device. The Google app is required for
     * SpeechRecognizer to work on most Android devices.
     */
    public static boolean isGoogleAppInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo("com.google.android.googlequicksearchbox", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Get an intent to open the Google app on Google Play Store.
     * Used when speech recognition is not available because the
     * Google app is not installed.
     */
    public static Intent getGoogleAppInstallIntent(Context ctx) {
        try {
            return new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.googlequicksearchbox"));
        } catch (Exception e) {
            // Fallback to browser URL
            return new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox"));
        }
    }

    /**
     * Start listening with the Shush Protocol.
     * Audio is automatically ducked before listening begins.
     *
     * If speech recognition is not available, calls onError with
     * a descriptive message and GOOGLE_APP_MISSING error code.
     */
    public void startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening, ignoring start request");
            return;
        }

        finishRecordingCalled = false;

        // Check if speech recognition is available on this device
        if (!isAvailable()) {
            String message;
            if (!isGoogleAppInstalled(context)) {
                message = "Speech recognition requires the Google app. Please install it from the Play Store.";
            } else {
                message = "Speech recognition is not available on this device. Try restarting your device.";
            }
            Log.e(TAG, message);

            if (callback != null) {
                callback.onError(message, ERROR_RECOGNIZER_UNAVAILABLE);
            }
            return;
        }

        // Try to create the SpeechRecognizer — this can fail if the
        // recognizer service is not properly configured
        try {
            // Destroy any existing recognizer
            destroyRecognizer();

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            if (speechRecognizer == null) {
                Log.e(TAG, "createSpeechRecognizer returned null");
                if (callback != null) {
                    callback.onError("Could not create speech recognizer. Please ensure the Google app is installed and updated.", ERROR_RECOGNIZER_UNAVAILABLE);
                }
                return;
            }

            speechRecognizer.setRecognitionListener(new FridayRecognitionListener());
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException creating SpeechRecognizer", e);
            if (callback != null) {
                callback.onError("Microphone permission is required for speech recognition.", SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
            }
            return;
        } catch (Exception e) {
            Log.e(TAG, "Exception creating SpeechRecognizer", e);
            if (callback != null) {
                callback.onError("Speech recognition failed to start: " + e.getMessage(), ERROR_RECOGNIZER_UNAVAILABLE);
            }
            return;
        }

        // Shush Protocol: duck audio first
        audioFocusManager.duckAudio();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);

        try {
            speechRecognizer.startListening(intent);
            isListening = true;
            Log.d(TAG, "Started listening (Shush Protocol active)");

            if (callback != null) {
                callback.onListeningStart();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException starting listening", e);
            isListening = false;
            audioFocusManager.resumeAudio();
            if (callback != null) {
                callback.onError("Microphone permission is required for speech recognition.", SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception starting listening", e);
            isListening = false;
            audioFocusManager.resumeAudio();
            if (callback != null) {
                callback.onError("Failed to start listening: " + e.getMessage(), ERROR_RECOGNIZER_UNAVAILABLE);
            }
        }
    }

    /**
     * Stop listening and resume audio.
     */
    public void stopListening() {
        if (!isListening || speechRecognizer == null) {
            return;
        }

        try {
            speechRecognizer.stopListening();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recognizer", e);
        }

        isListening = false;
        // Shush Protocol: resume audio
        audioFocusManager.resumeAudio();

        if (callback != null) {
            callback.onListeningEnd();
        }
    }

    /**
     * Cancel listening immediately without processing results.
     */
    public void cancel() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception e) {
                Log.e(TAG, "Error canceling recognizer", e);
            }
        }
        isListening = false;
        audioFocusManager.resumeAudio();

        if (callback != null) {
            callback.onListeningEnd();
        }
    }

    /**
     * Clean up resources.
     */
    public void destroy() {
        destroyRecognizer();
        isListening = false;
    }

    private void destroyRecognizer() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error destroying recognizer", e);
            }
            speechRecognizer = null;
        }
    }

    public boolean isListening() {
        return isListening;
    }

    /**
     * Mark that finishRecording has been called (used to prevent double-increment).
     */
    public void setFinishRecordingCalled() {
        finishRecordingCalled = true;
    }

    public boolean isFinishRecordingCalled() {
        return finishRecordingCalled;
    }

    /**
     * Reset the finishRecording flag. Called when starting a new recording.
     */
    public void resetFinishRecordingCalled() {
        finishRecordingCalled = false;
    }

    // Custom error codes
    public static final int ERROR_RECOGNIZER_UNAVAILABLE = 100;

    // ─── Recognition Listener Implementation ───────────────────

    private class FridayRecognitionListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Ready for speech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            if (callback != null) {
                callback.onRmsChanged(rmsdB);
            }
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            // Not used
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "End of speech");
            isListening = false;
            if (callback != null) {
                callback.onListeningEnd();
            }
        }

        @Override
        public void onError(int error) {
            isListening = false;
            // Shush Protocol: resume audio on error
            audioFocusManager.resumeAudio();

            String message;
            switch (error) {
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    message = "Network timeout — check your internet connection";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    message = "Network error — check your internet connection";
                    break;
                case SpeechRecognizer.ERROR_AUDIO:
                    message = "Audio recording error — try again";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    message = "Server error — try again in a moment";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    message = "Client error — try restarting the app";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "No speech detected — try speaking louder or moving closer to the mic";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    message = "Could not understand — please try again";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "Recognizer is busy — wait a moment and try again";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "Microphone permission required — please grant microphone access";
                    break;
                default:
                    message = "Speech recognition error (code: " + error + ")";
                    break;
            }

            Log.e(TAG, "Recognition error: " + message + " (code: " + error + ")");

            if (callback != null) {
                callback.onError(message, error);
            }
        }

        @Override
        public void onResults(Bundle results) {
            isListening = false;
            // Shush Protocol: resume audio after final result
            audioFocusManager.resumeAudio();

            ArrayList<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);

            String text = "";
            if (matches != null && !matches.isEmpty()) {
                text = matches.get(0);
            }

            Log.d(TAG, "Final result: " + text);

            if (callback != null) {
                callback.onFinalResult(text);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> partial = partialResults.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);

            String text = "";
            if (partial != null && !partial.isEmpty()) {
                text = partial.get(0);
            }

            if (callback != null) {
                callback.onPartialResult(text);
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            // Not used
        }
    }
}
