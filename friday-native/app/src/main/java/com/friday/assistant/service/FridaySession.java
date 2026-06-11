package com.friday.assistant.service;

import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

import com.friday.assistant.MainActivity;

/**
 * Friday — Voice Interaction Session
 *
 * The session that handles the actual assistant interaction.
 * When the user long-presses the home button (or uses the
 * assistant gesture), Android creates this session.
 *
 * This session launches MainActivity which handles speech
 * recognition and command processing.
 */
public class FridaySession extends VoiceInteractionSession {

    private static final String TAG = "Friday/Session";

    public FridaySession(android.content.Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        Log.d(TAG, "Voice interaction session shown — launching Friday");

        // Launch the main activity to handle the voice command
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("from_assistant", true);
        getContext().startActivity(intent);

        // Hide this session immediately since MainActivity handles the UI
        hide();
    }

    @Override
    public void onHide() {
        super.onHide();
        Log.d(TAG, "Voice interaction session hidden");
    }
}
