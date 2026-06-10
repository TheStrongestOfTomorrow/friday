# Friday

> System-level, voice-controlled macro and automation assistant for Android.

Friday is a deterministic tool — no conversational AI fluff. It uses a wake word to activate, pauses background media (audio ducking), and executes user-defined macros (simulating screen taps, running shell commands, or opening apps).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Capacitor (Web UI → Android wrapper) |
| Web Frontend | HTML, CSS, Vanilla JavaScript |
| Native Android | Kotlin (Capacitor plugins) |
| System APIs | AccessibilityService, VoiceInteractionService, AudioManager, Shizuku |

---

## Project Structure

```
friday/
├── index.html                    # Main app entry (Home/Dashboard)
├── src/
│   ├── css/
│   │   ├── main.css              # Global dark-mode styles
│   │   ├── peek.css              # Peek overlay (bottom-center/right)
│   │   └── settings.css          # Settings page styles
│   ├── js/
│   │   ├── app.js                # Main app controller
│   │   ├── friday-core.ts        # JS bridge → FridayCore plugin
│   │   └── settings.js           # Settings page controller
│   └── pages/
│       └── settings.html         # Settings page
├── plugins/
│   └── friday-core/
│       ├── package.json          # Capacitor plugin manifest
│       ├── src/
│       │   ├── index.ts          # Plugin TS exports
│       │   └── definitions.ts    # TypeScript interface
│       └── android/
│           └── src/main/java/com/friday/core/plugin/
│               └── FridayCorePlugin.kt  # Kotlin native plugin
├── android/
│   ├── app/                      # Android app module
│   ├── friday-core/              # FridayCore as Android library module
│   ├── build.gradle              # Root Gradle (with Kotlin)
│   └── settings.gradle           # Includes friday-core module
├── capacitor.config.ts           # Capacitor configuration
├── vite.config.ts                # Vite bundler config
├── tsconfig.json                 # TypeScript configuration
└── package.json                  # Node.js project
```

---

## Phase 1 — What's Built

### Web UI
- **Dark-mode, sleek, minimalist** interface with purple accent palette
- **Home/Dashboard**: Status indicator (Idle/Active/Listening), quick action buttons, macro list
- **Settings Page**: Wake word input, sensitivity slider, toggles (Stealth Mode, Audio Ducking, Vibration), macro management, system configuration (Assistant, Accessibility, Shizuku)
- **Peek Overlay**: Semi-transparent pill at bottom-center (portrait) / bottom-right (landscape). Contains a canvas-based waveform animation and status text

### Native Plugin (FridayCore)
- **`duckAudio()`**: Requests `AUDIOFOCUS_GAIN_TRANSIENT` — pauses/ducks background media (Spotify, YouTube, etc.) using Android's `AudioManager` with proper `AudioFocusRequest` on API 26+
- **`resumeAudio()`**: Abandons audio focus — background media resumes
- **`getAudioFocusState()`**: Returns current focus state
- Full Android 8+ `AudioFocusRequest` support with graceful fallback for older devices

### JS Bridge
- TypeScript interface for `FridayCorePlugin`
- Registered via `@capacitor/core`'s `registerPlugin()`
- Graceful fallback (demo mode) when running in browser without native plugin

---

## Getting Started

### Prerequisites

1. **Node.js** 18+ and **npm** 9+
2. **Android Studio** with SDK (API 24+)
3. **Java JDK** 17+
4. **Android device** with USB debugging enabled (or emulator)

### Install & Build

```bash
# 1. Clone the repository
git clone https://github.com/YOUR_USERNAME/friday.git
cd friday

# 2. Install Node.js dependencies
npm install

# 3. Build the web assets
npm run build

# 4. Sync with Capacitor (copies web assets + registers plugins)
npx cap sync android

# 5. Open in Android Studio
npx cap open android
```

### Build Debug APK (from terminal)

```bash
# Option A: One-liner
npm run android:build

# Option B: Step by step
npm run build
npx cap sync android
cd android
./gradlew assembleDebug
```

The debug APK will be at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

### Install on Device

```bash
# Via ADB (USB debugging must be enabled)
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# Or: Drag the APK to your device and install manually
```

### Enable USB Debugging on Android
1. Settings → About Phone → Tap "Build Number" 7 times
2. Settings → Developer Options → Enable USB Debugging
3. Connect device via USB
4. Run `adb devices` to verify

---

## Development

```bash
# Start Vite dev server (hot reload)
npm run dev

# In a separate terminal, sync changes to Android
npx cap sync android

# Run on connected Android device
npx cap run android
```

---

## Plugin API Reference

### `FridayCore.duckAudio()`

Requests `AUDIOFOCUS_GAIN_TRANSIENT` from Android's `AudioManager`. This causes other audio apps to either pause or significantly lower their volume.

```javascript
import FridayCore from './js/friday-core.js';

const result = await FridayCore.duckAudio();
// result = { success: true } or { success: false }
```

### `FridayCore.resumeAudio()`

Abandons the audio focus request, allowing background media to resume at normal volume.

```javascript
const result = await FridayCore.resumeAudio();
// result = { success: true } or { success: false }
```

### `FridayCore.getAudioFocusState()`

Returns the current audio focus state.

```javascript
const result = await FridayCore.getAudioFocusState();
// result = { state: "gained" | "lost" | "lost_transient" | "lost_transient_can_duck" | "abandoned" | "none" }
```

---

## Roadmap

| Phase | Feature |
|-------|---------|
| **Phase 1** ✅ | Project init, Web UI, AudioManager plugin, JS bridge |
| **Phase 2** | Wake word detection (Porcupine/Vosk), AccessibilityService for tap simulation |
| **Phase 3** | Macro engine (JSON-defined macros), Shizuku shell execution |
| **Phase 4** | VoiceInteractionService (Default Assistant), system overlay permissions |
| **Phase 5** | Macro editor UI, macro import/export, community macros |

---

## License

MIT
