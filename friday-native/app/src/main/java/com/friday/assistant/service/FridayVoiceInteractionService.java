package com.friday.assistant.service;

import android.service.voice.VoiceInteractionService;
import android.util.Log;

/**
 * Friday — Voice Interaction Service
 *
 * Allows Friday to be set as the default assistant on the device.
 * When enabled, a long-press of the home button activates Friday.
 *
 * CRITICAL: For an app to appear in Android's assistant selection
 * list, it MUST provide:
 *   1. VoiceInteractionService (this class)
 *   2. VoiceInteractionSessionService (FridaySessionService)
 *   3. Both declared in AndroidManifest with BIND_VOICE_INTERACTION permission
 *
 * Without #2, Android silently ignores the app and it never appears
 * in the default assistant picker.
 */
public class FridayVoiceInteractionService extends VoiceInteractionService {

    private static final String TAG = "Friday/VoiceInteraction";

    @Override
    public void onReady() {
        super.onReady();
        Log.d(TAG, "Voice Interaction Service is ready — Friday can now be set as default assistant");
    }

    @Override
    public void onShutdown() {
        super.onShutdown();
        Log.d(TAG, "Voice Interaction Service shut down");
    }
}
