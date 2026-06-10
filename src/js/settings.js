/**
 * Friday — Settings Page Controller
 *
 * Handles all settings interactions:
 *   - Wake word input
 *   - Sensitivity slider
 *   - Toggle switches (stealth, ducking, vibration)
 *   - Macro management (add/remove placeholder)
 *   - System configuration buttons
 */

import FridayCore from './friday-core.js';

// ─── DOM Elements ───────────────────────────────────────────
const wakeWordInput = document.getElementById('wake-word');
const sensitivitySlider = document.getElementById('sensitivity');
const sensitivityValue = document.getElementById('sensitivity-value');
const stealthToggle = document.getElementById('stealth-mode');
const audioDuckingToggle = document.getElementById('audio-ducking');
const vibrationToggle = document.getElementById('vibration');
const btnAddMacro = document.getElementById('btn-add-macro');
const macroContainer = document.getElementById('settings-macro-container');
const btnSetAssistant = document.getElementById('btn-set-assistant');
const btnSetAccessibility = document.getElementById('btn-set-accessibility');
const shizukuStatus = document.getElementById('shizuku-status');

// ─── State ──────────────────────────────────────────────────
let settings = loadSettings();

// ─── Settings Persistence ───────────────────────────────────
function loadSettings() {
  try {
    const saved = localStorage.getItem('friday_settings');
    return saved ? JSON.parse(saved) : getDefaultSettings();
  } catch {
    return getDefaultSettings();
  }
}

function saveSettings() {
  localStorage.setItem('friday_settings', JSON.stringify(settings));
}

function getDefaultSettings() {
  return {
    wakeWord: 'Hey Friday',
    sensitivity: 7,
    stealthMode: false,
    audioDucking: true,
    vibration: true,
    macros: [],
  };
}

// ─── Apply Settings to UI ───────────────────────────────────
function applySettingsToUI() {
  if (wakeWordInput) wakeWordInput.value = settings.wakeWord;
  if (sensitivitySlider) sensitivitySlider.value = settings.sensitivity;
  if (sensitivityValue) sensitivityValue.textContent = settings.sensitivity;
  if (stealthToggle) stealthToggle.checked = settings.stealthMode;
  if (audioDuckingToggle) audioDuckingToggle.checked = settings.audioDucking;
  if (vibrationToggle) vibrationToggle.checked = settings.vibration;
  renderMacros();
}

// ─── Macro Rendering ────────────────────────────────────────
function renderMacros() {
  if (!macroContainer) return;

  if (settings.macros.length === 0) {
    macroContainer.innerHTML = `
      <div class="macro-empty">
        <p>No macros configured yet.</p>
        <p class="macro-hint">Tap the button below to create your first macro.</p>
      </div>
    `;
    return;
  }

  macroContainer.innerHTML = settings.macros
    .map(
      (macro, index) => `
    <div class="macro-item" data-index="${index}">
      <div>
        <div class="macro-name">${escapeHtml(macro.name)}</div>
        <div class="macro-trigger">${escapeHtml(macro.trigger)}</div>
      </div>
      <div class="macro-actions">
        <button class="btn-delete-macro" data-index="${index}" title="Delete">&times;</button>
      </div>
    </div>
  `
    )
    .join('');

  // Bind delete buttons
  macroContainer.querySelectorAll('.btn-delete-macro').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const idx = parseInt(e.currentTarget.dataset.index, 10);
      settings.macros.splice(idx, 1);
      saveSettings();
      renderMacros();
    });
  });
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

// ─── Add Macro (Phase 1: placeholder) ──────────────────────
let macroCounter = 0;

function addMacro() {
  macroCounter++;
  settings.macros.push({
    name: `Macro ${macroCounter}`,
    trigger: `voice:"run macro ${macroCounter}"`,
    actions: [],
  });
  saveSettings();
  renderMacros();
}

// ─── System Configuration ───────────────────────────────────
function openAssistantSettings() {
  // In the real app, this would use an intent to open Android assistant settings
  try {
    // FridayCore.openAssistantSettings(); // Future API
    alert(
      'On your Android device:\n\n' +
        'Settings → Apps → Default Apps → Digital Assistant App → Select "Friday"'
    );
  } catch {
    alert('Please set Friday as your default assistant in Android Settings.');
  }
}

function openAccessibilitySettings() {
  try {
    // FridayCore.openAccessibilitySettings(); // Future API
    alert(
      'On your Android device:\n\n' +
        'Settings → Accessibility → Installed Services → Friday → Enable'
    );
  } catch {
    alert('Please enable Friday in Android Accessibility Settings.');
  }
}

async function checkShizukuStatus() {
  try {
    // const result = await FridayCore.getShizukuStatus(); // Future API
    // Update badge accordingly
  } catch {
    // Shizuku not available in web preview
  }
}

// ─── Event Listeners ────────────────────────────────────────
wakeWordInput?.addEventListener('change', () => {
  settings.wakeWord = wakeWordInput.value.trim() || 'Hey Friday';
  saveSettings();
});

sensitivitySlider?.addEventListener('input', () => {
  settings.sensitivity = parseInt(sensitivitySlider.value, 10);
  if (sensitivityValue) sensitivityValue.textContent = settings.sensitivity;
  saveSettings();
});

stealthToggle?.addEventListener('change', () => {
  settings.stealthMode = stealthToggle.checked;
  saveSettings();
});

audioDuckingToggle?.addEventListener('change', () => {
  settings.audioDucking = audioDuckingToggle.checked;
  saveSettings();
});

vibrationToggle?.addEventListener('change', () => {
  settings.vibration = vibrationToggle.checked;
  saveSettings();
});

btnAddMacro?.addEventListener('click', addMacro);
btnSetAssistant?.addEventListener('click', openAssistantSettings);
btnSetAccessibility?.addEventListener('click', openAccessibilitySettings);

// ─── Initialize ─────────────────────────────────────────────
applySettingsToUI();
