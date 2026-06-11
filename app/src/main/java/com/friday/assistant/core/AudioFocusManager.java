package com.friday.assistant.core;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

/**
 * Friday — Audio Focus Manager
 *
 * Manages audio focus for ducking and resuming background media.
 * Real Android AudioManager calls — no mocks.
 */
public class AudioFocusManager {

    private static final String TAG = "Friday/AudioFocus";

    public enum FocusState {
        NONE,       // No focus held
        DUCKED,     // We have transient focus (ducking others)
        FULL        // We have full focus
    }

    private final AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private FocusState currentState = FocusState.NONE;
    private final AudioManager.OnAudioFocusChangeListener focusChangeListener;

    public AudioFocusManager(Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        focusChangeListener = (focusChange) -> {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    currentState = FocusState.FULL;
                    Log.d(TAG, "Audio focus gained (full)");
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                    currentState = FocusState.DUCKED;
                    Log.d(TAG, "Audio focus gained (transient)");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    currentState = FocusState.NONE;
                    Log.d(TAG, "Audio focus lost");
                    break;
            }
        };

        // Build the focus request for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .setWillPauseWhenDucked(false)
                    .build();
        }
    }

    /**
     * Duck background audio — request transient focus.
     * This lowers other apps' volume while Friday is listening.
     */
    public boolean duckAudio() {
        if (audioManager == null) return false;

        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            );
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            currentState = FocusState.DUCKED;
            Log.d(TAG, "Audio ducked successfully");
            return true;
        } else {
            Log.w(TAG, "Audio focus request denied: " + result);
            return false;
        }
    }

    /**
     * Resume background audio — abandon focus.
     * Other apps return to normal volume.
     */
    public boolean resumeAudio() {
        if (audioManager == null) return false;

        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result = audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            result = audioManager.abandonAudioFocus(focusChangeListener);
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            currentState = FocusState.NONE;
            Log.d(TAG, "Audio resumed successfully");
            return true;
        } else {
            Log.w(TAG, "Audio abandon focus failed: " + result);
            return false;
        }
    }

    public FocusState getCurrentState() {
        return currentState;
    }

    public boolean isDucking() {
        return currentState == FocusState.DUCKED;
    }
}
