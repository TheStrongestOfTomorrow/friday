package com.friday.core.plugin;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * FridayCore — Main Capacitor Plugin for Friday Assistant
 *
 * Exposes native Android functionality to the WebView:
 *   - Audio focus management (duck/pause background media)
 *   - (Future) Accessibility service control
 *   - (Future) Shizuku shell execution
 *   - (Future) VoiceInteraction service triggers
 */
@CapacitorPlugin(name = "FridayCore")
public class FridayCorePlugin extends Plugin {

    private static final String TAG = "FridayCore";

    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private String currentFocusState = "none";

    // Callback for audio focus changes
    private final AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                currentFocusState = "gained";
                Log.d(TAG, "Audio focus gained");
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                currentFocusState = "lost";
                Log.d(TAG, "Audio focus lost");
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                currentFocusState = "lost_transient";
                Log.d(TAG, "Audio focus lost transient");
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                currentFocusState = "lost_transient_can_duck";
                Log.d(TAG, "Audio focus lost transient (can duck)");
                break;
        }
    };

    @Override
    public void load() {
        super.load();
        audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        Log.d(TAG, "FridayCore plugin loaded. AudioManager initialized.");
    }

    /**
     * Request AUDIOFOCUS_GAIN_TRANSIENT to pause/duck background media.
     *
     * This is called when the wake word is detected — it causes other
     * audio apps (Spotify, YouTube, etc.) to either pause or lower
     * their volume temporarily.
     *
     * Called from JS as: FridayCore.duckAudio()
     */
    @PluginMethod
    public void duckAudio(PluginCall call) {
        if (audioManager == null) {
            Log.e(TAG, "AudioManager not initialized");
            call.reject("AudioManager not available");
            return;
        }

        try {
            int result;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8+ (API 26+): Use AudioFocusRequest
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();

                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(audioAttributes)
                        .setOnAudioFocusChangeListener(focusChangeListener)
                        .setAcceptsDelayedFocusGain(true)
                        .setWillPauseWhenDucked(true)
                        .build();

                result = audioManager.requestAudioFocus(focusRequest);
            } else {
                // Legacy (pre-Android 8)
                result = audioManager.requestAudioFocus(
                        focusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                );
            }

            JSObject ret = new JSObject();

            switch (result) {
                case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                    currentFocusState = "gained";
                    Log.d(TAG, "Audio focus granted — background media ducked/paused");
                    ret.put("success", true);
                    call.resolve(ret);
                    break;

                case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                    currentFocusState = "lost";
                    Log.w(TAG, "Audio focus request failed");
                    ret.put("success", false);
                    call.resolve(ret);
                    break;

                case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                    currentFocusState = "pending";
                    Log.d(TAG, "Audio focus request delayed");
                    ret.put("success", true); // Treat delayed as success
                    call.resolve(ret);
                    break;

                default:
                    ret.put("success", false);
                    call.resolve(ret);
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error requesting audio focus", e);
            call.reject("Failed to request audio focus: " + e.getMessage());
        }
    }

    /**
     * Abandon audio focus, allowing background media to resume.
     *
     * Called when Friday is done processing (e.g., macro executed,
     * user cancelled, or timeout).
     *
     * Called from JS as: FridayCore.resumeAudio()
     */
    @PluginMethod
    public void resumeAudio(PluginCall call) {
        if (audioManager == null) {
            Log.e(TAG, "AudioManager not initialized");
            call.reject("AudioManager not available");
            return;
        }

        try {
            int result;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                // Android 8+: Abandon using the stored AudioFocusRequest
                result = audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                // Legacy
                result = audioManager.abandonAudioFocus(focusChangeListener);
            }

            JSObject ret = new JSObject();

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                currentFocusState = "abandoned";
                Log.d(TAG, "Audio focus abandoned — background media can resume");
                ret.put("success", true);
            } else {
                Log.w(TAG, "Audio focus abandon returned: " + result);
                ret.put("success", false);
            }

            call.resolve(ret);

        } catch (Exception e) {
            Log.e(TAG, "Error abandoning audio focus", e);
            call.reject("Failed to abandon audio focus: " + e.getMessage());
        }
    }

    /**
     * Get the current audio focus state.
     *
     * Returns one of: "gained", "lost", "lost_transient",
     * "lost_transient_can_duck", "pending", "abandoned", "none"
     *
     * Called from JS as: FridayCore.getAudioFocusState()
     */
    @PluginMethod
    public void getAudioFocusState(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("state", currentFocusState);
        call.resolve(ret);
    }
}
