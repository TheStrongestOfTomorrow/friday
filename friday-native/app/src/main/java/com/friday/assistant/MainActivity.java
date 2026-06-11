package com.friday.assistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.friday.assistant.core.AudioFocusManager;
import com.friday.assistant.core.FridaySpeechRecognizer;
import com.friday.assistant.core.IntentRouter;
import com.friday.assistant.core.PrefsManager;
import com.friday.assistant.core.PermissionHelper;
import com.friday.assistant.core.TTSManager;
import com.friday.assistant.core.WakeWordEngine;
import com.friday.assistant.service.FridayForegroundService;
import com.friday.assistant.service.PeekOverlayService;

/**
 * Friday — Main Activity
 *
 * The primary screen of the app. Shows status, listen button,
 * transcription output, and quick actions for audio ducking.
 *
 * Every button works. No mocks. No placeholders.
 * Uses Google's pre-installed speech services.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Friday/Main";

    // Core
    private PrefsManager prefs;
    private AudioFocusManager audioFocusManager;
    private FridaySpeechRecognizer speechRecognizer;
    private TTSManager ttsManager;
    private IntentRouter intentRouter;

    // UI
    private View statusDot;
    private TextView statusLabel;
    private TextView statusDesc;
    private View transcriptionCard;
    private TextView transcriptionText;
    private ImageButton btnListen;
    private TextView listenLabel;
    private ImageButton btnSettings;

    // State
    private boolean isListening = false;
    private boolean activatedFromAssistant = false;

    // Permission launcher
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean allGranted = true;
                        for (Boolean granted : result.values()) {
                            if (!granted) allGranted = false;
                        }
                        if (allGranted) {
                            setStatus("active", "Ready", "All permissions granted. Tap the mic to begin.");
                            if (activatedFromAssistant) {
                                startListening();
                            }
                        } else {
                            setStatus("idle", "Permissions Needed", "Some permissions were denied. Go to Settings to grant them.");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefsManager(this);

        // Check onboarding — redirect if not completed
        if (!prefs.isOnboardingCompleted()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        // Check if launched from assistant
        Intent launchIntent = getIntent();
        if (launchIntent != null && launchIntent.getBooleanExtra("from_assistant", false)) {
            activatedFromAssistant = true;
            Log.d(TAG, "Launched from assistant gesture — will auto-listen");
        }

        // Initialize core systems
        audioFocusManager = new AudioFocusManager(this);
        ttsManager = new TTSManager(this);
        speechRecognizer = new FridaySpeechRecognizer(this, audioFocusManager);
        intentRouter = new IntentRouter(this, ttsManager, prefs);
        speechRecognizer.setCallback(new SpeechCallback());

        // Bind views
        statusDot = findViewById(R.id.statusDot);
        statusLabel = findViewById(R.id.statusLabel);
        statusDesc = findViewById(R.id.statusDesc);
        transcriptionCard = findViewById(R.id.transcriptionCard);
        transcriptionText = findViewById(R.id.transcriptionText);
        btnListen = findViewById(R.id.btnListen);
        listenLabel = findViewById(R.id.listenLabel);
        btnSettings = findViewById(R.id.btnSettings);

        // Setup listeners
        btnListen.setOnClickListener(v -> toggleListening());
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Quick actions
        findViewById(R.id.btnTestDuck).setOnClickListener(v -> {
            boolean success = audioFocusManager.duckAudio();
            Toast.makeText(this, success ? "Audio ducked" : "Duck failed", Toast.LENGTH_SHORT).show();
            setStatus(success ? "active" : "idle",
                    success ? "Audio Ducked" : "Duck Failed",
                    success ? "Background media volume lowered" : "Could not duck audio");
        });

        findViewById(R.id.btnTestResume).setOnClickListener(v -> {
            boolean success = audioFocusManager.resumeAudio();
            Toast.makeText(this, success ? "Audio resumed" : "Resume failed", Toast.LENGTH_SHORT).show();
            setStatus("idle", "Idle", "Audio resumed to normal volume");
        });

        // Apply saved TTS settings
        ttsManager.setRate(prefs.getTtsRate());
        ttsManager.setPitch(prefs.getTtsPitch());

        // Check permissions on launch
        checkPermissionsOnLaunch();

        // Start foreground service if not running
        startForegroundService();

        // Start/stop Peek overlay based on preference
        updatePeekOverlay();

        // Check speech recognizer availability
        if (!speechRecognizer.isAvailable()) {
            setStatus("error", "Speech Not Available", "Install the Google app for speech recognition.");
        } else {
            setStatus("idle", "Idle", activatedFromAssistant ? "Activating..." : "Tap the mic or say your wake word.");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("from_assistant", false)) {
            activatedFromAssistant = true;
            Log.d(TAG, "Re-launched from assistant — will auto-listen");
            if (PermissionHelper.isMicGranted(this)) {
                startListening();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!PermissionHelper.allGranted(this)) {
            setStatus("idle", "Permissions Needed", "Some permissions are missing. Go to Settings to grant them.");
        }

        if (speechRecognizer != null && !speechRecognizer.isAvailable()) {
            setStatus("error", "Speech Not Available", "Install the Google app for speech recognition.");
        }

        if (activatedFromAssistant && PermissionHelper.isMicGranted(this)) {
            activatedFromAssistant = false;
            startListening();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (ttsManager != null) {
            ttsManager.destroy();
        }
    }

    // ─── Listening ─────────────────────────────────────────────

    private void toggleListening() {
        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }

    private void startListening() {
        if (!PermissionHelper.isGranted(this, Manifest.permission.RECORD_AUDIO)) {
            permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            return;
        }

        if (speechRecognizer != null && !speechRecognizer.isAvailable()) {
            showSpeechNotAvailableDialog();
            return;
        }

        isListening = true;
        btnListen.setBackgroundResource(R.drawable.bg_listen_button_listening);
        btnListen.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse));
        listenLabel.setText(R.string.btn_listening);
        transcriptionCard.setVisibility(View.VISIBLE);
        transcriptionText.setText("");
        transcriptionText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

        speechRecognizer.startListening();

        setStatus("listening", "Listening", "Speak your command...");
        showPeekOverlay("Listening...", "listening");
    }

    private void stopListening() {
        isListening = false;
        btnListen.setBackgroundResource(R.drawable.bg_listen_button);
        btnListen.clearAnimation();
        listenLabel.setText(R.string.btn_listen);
        speechRecognizer.stopListening();
        showPeekOverlay("Idle", "idle");
    }

    // ─── Speech Not Available Dialog ──────────────────────────

    private void showSpeechNotAvailableDialog() {
        boolean googleInstalled = FridaySpeechRecognizer.isGoogleAppInstalled(this);

        String message;
        if (!googleInstalled) {
            message = "Speech recognition requires the Google app, which is not installed on your device. " +
                    "Would you like to install it from the Play Store?";
        } else {
            message = "Speech recognition is currently unavailable. This may be because:\n\n" +
                    "1. The Google app needs to be updated\n" +
                    "2. Your device doesn't support speech recognition\n" +
                    "3. The speech service is temporarily down\n\n" +
                    "Try restarting your device or updating the Google app.";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Speech Recognition Unavailable")
                .setMessage(message)
                .setPositiveButton("OK", null);

        if (!googleInstalled) {
            builder.setNegativeButton("Install Google App", (dialog, which) -> {
                try {
                    startActivity(FridaySpeechRecognizer.getGoogleAppInstallIntent(this));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox")));
                    } catch (Exception ignored) {}
                }
            });
        }

        builder.show();
        setStatus("error", "Speech Unavailable", "Install or update the Google app");
    }

    // ─── Status Management ─────────────────────────────────────

    private void setStatus(String state, String label, String desc) {
        statusLabel.setText(label);
        statusDesc.setText(desc);

        int dotColor;
        switch (state) {
            case "listening":
                dotColor = ContextCompat.getColor(this, R.color.status_listening);
                break;
            case "active":
                dotColor = ContextCompat.getColor(this, R.color.status_active);
                break;
            case "error":
                dotColor = ContextCompat.getColor(this, R.color.status_error);
                break;
            default:
                dotColor = ContextCompat.getColor(this, R.color.status_idle);
                break;
        }
        statusDot.setBackgroundColor(dotColor);
    }

    // ─── Permissions ───────────────────────────────────────────

    private void checkPermissionsOnLaunch() {
        if (!PermissionHelper.allGranted(this)) {
            String[] ungranted = PermissionHelper.getUngrantedPermissions(this);
            if (ungranted.length > 0) {
                permissionLauncher.launch(ungranted);
            }
        }
    }

    // ─── Foreground Service ────────────────────────────────────

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.isGranted(this, Manifest.permission.POST_NOTIFICATIONS)) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted, starting service anyway");
            }
        }

        Intent serviceIntent = new Intent(this, FridayForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // ─── Peek Overlay ──────────────────────────────────────────

    private void updatePeekOverlay() {
        if (prefs.isPeekGuiEnabled() && !prefs.isStealthMode()) {
            Intent intent = new Intent(this, PeekOverlayService.class);
            intent.setAction("SHOW");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } else {
            Intent intent = new Intent(this, PeekOverlayService.class);
            intent.setAction("HIDE");
            startService(intent);
        }
    }

    private void showPeekOverlay(String text, String state) {
        if (!prefs.isPeekGuiEnabled() || prefs.isStealthMode()) return;

        Intent intent = new Intent(this, PeekOverlayService.class);
        intent.setAction("UPDATE");
        intent.putExtra("text", text);
        intent.putExtra("state", state);
        startService(intent);
    }

    // ─── Speech Callback ───────────────────────────────────────

    private class SpeechCallback implements FridaySpeechRecognizer.SpeechCallback {

        @Override
        public void onPartialResult(String text) {
            runOnUiThread(() -> {
                if (text != null && !text.isEmpty()) {
                    transcriptionText.setText(text);
                    transcriptionText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.text_secondary));
                    showPeekOverlay("Heard: " + text, "listening");
                }
            });
        }

        @Override
        public void onFinalResult(String text) {
            runOnUiThread(() -> {
                isListening = false;
                btnListen.setBackgroundResource(R.drawable.bg_listen_button);
                btnListen.clearAnimation();
                listenLabel.setText(R.string.btn_listen);

                if (text != null && !text.isEmpty()) {
                    transcriptionText.setText(text);
                    transcriptionText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.text_primary));

                    // Use IntentRouter to process the command
                    String response = intentRouter.routeCommand(text);
                    setStatus("active", "Recognized", "\"" + text + "\"");
                    showPeekOverlay(response, "active");
                } else {
                    setStatus("idle", "Idle", "No speech detected. Try again.");
                    transcriptionText.setText("No speech detected");
                    transcriptionText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.text_muted));
                    showPeekOverlay("No speech detected", "idle");
                }
            });
        }

        @Override
        public void onError(String message, int errorCode) {
            runOnUiThread(() -> {
                isListening = false;
                btnListen.setBackgroundResource(R.drawable.bg_listen_button);
                btnListen.clearAnimation();
                listenLabel.setText(R.string.btn_listen);

                if (errorCode == FridaySpeechRecognizer.ERROR_RECOGNIZER_UNAVAILABLE) {
                    showSpeechNotAvailableDialog();
                    setStatus("error", "Speech Unavailable", message);
                } else {
                    setStatus("error", "Error", message);
                }

                transcriptionText.setText("Error: " + message);
                transcriptionText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.danger_red));
                showPeekOverlay("Error: " + message, "error");

                if (errorCode == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    checkPermissionsOnLaunch();
                }
            });
        }

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onListeningStart() {}

        @Override
        public void onListeningEnd() {}
    }
}
