package com.friday.assistant.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.friday.assistant.R;

/**
 * Friday — Peek Overlay Service
 *
 * Shows a floating overlay (Peek GUI) on top of other apps.
 * Requires SYSTEM_ALERT_WINDOW permission.
 *
 * This is a REAL overlay using WindowManager — not a mock.
 */
public class PeekOverlayService extends Service {

    private static final String TAG = "Friday/PeekOverlay";

    private WindowManager windowManager;
    private View overlayView;
    private TextView peekText;
    private View peekStatusDot;
    private boolean isShowing = false;

    private final Handler animHandler = new Handler(Looper.getMainLooper());
    private int animFrame = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("SHOW".equals(action)) {
                showOverlay();
            } else if ("HIDE".equals(action)) {
                hideOverlay();
            } else if ("UPDATE".equals(action)) {
                String text = intent.getStringExtra("text");
                String state = intent.getStringExtra("state");
                updateOverlay(text, state);
            }
        }
        return START_NOT_STICKY;
    }

    @SuppressLint("InflateParams")
    private void showOverlay() {
        if (isShowing || windowManager == null) return;

        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Overlay permission not granted");
                return;
            }
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_peek, null);

        peekText = overlayView.findViewById(R.id.peekText);
        peekStatusDot = overlayView.findViewById(R.id.peekStatusDot);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = 100;

        try {
            windowManager.addView(overlayView, params);
            isShowing = true;
            startWaveAnimation();
            Log.d(TAG, "Peek overlay shown");
        } catch (Exception e) {
            Log.e(TAG, "Failed to show overlay", e);
        }
    }

    private void hideOverlay() {
        if (!isShowing || overlayView == null) return;

        try {
            animHandler.removeCallbacksAndMessages(null);
            windowManager.removeView(overlayView);
            isShowing = false;
            Log.d(TAG, "Peek overlay hidden");
        } catch (Exception e) {
            Log.e(TAG, "Failed to hide overlay", e);
        }
    }

    private void updateOverlay(String text, String state) {
        if (!isShowing || peekText == null || peekStatusDot == null) return;

        if (text != null) {
            peekText.setText(text);
        }

        int dotColor;
        if ("listening".equals(state)) {
            dotColor = ContextCompat.getColor(this, R.color.brand_purple);
        } else if ("active".equals(state)) {
            dotColor = ContextCompat.getColor(this, R.color.success_green);
        } else if ("error".equals(state)) {
            dotColor = ContextCompat.getColor(this, R.color.danger_red);
        } else {
            dotColor = ContextCompat.getColor(this, R.color.status_idle);
        }
        peekStatusDot.setBackgroundColor(dotColor);
    }

    private void startWaveAnimation() {
        // Animate the waveform bars
        LinearLayout waveform = overlayView.findViewById(R.id.peekWaveform);
        if (waveform == null) return;

        animHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isShowing) return;

                int count = waveform.getChildCount();
                for (int i = 0; i < count; i++) {
                    View bar = waveform.getChildAt(i);
                    float scale = 0.3f + 0.7f * (float) Math.abs(Math.sin(animFrame * 0.15 + i * 0.8));
                    bar.setScaleY(scale);
                }
                animFrame++;

                animHandler.postDelayed(this, 50);
            }
        }, 50);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideOverlay();
    }
}
