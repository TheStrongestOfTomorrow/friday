package com.friday.assistant.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.friday.assistant.MainActivity;
import com.friday.assistant.R;
import com.friday.assistant.core.FridaySpeechRecognizer;
import com.friday.assistant.core.PrefsManager;
import com.friday.assistant.core.WakeWordEngine;

/**
 * Friday — Foreground Service
 *
 * Keeps Friday running in the background with a persistent notification.
 *
 * FIX v3.2.0: Complete rewrite of wake word detection.
 * The old approach used SpeechRecognizer in a background service, which
 * is unreliable on modern Android (requires Activity context on most devices,
 * gets killed by battery optimization, doesn't work from background on 11+).
 *
 * New approach: Two-phase detection
 *   Phase 1: AudioRecord energy detection — lightweight, always-on
 *     Uses raw microphone input to detect when someone is speaking
 *     (energy level above threshold). This uses very little CPU.
 *   Phase 2: When speech is detected, start SpeechRecognizer briefly
 *     to transcribe what was said, then check against the wake word.
 *     SpeechRecognizer is only active for a few seconds at a time.
 *
 * This is much more reliable and battery-efficient than the old approach
 * of constantly starting/stopping SpeechRecognizer.
 */
public class FridayForegroundService extends Service {

    private static final String TAG = "Friday/ForegroundSvc";
    private static final String CHANNEL_ID = "friday_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Audio monitoring settings
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    private static final int ENERGY_CHECK_INTERVAL_MS = 500; // Check every 500ms
    private static final int SPEECH_ENERGY_THRESHOLD = 1500; // RMS threshold for speech detection
    private static final int SILENCE_COUNT_BEFORE_STOP = 3; // 3 consecutive low readings = silence
    private static final long COOLDOWN_AFTER_ACTIVATION_MS = 30000L; // 30s cooldown after detection

    private PrefsManager prefs;
    private Handler handler;
    private boolean isMonitoring = false;
    private boolean isTranscribing = false;

    // Phase 1: Raw audio monitoring
    private AudioRecord audioRecord;
    private boolean isAudioRecording = false;
    private int silenceCount = 0;

    // Phase 2: Speech recognition (only when speech detected)
    private FridaySpeechRecognizer speechRecognizer;
    private com.friday.assistant.core.AudioFocusManager audioFocusManager;

    private long lastActivationTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        prefs = new PrefsManager(this);
        handler = new Handler(Looper.getMainLooper());
        audioFocusManager = new com.friday.assistant.core.AudioFocusManager(this);

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
        Notification notification = buildNotification("Friday is Ready", "Tap to open Friday");
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

    // ─── Wake Word Monitoring (Two-Phase) ──────────────────────

    private void startWakeWordMonitoring() {
        if (isMonitoring) return;

        // Check mic permission
        if (!com.friday.assistant.core.PermissionHelper.isMicGranted(this)) {
            Log.w(TAG, "Microphone permission not granted, cannot monitor for wake word");
            updateNotification("Friday — No Mic", "Grant microphone permission to enable wake word");
            return;
        }

        isMonitoring = true;
        updateNotification("Friday is Listening", "Listening for \"" + prefs.getWakeWord() + "\"");
        Log.d(TAG, "Starting wake word monitoring (Phase 1: audio energy)");

        // Start Phase 1: raw audio energy monitoring
        startAudioEnergyMonitoring();
    }

    private void stopWakeWordMonitoring() {
        isMonitoring = false;
        isTranscribing = false;
        handler.removeCallbacksAndMessages(null);
        stopAudioEnergyMonitoring();

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        Log.d(TAG, "Wake word monitoring stopped");
    }

    // ─── Phase 1: Audio Energy Detection ───────────────────────
    // Lightweight always-on monitoring using AudioRecord.
    // Only detects when someone is speaking (energy above threshold).
    // Does NOT do speech recognition — that's Phase 2.

