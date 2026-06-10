package com.friday.core.plugin

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

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
class FridayCorePlugin : Plugin() {

    companion object {
        private const val TAG = "FridayCore"
    }

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var currentFocusState: String = "none"

    // Callback for audio focus changes
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                currentFocusState = "gained"
                Log.d(TAG, "Audio focus gained")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                currentFocusState = "lost"
                Log.d(TAG, "Audio focus lost")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                currentFocusState = "lost_transient"
                Log.d(TAG, "Audio focus lost transient")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                currentFocusState = "lost_transient_can_duck"
                Log.d(TAG, "Audio focus lost transient (can duck)")
            }
        }
    }

    override fun load() {
        super.load()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d(TAG, "FridayCore plugin loaded. AudioManager initialized.")
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
    fun duckAudio(call: PluginCall) {
        val am = audioManager
        if (am == null) {
            Log.e(TAG, "AudioManager not initialized")
            call.reject("AudioManager not available")
            return
        }

        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8+ (API 26+): Use AudioFocusRequest
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .build()

                am.requestAudioFocus(focusRequest!!)
            } else {
                // Legacy (pre-Android 8)
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    currentFocusState = "gained"
                    Log.d(TAG, "Audio focus granted — background media ducked/paused")
                    val ret = JSObject()
                    ret.put("success", true)
                    call.resolve(ret)
                }
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    currentFocusState = "lost"
                    Log.w(TAG, "Audio focus request failed")
                    val ret = JSObject()
                    ret.put("success", false)
                    call.resolve(ret)
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    currentFocusState = "pending"
                    Log.d(TAG, "Audio focus request delayed")
                    val ret = JSObject()
                    ret.put("success", true) // Treat delayed as success — it will be granted
                    call.resolve(ret)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
            call.reject("Failed to request audio focus: ${e.message}")
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
    fun resumeAudio(call: PluginCall) {
        val am = audioManager
        if (am == null) {
            Log.e(TAG, "AudioManager not initialized")
            call.reject("AudioManager not available")
            return
        }

        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                // Android 8+: Abandon using the stored AudioFocusRequest
                am.abandonAudioFocusRequest(focusRequest!!)
            } else {
                // Legacy
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusChangeListener)
            }

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                currentFocusState = "abandoned"
                Log.d(TAG, "Audio focus abandoned — background media can resume")
                val ret = JSObject()
                ret.put("success", true)
                call.resolve(ret)
            } else {
                Log.w(TAG, "Audio focus abandon returned: $result")
                val ret = JSObject()
                ret.put("success", false)
                call.resolve(ret)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus", e)
            call.reject("Failed to abandon audio focus: ${e.message}")
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
    fun getAudioFocusState(call: PluginCall) {
        val ret = JSObject()
        ret.put("state", currentFocusState)
        call.resolve(ret)
    }
}
