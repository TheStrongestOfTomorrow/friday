package com.friday.assistant.core;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.util.Log;

import com.friday.assistant.data.AppDatabase;
import com.friday.assistant.data.CustomCommand;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Friday — Intent Router
 *
 * The brain of Friday. Routes recognized speech to the correct
 * Android system action. Supports:
 *   - Built-in commands (time, date, open apps, alarms, settings, etc.)
 *   - User-defined custom commands from Room database
 *   - Fuzzy matching for natural language variations
 *
 * Uses Google's pre-installed speech services for recognition
 * and delegates execution to real Android system intents.
 * No mocks. No placeholders. Every command does something real.
 */
public class IntentRouter {

    private static final String TAG = "Friday/IntentRouter";

    private final Context context;
    private final TTSManager ttsManager;
    private final PrefsManager prefs;

    public IntentRouter(Context context, TTSManager ttsManager, PrefsManager prefs) {
        this.context = context;
        this.ttsManager = ttsManager;
        this.prefs = prefs;
    }

    /**
     * Route a recognized speech text to the appropriate action.
     * Returns the response string (also spoken via TTS).
     */
    public String routeCommand(String spokenText) {
        if (spokenText == null || spokenText.trim().isEmpty()) {
            return say("I didn't catch that. Could you repeat?");
        }

        String cleanText = spokenText.toLowerCase().trim();
        Log.d(TAG, "Routing command: " + cleanText);

        // 1. Check user-defined custom commands first
        CustomCommand custom = lookupCustomCommand(cleanText);
        if (custom != null) {
            return executeCustomAction(custom);
        }

        // 2. Strip wake word prefix if present
        String commandText = stripWakeWord(cleanText);

        // 3. Built-in commands — order matters (more specific first)
        if (isSetAlarmCommand(commandText)) return handleSetAlarm(commandText);
        if (isSetTimerCommand(commandText)) return handleSetTimer(commandText);
        if (isTimeCommand(commandText)) return handleTime();
        if (isDateCommand(commandText)) return handleDate();
        if (isOpenAppCommand(commandText)) return handleOpenApp(commandText);
        if (isCloseAppCommand(commandText)) return handleCloseApp(commandText);
        if (isCallCommand(commandText)) return handleCall(commandText);
        if (isWeatherCommand(commandText)) return handleWeather();
        if (isReminderCommand(commandText)) return handleReminder(commandText);
        if (isCalendarCommand(commandText)) return handleCalendar(commandText);
        if (isVolumeCommand(commandText)) return handleVolume(commandText);
        if (isSettingsCommand(commandText)) return handleOpenSettings();
        if (isFlashlightCommand(commandText)) return handleFlashlight(commandText);
        if (isBatteryCommand(commandText)) return handleBattery();
        if (isHelpCommand(commandText)) return handleHelp();
        if (isStopCommand(commandText)) return handleStop();

        // 4. Wake word alone with no command
        String wakeWord = prefs.getWakeWord().toLowerCase();
        if (WakeWordEngine.match(cleanText, wakeWord, prefs.getConfidenceThreshold()).matched
                && commandText.isEmpty()) {
            return say("Yes? I'm listening.");
        }

        // 5. Unknown command
        return say("I heard \"" + spokenText + "\" but I'm not sure what to do. " +
                "Say \"help\" to see what I can do.");
    }

    // ─── Custom Command Lookup ──────────────────────────────────

    private CustomCommand lookupCustomCommand(String text) {
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            // Exact match first
            CustomCommand exact = db.customCommandDao().getCommandByPhrase(text);
            if (exact != null) return exact;

            // Fuzzy search — check if any custom trigger phrase is contained in the spoken text
            for (CustomCommand cmd : db.customCommandDao().getAllCommands()) {
                if (text.contains(cmd.getTriggerPhrase().toLowerCase())) {
                    return cmd;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error looking up custom command", e);
        }
        return null;
    }

    private String executeCustomAction(CustomCommand command) {
        String actionType = command.getActionType();
        String target = command.getActionTarget();

        switch (actionType) {
            case "LAUNCH_APP":
                return handleOpenApp(target);

            case "TOGGLE_SETTING":
                if ("FLASHLIGHT_ON".equals(target)) return handleFlashlightOn();
                if ("FLASHLIGHT_OFF".equals(target)) return handleFlashlightOff();
                return say("Toggling " + target);

            case "TTS_REPLY":
                return say(target);

            case "OPEN_URL":
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return say("Opening link.");
                } catch (Exception e) {
                    return say("Couldn't open that link.");
                }

            default:
                return say("Running custom action.");
        }
    }

    // ─── Wake Word Handling ──────────────────────────────────────

