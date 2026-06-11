package com.friday.assistant.service;

import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.friday.assistant.AssistantActivity;
import com.friday.assistant.MainActivity;
import com.friday.assistant.R;
import com.friday.assistant.core.AudioFocusManager;
import com.friday.assistant.core.FridaySpeechRecognizer;
import com.friday.assistant.core.IntentRouter;
import com.friday.assistant.core.PrefsManager;
import com.friday.assistant.core.TTSManager;

/**
 * Friday — Voice Interaction Session
 *
 * The session that handles the actual assistant interaction.
 * When the user long-presses the home button (or uses the
 * assistant gesture), Android creates this session.
 *
 * This session shows a bottom-sheet UI with live speech recognition,
 * real-time transcription, and spoken responses via TTS.
 * It uses Google's pre-installed speech services for both
 * speech-to-text and text-to-speech.
 */
public class FridaySession extends VoiceInteractionSession {

    private static final String TAG = "Friday/Session";

    private View sessionView;
    private TextView tvStatus;
    private TextView tvTranscription;
    private ImageButton btnMic;
    private ProgressBar progressBar;
    private LinearLayout waveIndicator;

    private PrefsManager prefs;
    private AudioFocusManager audioFocusManager;
    private FridaySpeechRecognizer speechRecognizer;
    private TTSManager ttsManager;
    private IntentRouter intentRouter;

    private boolean isListening = false;

    public FridaySession(android.content.Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PrefsManager(getContext());
        audioFocusManager = new AudioFocusManager(getContext());
        ttsManager = new TTSManager(getContext());
        speechRecognizer = new FridaySpeechRecognizer(getContext(), audioFocusManager);
        intentRouter = new IntentRouter(getContext(), ttsManager, prefs);

        ttsManager.setRate(prefs.getTtsRate());
        ttsManager.setPitch(prefs.getTtsPitch());

        speechRecognizer.setCallback(new FridaySpeechRecognizer.SpeechCallback() {
            @Override
            public void onPartialResult(String text) {
                updateTranscription(text, true);
            }

            @Override
            public void onFinalResult(String text) {
                isListening = false;
                updateMicButton(false);
                if (text != null && !text.isEmpty()) {
                    updateTranscription(text, false);
                    String response = intentRouter.routeCommand(text);
                    updateStatus(response);
                } else {
                    updateStatus("No speech detected. Tap mic to try again.");
                }
            }

            @Override
            public void onError(String message, int errorCode) {
                isListening = false;
                updateMicButton(false);
                updateStatus("Error: " + message);
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Could animate waveform — kept for future use
            }

            @Override
            public void onListeningStart() {
                updateStatus("Listening...");
            }

            @Override
            public void onListeningEnd() {
                // Handled by onFinalResult/onError
            }
        });

        // Inflate the assistant UI layout
        LayoutInflater inflater = LayoutInflater.from(getContext());
        sessionView = inflater.inflate(R.layout.assistant_bottom_sheet, null);
        setContentView(sessionView);

        tvStatus = sessionView.findViewById(R.id.tvAssistantStatus);
        tvTranscription = sessionView.findViewById(R.id.tvAssistantTranscription);
        btnMic = sessionView.findViewById(R.id.btnAssistantMic);
        progressBar = sessionView.findViewById(R.id.assistantProgressBar);
        waveIndicator = sessionView.findViewById(R.id.waveIndicator);

        btnMic.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });

        updateStatus("Hi! I'm Friday. How can I help?");
        Log.d(TAG, "Voice interaction session created with UI");
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        Log.d(TAG, "Voice interaction session shown");

        // Auto-start listening when activated from assistant gesture
        if (speechRecognizer.isAvailable()) {
            startListening();
        } else {
            updateStatus("Speech recognition unavailable. Install the Google app.");
        }
    }

    @Override
    public void onHide() {
        super.onHide();
        Log.d(TAG, "Voice interaction session hidden");
        stopListening();
    }

    private void startListening() {
        if (isListening) return;

        isListening = true;
        updateMicButton(true);
        progressBar.setVisibility(View.VISIBLE);
        waveIndicator.setVisibility(View.VISIBLE);
        tvTranscription.setText("");
        tvTranscription.setVisibility(View.VISIBLE);
        updateStatus("Listening...");
        speechRecognizer.startListening();
    }

    private void stopListening() {
        if (!isListening) return;

        isListening = false;
        updateMicButton(false);
        progressBar.setVisibility(View.GONE);
        waveIndicator.setVisibility(View.GONE);
        speechRecognizer.stopListening();
    }

    private void updateStatus(final String text) {
        if (sessionView != null) {
            sessionView.post(() -> {
                if (tvStatus != null) tvStatus.setText(text);
            });
        }
    }

    private void updateTranscription(final String text, final boolean isPartial) {
        if (sessionView != null) {
            sessionView.post(() -> {
                if (tvTranscription != null && text != null) {
                    tvTranscription.setText(isPartial ? text + "..." : text);
                }
            });
        }
    }

    private void updateMicButton(final boolean listening) {
        if (sessionView != null) {
            sessionView.post(() -> {
                if (btnMic != null) {
                    btnMic.setAlpha(listening ? 1.0f : 0.7f);
                    btnMic.setBackgroundResource(listening ?
                            R.drawable.bg_listen_button_listening :
                            R.drawable.bg_listen_button);
                }
                if (progressBar != null) {
                    progressBar.setVisibility(listening ? View.VISIBLE : View.GONE);
                }
                if (waveIndicator != null) {
                    waveIndicator.setVisibility(listening ? View.VISIBLE : View.GONE);
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (ttsManager != null) ttsManager.destroy();
    }
}
