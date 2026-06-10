package com.friday.core.plugin;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import java.util.ArrayList;

/**
 * FridaySpeech — Speech Recognition Plugin for Friday Assistant
 *
 * Uses Android's native SpeechRecognizer to provide:
 *   - startListening(): Begins speech recognition with streaming partial results
 *   - stopListening(): Stops the current recognition session
 *
 * Streams live transcription to the web layer via Capacitor events:
 *   - "onPartialResult" — fired as the user speaks (live transcription)
 *   - "onFinalResult"  — fired when recognition completes
 *   - "onError"        — fired when recognition fails
 *   - "onListeningStart" — fired when the recognizer begins listening
 *   - "onListeningEnd"   — fired when the recognizer stops listening
 */
@CapacitorPlugin(
    name = "FridaySpeech",
    permissions = {
        @Permission(
            alias = "recordAudio",
            strings = { "android.permission.RECORD_AUDIO" }
        )
    }
)
public class FridaySpeechPlugin extends Plugin {

    private static final String TAG = "FridaySpeech";

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private PluginCall savedStartCall = null;

    // ─── RecognitionListener ────────────────────────────────────

    private final RecognitionListener recognitionListener = new RecognitionListener() {

        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Ready for speech");
            isListening = true;
            JSObject data = new JSObject();
            data.put("status", "ready");
            notifyListeners("onListeningStart", data);
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech detected");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Could be used for real-time volume metering in the UI
            JSObject data = new JSObject();
            data.put("rms", rmsdB);
            notifyListeners("onRmsChanged", data);
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            // Not used for text recognition
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "End of speech detected");
            isListening = false;
            JSObject data = new JSObject();
            data.put("status", "ended");
            notifyListeners("onListeningEnd", data);
        }

        @Override
        public void onError(int error) {
            isListening = false;
            String errorMessage = getErrorString(error);
            Log.e(TAG, "Speech recognition error: " + errorMessage + " (code: " + error + ")");

            JSObject data = new JSObject();
            data.put("code", error);
            data.put("message", errorMessage);
            notifyListeners("onError", data);

            if (savedStartCall != null) {
                savedStartCall.reject("Speech recognition error: " + errorMessage);
                savedStartCall = null;
            }
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String finalText = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";

            Log.d(TAG, "Final result: " + finalText);

            JSObject data = new JSObject();
            data.put("text", finalText);
            data.put("confidence", 1.0);
            notifyListeners("onFinalResult", data);

            if (savedStartCall != null) {
                JSObject ret = new JSObject();
                ret.put("text", finalText);
                ret.put("success", true);
                savedStartCall.resolve(ret);
                savedStartCall = null;
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String partialText = (partial != null && !partial.isEmpty()) ? partial.get(0) : "";

            Log.d(TAG, "Partial result: " + partialText);

            JSObject data = new JSObject();
            data.put("text", partialText);
            data.put("isPartial", true);
            notifyListeners("onPartialResult", data);
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            // Reserved for future use
        }
    };

    // ─── Plugin Lifecycle ───────────────────────────────────────

    @Override
    public void load() {
        super.load();
        if (SpeechRecognizer.isRecognitionAvailable(getContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
            speechRecognizer.setRecognitionListener(recognitionListener);
            Log.d(TAG, "FridaySpeech plugin loaded. SpeechRecognizer initialized.");
        } else {
            Log.e(TAG, "Speech recognition is NOT available on this device");
        }
    }

    @Override
    protected void handleOnDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.handleOnDestroy();
    }

    // ─── Plugin Methods ─────────────────────────────────────────

    /**
     * Start listening for speech.
     *
     * Creates a RecognizerIntent with:
     *   - ACTION_RECOGNIZE_SPEECH
     *   - LANGUAGE_MODEL_FREE_FORM (for natural commands)
     *   - Partial results enabled for live transcription
     *
     * Called from JS as: FridaySpeech.startListening()
     * Optional params: { language: "en-US" }
     */
    @PluginMethod
    public void startListening(PluginCall call) {
        if (speechRecognizer == null) {
            call.reject("SpeechRecognizer not available on this device");
            return;
        }

        if (isListening) {
            call.reject("Already listening");
            return;
        }

        savedStartCall = call;

        String language = call.getString("language", "en-US");

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        // Request shorter silence detection for more responsive results
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);

        try {
            speechRecognizer.startListening(intent);
            isListening = true;
            Log.d(TAG, "Started listening (language: " + language + ")");
            // Note: We don't resolve the call here — it will be resolved
            // in onResults() or rejected in onError()
        } catch (Exception e) {
            isListening = false;
            savedStartCall = null;
            Log.e(TAG, "Failed to start listening", e);
            call.reject("Failed to start listening: " + e.getMessage());
        }
    }

    /**
     * Stop listening for speech.
     *
     * Forces the SpeechRecognizer to stop and produce whatever
     * results it has so far (triggers onResults).
     *
     * Called from JS as: FridaySpeech.stopListening()
     */
    @PluginMethod
    public void stopListening(PluginCall call) {
        if (speechRecognizer == null) {
            call.reject("SpeechRecognizer not available");
            return;
        }

        if (!isListening) {
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("message", "Not currently listening");
            call.resolve(ret);
            return;
        }

        try {
            speechRecognizer.stopListening();
            isListening = false;
            Log.d(TAG, "Stopped listening");

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop listening", e);
            call.reject("Failed to stop listening: " + e.getMessage());
        }
    }

    /**
     * Check if the speech recognizer is currently listening.
     *
     * Called from JS as: FridaySpeech.isListening()
     */
    @PluginMethod
    public void isListening(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("isListening", isListening);
        call.resolve(ret);
    }

    /**
     * Check if speech recognition is available on this device.
     *
     * Called from JS as: FridaySpeech.isAvailable()
     */
    @PluginMethod
    public void isAvailable(PluginCall call) {
        boolean available = SpeechRecognizer.isRecognitionAvailable(getContext());
        JSObject ret = new JSObject();
        ret.put("available", available);
        call.resolve(ret);
    }

    // ─── Helpers ────────────────────────────────────────────────

    private String getErrorString(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech detected";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No recognition match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognizer is busy";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions (RECORD_AUDIO required)";
            default:
                return "Unknown error (" + errorCode + ")";
        }
    }
}