    private void startAudioEnergyMonitoring() {
        if (isAudioRecording) return;

        // Check mic permission again
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "No mic permission for audio monitoring");
            if (isMonitoring) {
                handler.postDelayed(this::startAudioEnergyMonitoring, 5000);
            }
            return;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    Math.max(BUFFER_SIZE, 1024));

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized");
                audioRecord.release();
                audioRecord = null;
                // Retry later
                if (isMonitoring) {
                    handler.postDelayed(this::startAudioEnergyMonitoring, 5000);
                }
                return;
            }

            audioRecord.startRecording();
            isAudioRecording = true;
            silenceCount = 0;
            Log.d(TAG, "Audio energy monitoring started");

            // Start checking audio energy periodically
            checkAudioEnergy();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio monitoring", e);
            audioRecord = null;
            if (isMonitoring) {
                handler.postDelayed(this::startAudioEnergyMonitoring, 10000);
            }
        }
    }

    private void stopAudioEnergyMonitoring() {
        isAudioRecording = false;
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioRecord", e);
            }
            audioRecord = null;
        }
    }

    private void checkAudioEnergy() {
        if (!isMonitoring || !isAudioRecording || audioRecord == null) return;

        try {
            short[] buffer = new short[BUFFER_SIZE / 2];
            int read = audioRecord.read(buffer, 0, buffer.length);

            if (read > 0) {
                // Calculate RMS energy
                long sum = 0;
                for (int i = 0; i < read; i++) {
                    sum += (long) buffer[i] * buffer[i];
                }
                double rms = Math.sqrt((double) sum / read);

                if (rms > SPEECH_ENERGY_THRESHOLD) {
                    // Speech detected!
                    silenceCount = 0;
                    Log.d(TAG, "Speech energy detected: RMS=" + (int)rms);

                    // Only start Phase 2 if we're not already transcribing
                    // and we're not in cooldown
                    if (!isTranscribing && !isInCooldown()) {
                        startTranscription();
                    }
                } else {
                    silenceCount++;
                    if (silenceCount > SILENCE_COUNT_BEFORE_STOP && isTranscribing) {
                        // Speaker has stopped, stop transcription
                        stopTranscription();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking audio energy", e);
        }

        // Schedule next check
        if (isMonitoring) {
            handler.postDelayed(this::checkAudioEnergy, ENERGY_CHECK_INTERVAL_MS);
        }
    }

    private boolean isInCooldown() {
        return System.currentTimeMillis() - lastActivationTime < COOLDOWN_AFTER_ACTIVATION_MS;
    }

    // ─── Phase 2: Speech Recognition ───────────────────────────
    // Started when Phase 1 detects speech energy.
    // Uses SpeechRecognizer for a brief period to transcribe,
    // then checks against the wake word.

    private void startTranscription() {
        if (isTranscribing) return;

        // Check if SpeechRecognizer is available
        if (speechRecognizer == null) {
            speechRecognizer = new FridaySpeechRecognizer(this, audioFocusManager);
            speechRecognizer.setCallback(new TranscriptionCallback());
        }

        if (!speechRecognizer.isAvailable()) {
            Log.w(TAG, "SpeechRecognizer not available for transcription");
            return;
        }

        isTranscribing = true;
        Log.d(TAG, "Phase 2: Starting speech transcription");

        try {
            speechRecognizer.startListening();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start transcription", e);
            isTranscribing = false;
        }

        // Auto-stop transcription after 5 seconds max
        handler.postDelayed(() -> {
            if (isTranscribing) {
                stopTranscription();
            }
        }, 5000);
    }

    private void stopTranscription() {
        if (speechRecognizer != null && speechRecognizer.isListening()) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping transcription", e);
            }
        }
        isTranscribing = false;
    }

    // ─── Transcription Callback ────────────────────────────────

    private class TranscriptionCallback implements FridaySpeechRecognizer.SpeechCallback {
        @Override
        public void onPartialResult(String text) {
            // Check partial results against wake word for faster response
            if (text != null && !text.isEmpty()) {
                String wakeWord = prefs.getWakeWord();
                float threshold = prefs.getConfidenceThreshold();
                WakeWordEngine.MatchResult match = WakeWordEngine.match(
                        text.toLowerCase(), wakeWord.toLowerCase(), threshold);
                if (match.matched) {
                    Log.d(TAG, "Wake word detected in partial result!");
                    onWakeWordDetected();
                }
            }
        }

        @Override
        public void onFinalResult(String text) {
            isTranscribing = false;
            if (text == null || text.isEmpty()) return;

            String wakeWord = prefs.getWakeWord();
            float threshold = prefs.getConfidenceThreshold();

            WakeWordEngine.MatchResult match = WakeWordEngine.match(
                    text.toLowerCase(), wakeWord.toLowerCase(), threshold);

            if (match.matched) {
                onWakeWordDetected();
            } else {
                Log.d(TAG, "Heard \"" + text + "\" — not a wake word match");
            }
        }

        @Override
        public void onError(String message, int errorCode) {
            isTranscribing = false;
            // Don't log verbosely for expected errors
            if (errorCode != android.speech.SpeechRecognizer.ERROR_NO_MATCH &&
                errorCode != android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Log.w(TAG, "Transcription error: " + message);
            }
        }

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onListeningStart() {
            Log.d(TAG, "Transcription started");
        }

        @Override
        public void onListeningEnd() {
            Log.d(TAG, "Transcription ended");
        }
    }

    // ─── Wake Word Detected! ───────────────────────────────────

    private void onWakeWordDetected() {
        lastActivationTime = System.currentTimeMillis();
        Log.d(TAG, "WAKE WORD DETECTED! Launching Friday...");
        updateNotification("Wake Word Detected!", "Opening Friday...");

        // Stop current transcription
        isTranscribing = false;
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
        }

        // Pause audio monitoring during activation
        stopAudioEnergyMonitoring();

        // Launch MainActivity with assistant flag
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("from_assistant", true);
        startActivity(intent);

        // Resume monitoring after cooldown
        handler.postDelayed(() -> {
            if (isMonitoring) {
                updateNotification("Friday is Listening", "Listening for \"" + prefs.getWakeWord() + "\"");
                startAudioEnergyMonitoring();
            }
        }, COOLDOWN_AFTER_ACTIVATION_MS);
    }

    // ─── Notification ──────────────────────────────────────────

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
