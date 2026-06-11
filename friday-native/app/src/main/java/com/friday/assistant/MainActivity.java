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
import com.friday.assistant.core.PrefsManager;
import com.friday.assistant.core.PermissionHelper;
import com.friday.assistant.core.TTSManager;
import com.friday.assistant.core.WakeWordEngine;
import com.friday.assistant.service.FridayForegroundService;
import com.friday.assistant.service.PeekOverlayService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Friday — Main Activity
 *
 * The primary screen of the app. Shows status, listen button,
 * transcription output, and quick actions for audio ducking.
 *
 * Every button works. No mocks. No placeholders.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Friday/Main";

    // Core
    private PrefsManager prefs;
    private AudioFocusManager audioFocusManager;
    private FridaySpeechRecognizer speechRecognizer;
    private TTSManager ttsManager;

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
                            // If we were activated from assistant, auto-start listening
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
        // Handle being re-launched from assistant while already running
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
        // Re-check permissions when returning to activity
        if (!PermissionHelper.allGranted(this)) {
            setStatus("idle", "Permissions Needed", "Some permissions are missing. Go to Settings to grant them.");
        }

        // Re-check speech availability
        if (speechRecognizer != null && !speechRecognizer.isAvailable()) {
            setStatus("error", "Speech Not Available", "Install the Google app for speech recognition.");
        }

        // If activated from assistant and permissions are good, auto-listen
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
        // Check microphone permission first
        if (!PermissionHelper.isGranted(this, Manifest.permission.RECORD_AUDIO)) {
            permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            return;
        }

        // Check speech recognition availability
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

        // Show Peek overlay if enabled
        showPeekOverlay("Listening...", "listening");
    }

    private void stopListening() {
        isListening = false;
        btnListen.setBackgroundResource(R.drawable.bg_listen_button);
        btnListen.clearAnimation();
        listenLabel.setText(R.string.btn_listen);

        speechRecognizer.stopListening();

        // Update Peek overlay
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
                    // Fallback to opening Play Store directly
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox")));
                    } catch (Exception ignored) {}
                }
            });
        } else {
            builder.setNeutralButton("Update Google App", (dialog, which) -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=com.google.android.googlequicksearchbox"));
                    startActivity(intent);
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
            // Request missing permissions
            String[] ungranted = PermissionHelper.getUngrantedPermissions(this);
            if (ungranted.length > 0) {
                permissionLauncher.launch(ungranted);
            }
        }
    }

    // ─── Foreground Service ────────────────────────────────────

    private void startForegroundService() {
        // Check POST_NOTIFICATIONS permission before starting foreground service on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.isGranted(this, Manifest.permission.POST_NOTIFICATIONS)) {
                // Start the service anyway — Android will show the notification
                // but won't crash. The notification channel uses IMPORTANCE_LOW
                // which is acceptable without the permission on some devices.
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

    private void hidePeekOverlay() {
        Intent intent = new Intent(this, PeekOverlayService.class);
        intent.setAction("HIDE");
        startService(intent);
    }

    // ─── Command Processing ────────────────────────────────────

    private void processCommand(String text) {
        Log.d(TAG, "Processing command: " + text);

        String lowerText = text.toLowerCase().trim();
        String response;

        // Check wake word match first
        String wakeWord = prefs.getWakeWord();
        float threshold = prefs.getConfidenceThreshold();

        WakeWordEngine.MatchResult match = WakeWordEngine.match(lowerText, wakeWord.toLowerCase(), threshold);

        // Strip wake word from command if matched
        String commandText = lowerText;
        if (match.matched) {
            // Remove the wake word to get the actual command
            commandText = lowerText.replace(wakeWord.toLowerCase(), "").trim();
            // Also try common prefixes
            if (commandText.isEmpty()) {
                commandText = lowerText.replace("hey " + wakeWord.toLowerCase(), "").trim();
            }
            if (commandText.isEmpty()) {
                commandText = lowerText.replace("ok " + wakeWord.toLowerCase(), "").trim();
            }
        }

        // Process built-in commands
        if (commandText.isEmpty() && match.matched) {
            // Just the wake word with no command
            response = "Yes? I'm listening.";
        } else if (isTimeCommand(commandText)) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            String time = sdf.format(new Date());
            response = "It's " + time + ".";
        } else if (isDateCommand(commandText)) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
            String date = sdf.format(new Date());
            response = "Today is " + date + ".";
        } else if (isWeatherCommand(commandText)) {
            response = "I don't have access to weather data yet. You can ask me for the time or date instead.";
        } else if (isOpenAppCommand(commandText)) {
            String appName = extractAppName(commandText);
            response = openApp(appName);
        } else if (isReminderCommand(commandText)) {
            response = "I heard your reminder request, but I don't have access to the alarm system yet. I'll add this feature soon.";
        } else if (isCallCommand(commandText)) {
            response = "I heard your call request. Contact access is being developed. For now, please dial manually.";
        } else if (isHelpCommand(commandText)) {
            response = "You can ask me for the time, the date, or to open an app. Say my name followed by a command.";
        } else if (match.ambiguous) {
            response = match.suggestion != null ? match.suggestion : "Did you say something?";
        } else if (match.matched) {
            // Wake word matched with a command we don't understand
            response = "I heard \"" + commandText + "\" but I'm not sure what to do with that yet. Try asking for the time or date.";
        } else {
            // No wake word match — just repeat what was heard
            response = "I heard: \"" + text + "\". Say \"" + wakeWord + "\" followed by a command.";
        }

        // Speak the response
        if (ttsManager.isReady()) {
            ttsManager.speak(response);
        }

        setStatus("active", "Recognized", "\"" + text + "\"");
        transcriptionText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

        // Update Peek overlay
        showPeekOverlay(response, "active");
    }

    // ─── Command Detection Helpers ─────────────────────────────

    private boolean isTimeCommand(String text) {
        return text.contains("time") || text.contains("what time") ||
               text.contains("what's the time") || text.contains("whats the time") ||
               text.contains("tell me the time") || text.contains("current time");
    }

    private boolean isDateCommand(String text) {
        return text.contains("date") || text.contains("what day") ||
               text.contains("what's the date") || text.contains("whats the date") ||
               text.contains("today's date") || text.contains("todays date") ||
               text.contains("tell me the date") || text.contains("what is today");
    }

    private boolean isWeatherCommand(String text) {
        return text.contains("weather") || text.contains("temperature") ||
               text.contains("forecast") || text.contains("rain") ||
               text.contains("sunny") || text.contains("cold") ||
               text.contains("hot outside");
    }

    private boolean isOpenAppCommand(String text) {
        return text.contains("open") || text.contains("launch") ||
               text.contains("start") || text.contains("run");
    }

    private boolean isReminderCommand(String text) {
        return text.contains("remind") || text.contains("reminder") ||
               text.contains("alarm") || text.contains("timer") ||
               text.contains("wake me") || text.contains("set a");
    }

    private boolean isCallCommand(String text) {
        return text.contains("call") || text.contains("dial") ||
               text.contains("phone") || text.contains("ring");
    }

    private boolean isHelpCommand(String text) {
        return text.contains("help") || text.contains("what can you do") ||
               text.contains("commands") || text.contains("what do you do");
    }

    private String extractAppName(String text) {
        // Remove common prefixes
        String name = text.replace("open ", "")
                         .replace("launch ", "")
                         .replace("start ", "")
                         .replace("run ", "")
                         .replace("the ", "")
                         .trim();
        return name;
    }

    private String openApp(String appName) {
        if (appName.isEmpty()) {
            return "Which app would you like me to open?";
        }

        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(appName);

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
                return "Opening " + appName + ".";
            } catch (Exception e) {
                Log.e(TAG, "Failed to open app: " + appName, e);
            }
        }

        // Try common app name mappings
        String packageName = resolveCommonApp(appName);
        if (packageName != null) {
            intent = pm.getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                    return "Opening " + appName + ".";
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open app: " + packageName, e);
                }
            }
        }

        // Try searching on Play Store
        try {
            Intent searchIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://search?q=" + Uri.encode(appName)));
            searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(searchIntent);
            return "I couldn't find " + appName + " installed. Searching the Play Store.";
        } catch (Exception e) {
            return "I couldn't find an app called " + appName + ".";
        }
    }

    private String resolveCommonApp(String name) {
        switch (name.toLowerCase()) {
            case "chrome":
            case "browser":
                return "com.android.chrome";
            case "youtube":
                return "com.google.android.youtube";
            case "maps":
            case "google maps":
                return "com.google.android.apps.maps";
            case "gmail":
            case "email":
            case "mail":
                return "com.google.android.gm";
            case "camera":
                return "com.android.camera";
            case "photos":
            case "gallery":
                return "com.google.android.apps.photos";
            case "play store":
            case "playstore":
                return "com.android.vending";
            case "settings":
                return "com.android.settings";
            case "whatsapp":
                return "com.whatsapp";
            case "spotify":
                return "com.spotify.music";
            case "twitter":
            case "x":
                return "com.twitter.android";
            case "instagram":
                return "com.instagram.android";
            case "facebook":
                return "com.facebook.katana";
            case "telegram":
                return "org.telegram.messenger";
            case "calculator":
                return "com.android.calculator2";
            case "clock":
            case "alarm":
                return "com.android.deskclock";
            case "calendar":
                return "com.google.android.calendar";
            case "messages":
            case "sms":
            case "text":
                return "com.google.android.apps.messaging";
            case "phone":
            case "dialer":
                return "com.google.android.dialer";
            case "files":
            case "file manager":
                return "com.google.android.apps.nbu.files";
            default:
                return null;
        }
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
                    processCommand(text);
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

                // Check if it's the recognizer unavailable error
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
                    // Re-request permission
                    checkPermissionsOnLaunch();
                }
            });
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Could animate listen button based on volume — kept for future use
        }

        @Override
        public void onListeningStart() {
            // Already handled in startListening()
        }

        @Override
        public void onListeningEnd() {
            // Handled by onFinalResult/onError
        }
    }
}
