package com.friday.assistant.core;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

/**
 * Friday — Permission Helper
 *
 * Checks and requests all runtime permissions that Friday needs.
 * No mocks — calls real Android permission APIs.
 */
public class PermissionHelper {

    // All permissions Friday needs — using Manifest.permission constants
    public static final String[] ALL_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_CONTACTS,
    };

    /**
     * Check if a single permission is granted.
     */
    public static boolean isGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if ALL required runtime permissions are granted.
     */
    public static boolean allGranted(Context context) {
        for (String perm : ALL_PERMISSIONS) {
            if (!isGranted(context, perm)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if overlay (SYSTEM_ALERT_WINDOW) permission is granted.
     * This is a special permission that can't be requested at runtime —
     * the user must grant it in Settings.
     */
    public static boolean isOverlayGranted(Context context) {
        return android.provider.Settings.canDrawOverlays(context);
    }

    /**
     * Check if battery optimization is disabled for this app.
     */
    public static boolean isBatteryOptimizationDisabled(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager)
                    context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return true; // Pre-M doesn't have battery optimization
    }

    /**
     * Check if the microphone permission specifically is granted.
     */
    public static boolean isMicGranted(Context context) {
        return isGranted(context, Manifest.permission.RECORD_AUDIO);
    }

    /**
     * Get count of ungranted runtime permissions.
     */
    public static int ungrantedCount(Context context) {
        int count = 0;
        for (String perm : ALL_PERMISSIONS) {
            if (!isGranted(context, perm)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get the list of ungranted permission strings.
     */
    public static String[] getUngrantedPermissions(Context context) {
        java.util.ArrayList<String> ungranted = new java.util.ArrayList<>();
        for (String perm : ALL_PERMISSIONS) {
            if (!isGranted(context, perm)) {
                ungranted.add(perm);
            }
        }
        return ungranted.toArray(new String[0]);
    }
}
