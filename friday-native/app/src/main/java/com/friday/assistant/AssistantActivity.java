package com.friday.assistant;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.friday.assistant.core.AudioFocusManager;
import com.friday.assistant.core.FridaySpeechRecognizer;
import com.friday.assistant.core.IntentRouter;
import com.friday.assistant.core.PrefsManager;
import com.friday.assistant.core.PermissionHelper;
import com.friday.assistant.core.TTSManager;

/**
 * Friday — Assistant Activity
 *
 * Handles the ACTION.ASSIST intent — this is what gets triggered
 * when the user long-presses the home button or uses the assistant
 * gesture. It shows a full-screen assistant UI with live speech
 * recognition, real-time transcription, and spoken responses.
 *
 * This uses Google's pre-installed speech services for both
 * speech-to-text (SpeechRecognizer) and text-to-speech (TextToSpeech).
 */
public class AssistantActivity extends AppCompatActivity {

    private static final String TAG = "Friday/Assistant";

    private PrefsManager prefs;
    private AudioFocusManager audioFocusManager;
    private FridaySpeechRecognizer speechRecognizer;
    private TTSManager ttsManager;
    private IntentRouter intentRouter;

    // UI
    private View rootView;
    private TextView tvStatus;
    private TextView tvTranscription;
    private ImageButton btnMic;
    private ProgressBar progressBar;
    private LinearLayout waveIndicator;
    private View btnClose;

    private boolean isListening = false;

    // Permission launcher
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean allGranted = true;
                        for (Boolean granted : result.values()) {
                            if (!granted) allGranted = false;
                        }
                        if (allGranted) {
                            startListening();
                        } else {
                            tvStatus.setText("Microphone permission is required.");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant);

        prefs = new PrefsManager(this);
        audioFocusManager = new AudioFocusManager(this);
        ttsManager = new TTSManager(this);
        speechRecognizer = new FridaySpeechRecognizer(this, audioFocusManager);
        intentRouter = new IntentRouter(this, ttsManager, prefs);

        ttsManager.setRate(prefs.getTtsRate());
        ttsManager.setPitch(prefs.getTtsPitch());

        // Bind views
        rootView = findViewById(R.id.assistantRoot);
        tvStatus = findViewById(R.id.tvAssistantStatus);
        tvTranscription = findViewById(R.id.tvAssistantTranscription);
        btnMic = findViewById(R.id.btnAssistantMic);
        progressBar = findViewById(R.id.assistantProgressBar);
        waveIndicator = findViewById(R.id.waveIndicator);
        btnClose = findViewById(R.id.btnClose);

        btnMic.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });

        btnClose.setOnClickListener(v -> {
            stopListening();
            finish();
        });

        // Set up speech callback
        speechRecognizer.setCallback(new FridaySpeechRecognizer.SpeechCallback() {
            @Override
            public void onPartialResult(String text) {
                runOnUiThread(() -> {
                    if (text != null && !text.isEmpty()) {
                        tvTranscription.setText(text + "...");
                        tvTranscription.setTextColor(ContextCompat.getColor(AssistantActivity.this, R.color.text_secondary));
                    }
                });
            }

            @Override
            public void onFinalResult(String text) {
                runOnUiThread(() -> {
                    isListening = false;
                    updateMicUI(false);

                    if (text != null && !text.isEmpty()) {
                        tvTranscription.setText(text);
                        tvTranscription.setTextColor(ContextCompat.getColor(AssistantActivity.this, R.color.text_primary));
                        String response = intentRouter.routeCommand(text);
                        tvStatus.setText(response);
                    } else {
                        tvStatus.setText("No speech detected. Tap the mic to try again.");
                    }
                });
            }

            @Override
            public void onError(String message, int errorCode) {
                runOnUiThread(() -> {
                    isListening = false;
                    updateMicUI(false);

                    if (errorCode == FridaySpeechRecognizer.ERROR_RECOGNIZER_UNAVAILABLE) {
                        tvStatus.setText("Speech recognition unavailable. Install the Google app.");
                    } else {
                        tvStatus.setText("Error: " + message);
                    }
                });
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onListeningStart() {
                runOnUiThread(() -> tvStatus.setText("Listening..."));
            }

            @Override
            public void onListeningEnd() {}
        });

        tvStatus.setText("Hi! I'm Friday. How can I help?");

        // Auto-start listening after a short delay
        rootView.postDelayed(() -> {
            if (PermissionHelper.isMicGranted(this)) {
                startListening();
            } else {
                permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            }
        }, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (ttsManager != null) ttsManager.destroy();
    }

    private void startListening() {
        if (isListening) return;

        if (!PermissionHelper.isMicGranted(this)) {
            permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            return;
        }

        if (!speechRecognizer.isAvailable()) {
            tvStatus.setText("Speech recognition not available. Install the Google app.");
            return;
        }

        isListening = true;
        updateMicUI(true);
        tvTranscription.setText("");
        tvTranscription.setVisibility(View.VISIBLE);
        tvStatus.setText("Listening...");
        speechRecognizer.startListening();
    }

    private void stopListening() {
        if (!isListening) return;
        isListening = false;
        updateMicUI(false);
        speechRecognizer.stopListening();
    }

    private void updateMicUI(boolean listening) {
        btnMic.setBackgroundResource(listening ?
                R.drawable.bg_listen_button_listening : R.drawable.bg_listen_button);
        if (listening) {
            btnMic.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse));
        } else {
            btnMic.clearAnimation();
        }
        progressBar.setVisibility(listening ? View.VISIBLE : View.GONE);
        waveIndicator.setVisibility(listening ? View.VISIBLE : View.GONE);
    }
}
