package com.friday.assistant.service;

import android.service.voice.VoiceInteractionService;
import android.util.Log;

/**
 * Friday — Voice Interaction Service
 *
 * The bootstrap point that Android uses to recognize Friday as a valid
 * assistant. When the user long-presses the home button or uses the
 * assistant gesture, Android activates this service.
 *
 * CRITICAL: For an app to appear in Android's assistant selection list,
 * it MUST provide:
 *   1. VoiceInteractionService (this class) with BIND_VOICE_INTERACTION
 *   2. VoiceInteractionSessionService declared in manifest
 *   3. voice_interaction.xml with sessionService, recognitionService,
 *      supportsAssist="true", supportsLocalInteraction="true"
 *   4. An activity with ACTION.ASSIST intent filter
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
