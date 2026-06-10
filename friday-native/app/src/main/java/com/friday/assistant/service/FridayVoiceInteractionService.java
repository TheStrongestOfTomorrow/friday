package com.friday.assistant.service;

import android.service.voice.VoiceInteractionService;
import android.util.Log;

/**
 * Friday — Voice Interaction Service
 *
 * Allows Friday to be set as the default assistant on the device.
 * When enabled, a long-press of the home button activates Friday.
 *
 * This is a REAL VoiceInteractionService — Android's official API
 * for digital assistants.
 */
public class FridayVoiceInteractionService extends VoiceInteractionService {

    private static final String TAG = "Friday/VoiceInteraction";

    @Override
    public void onReady() {
        super.onReady();
        Log.d(TAG, "Voice Interaction Service is ready");
    }

    @Override
    public void onShutdown() {
        super.onShutdown();
        Log.d(TAG, "Voice Interaction Service shut down");
    }
}
