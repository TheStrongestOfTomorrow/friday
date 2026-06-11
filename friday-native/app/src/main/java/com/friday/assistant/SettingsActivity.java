package com.friday.assistant;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.friday.assistant.core.AudioFocusManager;
import com.friday.assistant.core.FridaySpeechRecognizer;
import com.friday.assistant.core.PrefsManager;
import com.friday.assistant.core.PermissionHelper;
import com.friday.assistant.core.TTSManager;
import com.friday.assistant.service.FridayForegroundService;
import com.friday.assistant.service.PeekOverlayService;

/**
 * Friday — Settings Activity
 *
 * All settings are wired to real SharedPreferences.
 * All buttons perform real actions. No mocks.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "Friday/Settings";

    private PrefsManager prefs;
    private TTSManager ttsManager;
    private FridaySpeechRecognizer speechRecognizer;
    private AudioFocusManager audioFocusManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new PrefsManager(this);
        ttsManager = new TTSManager(this);
        audioFocusManager = new AudioFocusManager(this);
        speechRecognizer = new FridaySpeechRecognizer(this, audioFocusManager);

        // Back button
        ImageButton btnBack = findViewById(R.id.btnBackSettings);
        btnBack.setOnClickListener(v -> finish());

        // ─── General ───────────────────────────────────────────
        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText("v3.1.0");

        Button btnRunOnboarding = findViewById(R.id.btnRunOnboarding);
        btnRunOnboarding.setOnClickListener(v -> {
            prefs.setOnboardingCompleted(false);
            prefs.setFirstLaunch(true);
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
        });

        Button btnResetSettings = findViewById(R.id.btnResetSettings);
        btnResetSettings.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Reset All Settings?")
                    .setMessage("This will restore all values to their defaults. This cannot be undone.")
                    .setPositiveButton("Reset", (dialog, which) -> {
                        prefs.resetAll();
                        applySettingsToUI();
                        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // ─── Wake Word ─────────────────────────────────────────
        EditText etWakeWord = findViewById(R.id.etWakeWord);
        etWakeWord.setText(prefs.getWakeWord());
        etWakeWord.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                prefs.setWakeWord(etWakeWord.getText().toString().trim());
            }
        });
        // Also save on action done
        etWakeWord.setOnEditorActionListener((v, actionId, event) -> {
            prefs.setWakeWord(etWakeWord.getText().toString().trim());
            return false;
        });

        // Wire up the Wake Word Enabled switch (was previously dead UI)
        Switch switchWakeWordEnabled = findViewById(R.id.switchWakeWordEnabled);
        switchWakeWordEnabled.setChecked(prefs.isWakeWordEnabled());
        switchWakeWordEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setWakeWordEnabled(isChecked);

            // Update the foreground service monitoring state
            Intent serviceIntent = new Intent(this, FridayForegroundService.class);
            if (isChecked) {
                serviceIntent.setAction("START_MONITORING");
            } else {
                serviceIntent.setAction("STOP_MONITORING");
            }
            startService(serviceIntent);

            Toast.makeText(this, isChecked ? "Wake word detection enabled" : "Wake word detection disabled",
                    Toast.LENGTH_SHORT).show();
        });

        SeekBar seekConfidence = findViewById(R.id.seekConfidence);
        TextView tvConfidenceValue = findViewById(R.id.tvConfidenceValue);
        seekConfidence.setProgress((int) (prefs.getConfidenceThreshold() * 100));
        tvConfidenceValue.setText(Math.round(prefs.getConfidenceThreshold() * 100) + "%");
        seekConfidence.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvConfidenceValue.setText(progress + "%");
                prefs.setConfidenceThreshold(progress / 100f);
            }
        });

        // ─── Audio ─────────────────────────────────────────────
        SeekBar seekTtsRate = findViewById(R.id.seekTtsRate);
        TextView tvTtsRateValue = findViewById(R.id.tvTtsRateValue);
        int rateProgress = (int) (prefs.getTtsRate() * 10);
        seekTtsRate.setProgress(rateProgress);
        tvTtsRateValue.setText(prefs.getTtsRate() + "x");
        seekTtsRate.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float rate = progress / 10f;
                tvTtsRateValue.setText(rate + "x");
                prefs.setTtsRate(rate);
                ttsManager.setRate(rate);
            }
        });

        SeekBar seekTtsPitch = findViewById(R.id.seekTtsPitch);
        TextView tvTtsPitchValue = findViewById(R.id.tvTtsPitchValue);
        int pitchProgress = (int) (prefs.getTtsPitch() * 10);
        seekTtsPitch.setProgress(pitchProgress);
        tvTtsPitchValue.setText(String.valueOf(prefs.getTtsPitch()));
        seekTtsPitch.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float pitch = progress / 10f;
                tvTtsPitchValue.setText(String.valueOf(pitch));
                prefs.setTtsPitch(pitch);
                ttsManager.setPitch(pitch);
            }
        });

        Button btnTestVoice = findViewById(R.id.btnTestVoice);
        btnTestVoice.setOnClickListener(v -> {
            btnTestVoice.setText("Speaking...");
            ttsManager.speak("Hello! I am Friday, your personal assistant. How can I help you today?");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> btnTestVoice.setText("Play"), 4000);
        });

        // ─── Display ───────────────────────────────────────────
        Switch switchPeekGui = findViewById(R.id.switchPeekGui);
        switchPeekGui.setChecked(prefs.isPeekGuiEnabled());
        switchPeekGui.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setPeekGuiEnabled(isChecked);

            // Actually start/stop the Peek overlay service
            if (isChecked && !prefs.isStealthMode()) {
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
        });

        Switch switchStealthMode = findViewById(R.id.switchStealthMode);
        switchStealthMode.setChecked(prefs.isStealthMode());
        switchStealthMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setStealthMode(isChecked);

            // If stealth mode enabled, hide overlay
            if (isChecked) {
                Intent intent = new Intent(this, PeekOverlayService.class);
                intent.setAction("HIDE");
                startService(intent);
            } else if (prefs.isPeekGuiEnabled()) {
                Intent intent = new Intent(this, PeekOverlayService.class);
                intent.setAction("SHOW");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            }
        });

        Switch switchStartOnBoot = findViewById(R.id.switchStartOnBoot);
        switchStartOnBoot.setChecked(prefs.isStartOnBoot());
        switchStartOnBoot.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setStartOnBoot(isChecked);
        });

        // ─── System ────────────────────────────────────────────
        Button btnSetAssistant = findViewById(R.id.btnSetAssistant);
        btnSetAssistant.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS);
                startActivity(intent);
                Toast.makeText(this, "Set Friday as your default assistant", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Please set Friday as your assistant in Android Settings", Toast.LENGTH_LONG).show();
            }
        });

        Button btnDisableBattery = findViewById(R.id.btnDisableBattery);
        btnDisableBattery.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        startActivity(intent);
                    } catch (Exception e2) {
                        Toast.makeText(this, "Please disable battery optimization in Settings", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsManager != null) ttsManager.destroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }

    private void applySettingsToUI() {
        EditText etWakeWord = findViewById(R.id.etWakeWord);
        Switch switchWakeWordEnabled = findViewById(R.id.switchWakeWordEnabled);
        SeekBar seekConfidence = findViewById(R.id.seekConfidence);
        SeekBar seekTtsRate = findViewById(R.id.seekTtsRate);
        SeekBar seekTtsPitch = findViewById(R.id.seekTtsPitch);
        Switch switchPeekGui = findViewById(R.id.switchPeekGui);
        Switch switchStealthMode = findViewById(R.id.switchStealthMode);
        Switch switchStartOnBoot = findViewById(R.id.switchStartOnBoot);

        etWakeWord.setText(prefs.getWakeWord());
        switchWakeWordEnabled.setChecked(prefs.isWakeWordEnabled());
        seekConfidence.setProgress((int) (prefs.getConfidenceThreshold() * 100));
        seekTtsRate.setProgress((int) (prefs.getTtsRate() * 10));
        seekTtsPitch.setProgress((int) (prefs.getTtsPitch() * 10));
        switchPeekGui.setChecked(prefs.isPeekGuiEnabled());
        switchStealthMode.setChecked(prefs.isStealthMode());
        switchStartOnBoot.setChecked(prefs.isStartOnBoot());
    }

    private abstract class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
