package com.friday.assistant.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.friday.assistant.core.PrefsManager;

/**
 * Friday — Boot Receiver
 *
 * Starts Friday's foreground service when the device boots.
 * Only starts if the user has enabled "Start on Boot" in settings.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "Friday/BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            PrefsManager prefs = new PrefsManager(context);

            if (!prefs.isStartOnBoot()) {
                Log.d(TAG, "Start on boot is disabled, skipping");
                return;
            }

            if (!prefs.isOnboardingCompleted()) {
                Log.d(TAG, "Onboarding not completed, skipping auto-start");
                return;
            }

            Log.d(TAG, "Boot received, starting Friday service");

            Intent serviceIntent = new Intent(context, FridayForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
