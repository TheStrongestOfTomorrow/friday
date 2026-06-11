package com.friday.assistant.service;

import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
import android.util.Log;

/**
 * Friday — Voice Interaction Session Service
 *
 * REQUIRED for Friday to appear in the default assistant picker.
 * Android will not show an app as a selectable assistant unless
 * it provides both a VoiceInteractionService AND a
 * VoiceInteractionSessionService.
 *
 * This service creates the session that handles the actual
 * interaction when the user activates the assistant.
 */
public class FridaySessionService extends VoiceInteractionSessionService {

    private static final String TAG = "Friday/SessionService";

    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        Log.d(TAG, "Creating new voice interaction session");
        return new FridaySession(this);
    }
}
