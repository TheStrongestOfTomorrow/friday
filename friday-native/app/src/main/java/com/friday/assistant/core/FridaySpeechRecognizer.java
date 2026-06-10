package com.friday.assistant.core;

import android.content.Context;
import android.content.Intent;
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
     */
    public boolean isAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    /**
     * Start listening with the Shush Protocol.
     * Audio is automatically ducked before listening begins.
     */
    public void startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening, ignoring start request");
            return;
        }

        if (!isAvailable()) {
            if (callback != null) {
                callback.onError("Speech recognition not available on this device", -1);
            }
            return;
        }

        // Shush Protocol: duck audio first
        audioFocusManager.duckAudio();

        // Destroy any existing recognizer
        destroyRecognizer();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new FridayRecognitionListener());

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);

        speechRecognizer.startListening(intent);
        isListening = true;
        Log.d(TAG, "Started listening (Shush Protocol active)");

        if (callback != null) {
            callback.onListeningStart();
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
                    message = "Network timeout";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    message = "Network error";
                    break;
                case SpeechRecognizer.ERROR_AUDIO:
                    message = "Audio recording error";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    message = "Server error";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    message = "Client error";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "No speech detected";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    message = "No match found";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "Recognizer busy";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "Insufficient permissions — please grant microphone access";
                    break;
                default:
                    message = "Unknown error (" + error + ")";
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
