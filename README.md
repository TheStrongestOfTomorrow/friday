# ⚡ Friday Voice Assistant — Desktop (Linux, Windows, macOS) & Android Native

Friday is a smart, responsive voice assistant built for **Desktop** (Linux, Windows, macOS) and **Android Native**. It features real-time voice activation with a custom **"Hey Friday" wake word engine**, support for **custom `.tflite` model uploads**, floating mini/toast overlay widgets, system tool integrations, and automated GitHub Actions CI/CD builds.

---

## 🌟 Key Features

### 💻 Desktop Features (`friday-desktop`)
- **Multi-Platform Support**: Built with Electron & Node.js for Linux (`.AppImage`, `.deb`), Windows (`.exe` NSIS/portable), and macOS (`.dmg`).
- **Dual Display Modes**: Toggle between Full-Screen App and Floating Toast / Mini Overlay widget.
- **System Commands & Integration**: Direct volume control, app launching (Chrome, VS Code, Terminal), shell command runner, and live hardware statistics.
- **Tray & Hotkeys**: Global keyboard shortcut (`Ctrl+Shift+F` / `Cmd+Shift+F`) and system tray widget for quick access.

### 📱 Android Native Features (`friday-native`)
- **Native Assistant Service**: Registered Android Assistant Service (`FridayVoiceInteractionService`).
- **Seamless Toast Mini Overlay**: On-screen floating widget that transitions into the full app on tap.
- **Audio Focus & TTS**: Seamless audio ducking during speech interaction and Text-to-Speech synthesis.

### 🤖 Voice Activation & Custom TFLite
- **Built-in "Hey Friday" Engine**: Reliable keyword matching with fuzzy confidence scoring.
- **Custom Model Upload**: Load custom trained `.tflite` wake word files with configurable sensitivity thresholds in Settings.

---

## 🛠️ Project Structure

```
.
├── friday-desktop/            # Electron Desktop App (Linux, Windows, macOS)
│   ├── src/
│   │   ├── main/             # Main process (preload.js, main.js)
│   │   └── renderer/         # Dark purple UI (index.html, styles.css, renderer.js, toast.html)
│   ├── tests/                # Automated test suite (test_runner.js)
│   └── package.json          # Desktop scripts & packaging config
│
├── friday-native/             # Android Native Gradle Project
│   ├── app/                  # Java/Android source code & layouts
│   ├── build.gradle          # Android Gradle build file
│   └── gradlew               # Gradle wrapper
│
└── .github/workflows/         # GitHub Actions CI/CD workflows
    └── build-apps.yml        # Multi-platform Desktop & Android build pipeline
```

---

## 🚀 Quick Start & Build Instructions

### 💻 Running Desktop App Locally
- `cd friday-desktop`
- `npm install`
- Launch Electron app locally

### 🧪 Running Desktop Test Suite
- `cd friday-desktop`
- `npm test`

### 📦 Packaging Desktop Applications
- **Linux (AppImage & deb)**: `npm run package:linux`
- **Windows (NSIS & portable)**: `npm run package:win`
- **macOS (dmg & zip)**: `npm run package:mac`

### 📱 Building Android APK
- `cd friday-native`
- `./gradlew assembleDebug`

---

## ⚙️ Automated GitHub Actions CI/CD (`.github/workflows/build-apps.yml`)

The repository automatically builds installers and binaries on every push and pull request:
1. **Linux**: Builds `.AppImage` and `.deb` desktop artifacts.
2. **Windows**: Builds NSIS installer and portable `.exe`.
3. **macOS**: Builds `.dmg` desktop installer.
4. **Android**: Compiles `app-debug.apk`.

---

## 📄 License
MIT License. Built for the Friday Assistant Ecosystem.