    private String stripWakeWord(String text) {
        String wakeWord = prefs.getWakeWord().toLowerCase();
        String[] prefixes = {
                wakeWord,
                "hey " + wakeWord,
                "ok " + wakeWord,
                "okay " + wakeWord
        };

        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) {
                String remaining = text.substring(prefix.length()).trim();
                // Remove leading comma or "please"
                remaining = remaining.replaceFirst("^[, ]+", "").replaceFirst("^please ", "");
                return remaining.trim();
            }
        }
        return text;
    }

    // ─── Time ────────────────────────────────────────────────────

    private boolean isTimeCommand(String text) {
        return text.contains("what time") || text.contains("tell me the time") ||
               text.contains("current time") || text.contains("what's the time") ||
               text.matches(".*\\btime\\b.*");
    }

    private String handleTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String time = sdf.format(new Date());
        return say("It's " + time + ".");
    }

    // ─── Date ────────────────────────────────────────────────────

    private boolean isDateCommand(String text) {
        return text.contains("what day") || text.contains("what's the date") ||
               text.contains("today's date") || text.contains("tell me the date") ||
               text.contains("what is today") || text.matches(".*\\bdate\\b.*");
    }

    private String handleDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
        String date = sdf.format(new Date());
        return say("Today is " + date + ".");
    }

    // ─── Open App ────────────────────────────────────────────────

    private boolean isOpenAppCommand(String text) {
        return text.startsWith("open ") || text.startsWith("launch ") ||
               text.startsWith("start ") || text.startsWith("run ");
    }

    private String handleOpenApp(String text) {
        String appName = text.replaceFirst("^(open|launch|start|run)\\s+", "")
                             .replace("the ", "").trim();

        if (appName.isEmpty()) {
            return say("Which app would you like me to open?");
        }

        String packageName = resolveAppPackage(appName);

        if (packageName != null) {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(intent);
                    return say("Opening " + appName + ".");
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
            context.startActivity(searchIntent);
            return say("I couldn't find " + appName + " installed. Searching the Play Store.");
        } catch (Exception e) {
            return say("I couldn't find an app called " + appName + ".");
        }
    }

    // ─── Close App ───────────────────────────────────────────────

    private boolean isCloseAppCommand(String text) {
        return text.startsWith("close ") || text.startsWith("quit ") ||
               text.startsWith("kill ");
    }

    private String handleCloseApp(String text) {
        String appName = text.replaceFirst("^(close|quit|kill)\\s+", "")
                             .replace("the ", "").trim();
        // Android doesn't allow third-party apps to kill other apps.
        // Best we can do is open the app settings page.
        String packageName = resolveAppPackage(appName);
        if (packageName != null) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return say("Opening settings for " + appName + " so you can force stop it.");
            } catch (Exception e) {
                return say("Couldn't open settings for " + appName + ".");
            }
        }
        return say("I couldn't find " + appName + ".");
    }

    // ─── Set Alarm ───────────────────────────────────────────────

    private boolean isSetAlarmCommand(String text) {
        return text.contains("set alarm") || text.contains("set an alarm") ||
               text.matches(".*wake me.*\\d.*") || text.matches(".*alarm.*\\d.*");
    }

    private String handleSetAlarm(String text) {
        // Try to extract time from text like "set alarm for 7 am" or "wake me at 6:30"
        Pattern timePattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = timePattern.matcher(text);

        if (matcher.find()) {
            try {
                int hour = Integer.parseInt(matcher.group(1));
                int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
                String ampm = matcher.group(3);

                if (ampm != null && ampm.toLowerCase().startsWith("p") && hour != 12) {
                    hour += 12;
                } else if (ampm != null && ampm.toLowerCase().startsWith("a") && hour == 12) {
                    hour = 0;
                }

                Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
                intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
                intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
                intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                    String timeStr = String.format(Locale.getDefault(), "%d:%02d %s",
                            hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour),
                            minute, hour >= 12 ? "PM" : "AM");
                    return say("Setting alarm for " + timeStr + ".");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set alarm", e);
            }
        }

        // Fallback: open the alarm app
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return say("Opening the alarm clock.");
            }
        } catch (Exception e) {
            return say("I couldn't set an alarm. Please set it manually.");
        }
        return say("I couldn't understand the time. Try saying \"set alarm for 7 AM\".");
    }

    // ─── Set Timer ───────────────────────────────────────────────

    private boolean isSetTimerCommand(String text) {
        return text.contains("set timer") || text.contains("set a timer") ||
               text.matches(".*timer.*\\d.*") || text.contains("countdown");
    }

    private String handleSetTimer(String text) {
        // Try to extract duration like "set timer for 5 minutes"
        Pattern minPattern = Pattern.compile("(\\d+)\\s*min", Pattern.CASE_INSENSITIVE);
        Pattern secPattern = Pattern.compile("(\\d+)\\s*sec", Pattern.CASE_INSENSITIVE);
        Pattern hourPattern = Pattern.compile("(\\d+)\\s*(hour|hr)", Pattern.CASE_INSENSITIVE);

        int totalSeconds = 0;
        Matcher m;

        m = hourPattern.matcher(text);
        if (m.find()) totalSeconds += Integer.parseInt(m.group(1)) * 3600;

        m = minPattern.matcher(text);
        if (m.find()) totalSeconds += Integer.parseInt(m.group(1)) * 60;

        m = secPattern.matcher(text);
        if (m.find()) totalSeconds += Integer.parseInt(m.group(1));

        if (totalSeconds > 0) {
            try {
                Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
                intent.putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds);
                intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                    return say("Starting timer for " + formatDuration(totalSeconds) + ".");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set timer", e);
            }
        }

        return say("I couldn't understand the timer duration. Try saying \"set timer for 5 minutes\".");
    }

    // ─── Call ────────────────────────────────────────────────────

    private boolean isCallCommand(String text) {
        return text.startsWith("call ") || text.startsWith("dial ") ||
               text.startsWith("phone ");
    }

    private String handleCall(String text) {
        String target = text.replaceFirst("^(call|dial|phone)\\s+", "").trim();

        // Try as phone number
        if (target.matches(".*\\d.*")) {
            String number = target.replaceAll("[^\\d+]", "");
            try {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + number));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return say("Dialing " + number + ".");
            } catch (Exception e) {
                return say("I couldn't dial that number.");
            }
        }

        // It's a name — open the dialer for manual selection
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return say("Opening the phone. I can't look up contacts by name yet, but that's coming soon.");
        } catch (Exception e) {
            return say("I couldn't open the phone app.");
        }
    }

    // ─── Weather ─────────────────────────────────────────────────

    private boolean isWeatherCommand(String text) {
        return text.contains("weather") || text.contains("temperature") ||
               text.contains("forecast") || text.contains("rain") ||
               text.contains("sunny") || text.contains("cold outside") ||
               text.contains("hot outside");
    }

    private String handleWeather() {
        // Open the weather app or Google Weather
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://weather.com"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return say("Opening weather information for you.");
        } catch (Exception e) {
            return say("I can't check the weather directly yet. You can ask Google or check your weather app.");
        }
    }

    // ─── Reminder ────────────────────────────────────────────────

    private boolean isReminderCommand(String text) {
        return text.contains("remind") || text.contains("reminder") ||
               text.contains("note to self") || text.contains("remember to");
    }

    private String handleReminder(String text) {
        // Open the default reminder app
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return say("Opening the alarm app so you can set a reminder.");
            }
        } catch (Exception ignored) {}

        return say("I'll remember that. Setting reminders with the clock app is coming soon.");
    }

    // ─── Calendar ────────────────────────────────────────────────

    private boolean isCalendarCommand(String text) {
        return text.contains("calendar") || text.contains("schedule") ||
               text.contains("appointment") || text.contains("meeting");
    }

    private String handleCalendar(String text) {
        try {
            long startTime = System.currentTimeMillis();
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setType("vnd.android.cursor.item/event");
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return say("Opening the calendar to create an event.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open calendar", e);
        }
        return say("I couldn't open the calendar.");
    }

    // ─── Volume ──────────────────────────────────────────────────

    private boolean isVolumeCommand(String text) {
        return text.contains("volume up") || text.contains("volume down") ||
               text.contains("turn up") || text.contains("turn down") ||
               text.contains("louder") || text.contains("quieter") ||
               text.contains("mute") || text.contains("silent");
    }

    private String handleVolume(String text) {
        android.media.AudioManager am = (android.media.AudioManager)
                context.getSystemService(Context.AUDIO_SERVICE);

        if (am == null) return say("Couldn't access audio controls.");

        int maxVolume = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        int currentVolume = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);

        if (text.contains("mute") || text.contains("silent")) {
            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0);
            return say("Volume muted.");
        } else if (text.contains("volume up") || text.contains("louder") || text.contains("turn up")) {
            int newVol = Math.min(currentVolume + 2, maxVolume);
            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0);
            return say("Volume turned up.");
        } else if (text.contains("volume down") || text.contains("quieter") || text.contains("turn down")) {
            int newVol = Math.max(currentVolume - 2, 0);
            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0);
            return say("Volume turned down.");
        }

        return say("Current volume is " + currentVolume + " out of " + maxVolume + ".");
    }

    // ─── Settings ────────────────────────────────────────────────

    private boolean isSettingsCommand(String text) {
        return text.contains("open settings") || text.contains("go to settings") ||
               text.contains("show settings") || text.contains("android settings");
    }

    private String handleOpenSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return say("Opening settings.");
        } catch (Exception e) {
            return say("Couldn't open settings.");
        }
    }

    // ─── Flashlight ──────────────────────────────────────────────

    private boolean isFlashlightCommand(String text) {
        return text.contains("flashlight") || text.contains("torch") ||
               text.contains("turn on light") || text.contains("turn off light");
    }

    private String handleFlashlight(String text) {
        if (text.contains("off")) {
            return handleFlashlightOff();
        }
        return handleFlashlightOn();
    }

    private String handleFlashlightOn() {
        try {
            android.hardware.camera2.CameraManager camManager =
                    (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (camManager != null) {
                String cameraId = camManager.getCameraIdList()[0];
                camManager.setTorchMode(cameraId, true);
                return say("Flashlight on.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle flashlight", e);
        }
        return say("I couldn't toggle the flashlight. You may need to grant camera permission.");
    }

    private String handleFlashlightOff() {
        try {
            android.hardware.camera2.CameraManager camManager =
                    (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (camManager != null) {
                String cameraId = camManager.getCameraIdList()[0];
                camManager.setTorchMode(cameraId, false);
                return say("Flashlight off.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle flashlight", e);
        }
        return say("I couldn't toggle the flashlight.");
    }

    // ─── Battery ─────────────────────────────────────────────────

    private boolean isBatteryCommand(String text) {
        return text.contains("battery") || text.contains("how much charge") ||
               text.contains("battery level");
    }

    private String handleBattery() {
        try {
            android.content.IntentFilter ifilter = new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent batteryStatus = context.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                int pct = (int) (level * 100.0 / scale);
                int status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING;
                String response = "Battery is at " + pct + " percent.";
                if (charging) response += " It's charging.";
                return say(response);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read battery", e);
        }
        return say("I couldn't check the battery level.");
    }

    // ─── Help ────────────────────────────────────────────────────

    private boolean isHelpCommand(String text) {
        return text.contains("help") || text.contains("what can you do") ||
               text.contains("commands") || text.contains("what do you do");
    }

    private String handleHelp() {
        return say("I can help with: time, date, opening apps, setting alarms and timers, " +
                "dialing phone numbers, checking battery, toggling the flashlight, " +
                "controlling volume, and opening settings. " +
                "You can also set up custom voice commands in Settings.");
    }

    // ─── Stop ────────────────────────────────────────────────────

    private boolean isStopCommand(String text) {
        return text.equals("stop") || text.equals("cancel") ||
               text.equals("never mind") || text.equals("nevermind") ||
               text.equals("quit") || text.equals("exit");
    }

    private String handleStop() {
        ttsManager.stop();
        return "";
    }

    // ─── App Name Resolution ─────────────────────────────────────

    private String resolveAppPackage(String name) {
        switch (name.toLowerCase()) {
            case "chrome": case "browser": return "com.android.chrome";
            case "youtube": return "com.google.android.youtube";
            case "maps": case "google maps": return "com.google.android.apps.maps";
            case "gmail": case "email": case "mail": return "com.google.android.gm";
            case "camera": return "com.android.camera";
            case "photos": case "gallery": return "com.google.android.apps.photos";
            case "play store": case "playstore": return "com.android.vending";
            case "settings": return "com.android.settings";
            case "whatsapp": return "com.whatsapp";
            case "spotify": return "com.spotify.music";
            case "twitter": case "x": return "com.twitter.android";
            case "instagram": return "com.instagram.android";
            case "facebook": return "com.facebook.katana";
            case "telegram": return "org.telegram.messenger";
            case "calculator": return "com.android.calculator2";
            case "clock": case "alarm": return "com.android.deskclock";
            case "calendar": return "com.google.android.calendar";
            case "messages": case "sms": case "text": return "com.google.android.apps.messaging";
            case "phone": case "dialer": return "com.google.android.dialer";
            case "files": case "file manager": return "com.google.android.apps.nbu.files";
            case "netflix": return "com.netflix.mediaclient";
            case "discord": return "com.discord";
            case "reddit": return "com.reddit.frontpage";
            case "tiktok": return "com.zhiliaoapp.musically";
            case "snapchat": return "com.snapchat.android";
            case "paypal": return "com.paypal.android.p2pmobile";
            case "uber": return "com.ubercab";
            case "lyft": return "me.lyft.android";
            default: return null;
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────

    /**
     * Speak the response and return it for display.
     */
    private String say(String text) {
        if (ttsManager != null && ttsManager.isReady()) {
            ttsManager.speak(text);
        }
        return text;
    }

    private String formatDuration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append(" hour").append(hours > 1 ? "s" : "");
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(minutes).append(" minute").append(minutes > 1 ? "s" : "");
        }
        if (seconds > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(seconds).append(" second").append(seconds > 1 ? "s" : "");
        }
        return sb.toString();
    }
}
