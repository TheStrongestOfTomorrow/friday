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
 *
 * FIX: Separated MIC-only check from allGranted so that
 * speech recognition doesn't fail with a misleading "mic permission
 * required" message when mic IS granted but contacts isn't.
 */
public class PermissionHelper {

    // Core permissions — without these, Friday cannot function at all
    public static final String[] CORE_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
    };

    // Optional permissions — Friday works without these but some features are limited
    public static final String[] OPTIONAL_PERMISSIONS = {
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_CONTACTS,
    };

    // All permissions combined
    public static final String[] ALL_PERMISSIONS;

    static {
        ALL_PERMISSIONS = new String[CORE_PERMISSIONS.length + OPTIONAL_PERMISSIONS.length];
        System.arraycopy(CORE_PERMISSIONS, 0, ALL_PERMISSIONS, 0, CORE_PERMISSIONS.length);
        System.arraycopy(OPTIONAL_PERMISSIONS, 0, ALL_PERMISSIONS, CORE_PERMISSIONS.length, OPTIONAL_PERMISSIONS.length);
    }

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
     * FIX: Check if only CORE permissions are granted (mic).
     * This is the check that should be used for speech recognition.
     * Speech recognition ONLY needs RECORD_AUDIO — it should NOT
     * fail because contacts permission was denied.
     */
    public static boolean coreGranted(Context context) {
        for (String perm : CORE_PERMISSIONS) {
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

    /**
     * FIX: Get only the ungranted CORE permissions (mic).
     */
    public static String[] getUngrantedCorePermissions(Context context) {
        java.util.ArrayList<String> ungranted = new java.util.ArrayList<>();
        for (String perm : CORE_PERMISSIONS) {
            if (!isGranted(context, perm)) {
                ungranted.add(perm);
            }
        }
        return ungranted.toArray(new String[0]);
    }
}
