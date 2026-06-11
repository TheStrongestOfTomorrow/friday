package com.friday.assistant.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.friday.assistant.MainActivity;
import com.friday.assistant.R;
import com.friday.assistant.core.AudioFocusManager;
import com.friday.assistant.core.FridaySpeechRecognizer;
import com.friday.assistant.core.PrefsManager;
import com.friday.assistant.core.WakeWordEngine;

/**
 * Friday — Foreground Service
 *
 * Keeps Friday running in the background. When wake word detection
 * is enabled, this service periodically listens for the wake word
 * and launches MainActivity when it's detected.
 *
 * This is a REAL foreground service with a persistent notification
 * and actual background wake word detection.
 */
public class FridayForegroundService extends Service {

    private static final String TAG = "Friday/ForegroundSvc";
    private static final String CHANNEL_ID = "friday_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Wake word detection interval: listen for 5 seconds, then pause for 10 seconds
    private static final long LISTEN_DURATION_MS = 5000L;
    private static final long PAUSE_DURATION_MS = 10000L;

    private PrefsManager prefs;
    private AudioFocusManager audioFocusManager;
    private FridaySpeechRecognizer speechRecognizer;
    private Handler wakeWordHandler;
    private boolean isWakeWordListening = false;
    private boolean isMonitoring = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        prefs = new PrefsManager(this);
        audioFocusManager = new AudioFocusManager(this);
        wakeWordHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "Foreground service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if ("STOP_MONITORING".equals(action)) {
            stopWakeWordMonitoring();
            updateNotification("Friday is Idle", "Wake word detection paused");
            return START_STICKY;
        }

        if ("START_MONITORING".equals(action)) {
            startWakeWordMonitoring();
            return START_STICKY;
        }

        // Default: start foreground and begin monitoring if enabled
        Notification notification = buildNotification("Friday is Listening", "Tap to open Friday");
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "Foreground service started");

        // Start wake word monitoring if enabled
        if (prefs.isWakeWordEnabled() && prefs.isOnboardingCompleted()) {
            startWakeWordMonitoring();
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopWakeWordMonitoring();
        Log.d(TAG, "Foreground service destroyed");
    }

    // ─── Wake Word Monitoring ──────────────────────────────────

    private void startWakeWordMonitoring() {
        if (isMonitoring) return;

        isMonitoring = true;
        updateNotification("Friday is Listening", "Listening for \"" + prefs.getWakeWord() + "\"");
        Log.d(TAG, "Starting wake word monitoring for: " + prefs.getWakeWord());

        // Initialize speech recognizer for background listening
        if (speechRecognizer == null) {
            speechRecognizer = new FridaySpeechRecognizer(this, audioFocusManager);
            speechRecognizer.setCallback(new WakeWordCallback());
        }

        scheduleNextListening();
    }

    private void stopWakeWordMonitoring() {
        isMonitoring = false;
        isWakeWordListening = false;
        wakeWordHandler.removeCallbacksAndMessages(null);

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        Log.d(TAG, "Wake word monitoring stopped");
    }

    private void scheduleNextListening() {
        if (!isMonitoring) return;

        wakeWordHandler.postDelayed(() -> {
            if (!isMonitoring) return;

            if (!prefs.isWakeWordEnabled()) {
                // Wake word disabled — stop monitoring but keep service alive
                updateNotification("Friday is Idle", "Wake word detection disabled");
                isMonitoring = false;
                return;
            }

            startWakeWordListeningCycle();
        }, PAUSE_DURATION_MS);
    }

    private void startWakeWordListeningCycle() {
        if (!isMonitoring || isWakeWordListening) return;

        // Check if SpeechRecognizer is available
        if (speechRecognizer == null) {
            speechRecognizer = new FridaySpeechRecognizer(this, audioFocusManager);
            speechRecognizer.setCallback(new WakeWordCallback());
        }

        if (!speechRecognizer.isAvailable()) {
            Log.w(TAG, "SpeechRecognizer not available, skipping wake word cycle");
            scheduleNextListening();
            return;
        }

        isWakeWordListening = true;
        speechRecognizer.startListening();

        // Auto-stop listening after the listen duration
        wakeWordHandler.postDelayed(() -> {
            if (speechRecognizer != null && speechRecognizer.isListening()) {
                speechRecognizer.stopListening();
            }
            isWakeWordListening = false;

            // Schedule next cycle
            if (isMonitoring) {
                scheduleNextListening();
            }
        }, LISTEN_DURATION_MS);
    }

    // ─── Wake Word Callback ────────────────────────────────────

    private class WakeWordCallback implements FridaySpeechRecognizer.SpeechCallback {
        @Override
        public void onPartialResult(String text) {
            // Not used in background monitoring
        }

        @Override
        public void onFinalResult(String text) {
            isWakeWordListening = false;
            if (text == null || text.isEmpty()) return;

            String wakeWord = prefs.getWakeWord();
            float threshold = prefs.getConfidenceThreshold();

            WakeWordEngine.MatchResult match = WakeWordEngine.match(
                    text.toLowerCase(), wakeWord.toLowerCase(), threshold);

            if (match.matched) {
                Log.d(TAG, "Wake word detected! Launching MainActivity");
                updateNotification("Wake Word Detected!", "Opening Friday...");

                // Launch MainActivity
                Intent intent = new Intent(FridayForegroundService.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra("from_assistant", true);
                startActivity(intent);

                // Pause monitoring for a bit after activation to avoid loops
                wakeWordHandler.removeCallbacksAndMessages(null);
                wakeWordHandler.postDelayed(() -> {
                    if (isMonitoring) {
                        updateNotification("Friday is Listening", "Listening for \"" + prefs.getWakeWord() + "\"");
                        scheduleNextListening();
                    }
                }, 30000L); // Wait 30 seconds after activation before listening again
            }
        }

        @Override
        public void onError(String message, int errorCode) {
            isWakeWordListening = false;
            // Don't log too verbosely for expected errors like NO_MATCH or SPEECH_TIMEOUT
            if (errorCode != android.speech.SpeechRecognizer.ERROR_NO_MATCH &&
                errorCode != android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Log.w(TAG, "Background speech error: " + message);
            }
        }

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onListeningStart() {
            Log.d(TAG, "Background listening cycle started");
        }

        @Override
        public void onListeningEnd() {
            Log.d(TAG, "Background listening cycle ended");
        }
    }

    // ─── Notification ──────────────────────────────────────────

    /**
     * Update the foreground notification text.
     */
    public void updateNotification(String title, String text) {
        Notification notification = buildNotification(title, text);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String title, String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.service_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.service_channel_desc));
            channel.setShowBadge(false);

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
