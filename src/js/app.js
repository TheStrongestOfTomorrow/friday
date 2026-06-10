/**
 * Friday — Main App Controller
 * Phase 2: Speech Recognition + Live UI Wiring
 *
 * Handles:
 *   - Speech recognition (start/stop via FridaySpeech)
 *   - Shush Protocol (auto duck/resume audio around listening)
 *   - Peek overlay: waveform animation + live transcription
 *   - Listen button (manual trigger for Phase 2)
 *   - Transcription card (shows recognized text)
 *   - Status updates
 */

import FridayCore from './friday-core.js';
import FridaySpeech from './friday-speech.js';

// ─── DOM Elements ───────────────────────────────────────────
const statusDot = document.getElementById('status-dot');
const statusLabel = document.getElementById('status-label');
const statusDesc = document.getElementById('status-desc');
const peekOverlay = document.getElementById('peek-overlay');
const peekText = document.getElementById('peek-text');
const peekWaveform = document.getElementById('peek-waveform');
const waveformCanvas = document.getElementById('waveform-canvas');
const btnTestDuck = document.getElementById('btn-test-duck');
const btnTestResume = document.getElementById('btn-test-resume');
const btnTogglePeek = document.getElementById('btn-toggle-peek');
const btnSettings = document.getElementById('btn-settings');

// Phase 2 elements
const btnListen = document.getElementById('btn-listen');
const listenLabel = document.getElementById('listen-label');
const listenWaveCanvas = document.getElementById('listen-wave-canvas');
const transcriptionCard = document.getElementById('transcription-card');
const transcriptionText = document.getElementById('transcription-text');
const transcriptionStatus = document.getElementById('transcription-status');

// ─── State ──────────────────────────────────────────────────
let peekVisible = false;
let isAudioDucked = false;
let isListening = false;
let animFrameId = null;
let listenAnimFrameId = null;
let currentRms = 0;

// ─── Status Management ──────────────────────────────────────
function setStatus(state, label, desc) {
  if (statusDot) statusDot.className = `status-dot ${state}`;
  if (statusLabel) statusLabel.textContent = label;
  if (desc && statusDesc) statusDesc.textContent = desc;
}

function setPeekState(state, text) {
  if (peekOverlay) peekOverlay.className = `peek-overlay ${state}`;
  if (peekText) peekText.textContent = text;
}

function setListeningState(listening) {
  isListening = listening;

  if (btnListen) {
    btnListen.classList.toggle('listening', listening);
  }
  if (listenLabel) {
    listenLabel.textContent = listening ? 'Listening...' : 'Tap to Listen';
  }
  if (transcriptionCard) {
    transcriptionCard.classList.toggle('listening', listening);
    transcriptionCard.classList.toggle('hidden', false);
  }
  if (transcriptionStatus) {
    transcriptionStatus.textContent = listening ? 'Listening...' : '';
    transcriptionStatus.className = 'transcription-status';
  }

  if (listening) {
    setStatus('listening', 'Listening', 'Speak your command...');
    setPeekState('listening transcribing', 'Listening...');
    startListenWaveAnimation();
  } else {
    startWaveform(); // Return to idle waveform
    stopListenWaveAnimation();
  }
}

// ─── Peek Overlay Toggle ────────────────────────────────────
function togglePeek() {
  peekVisible = !peekVisible;
  if (peekVisible) {
    setPeekState('visible', 'Waiting...');
    startWaveform();
  } else {
    setPeekState('', '');
    stopWaveform();
  }
}

// ─── Waveform Animation (Peek) ──────────────────────────────
function startWaveform() {
  if (!waveformCanvas) return;
  if (animFrameId) cancelAnimationFrame(animFrameId);

  const ctx = waveformCanvas.getContext('2d');
  const W = waveformCanvas.width;
  const H = waveformCanvas.height;
  const bars = 8;
  const barWidth = 3;
  const gap = (W - bars * barWidth) / (bars + 1);
  let phase = 0;

  function draw() {
    ctx.clearRect(0, 0, W, H);
    const isActive = peekOverlay?.classList.contains('listening');

    for (let i = 0; i < bars; i++) {
      let amplitude;
      if (isActive) {
        // Responsive to RMS from speech recognizer
        const rmsFactor = Math.min(currentRms / 10, 1);
        amplitude = 0.3 + 0.7 * Math.abs(Math.sin(phase + i * 0.6)) * (0.4 + 0.6 * rmsFactor);
      } else {
        amplitude = 0.15 + 0.1 * Math.sin(phase * 0.5 + i * 0.6);
      }
      const barH = H * amplitude;
      const x = gap + i * (barWidth + gap);
      const y = (H - barH) / 2;

      ctx.fillStyle = isActive
        ? `rgba(124, 58, 237, ${0.6 + 0.4 * amplitude})`
        : `rgba(136, 136, 164, ${0.3 + 0.2 * amplitude})`;
      ctx.beginPath();
      ctx.roundRect(x, y, barWidth, barH, 1.5);
      ctx.fill();
    }

    phase += isActive ? 0.18 : 0.03;
    animFrameId = requestAnimationFrame(draw);
  }

  draw();
}

function stopWaveform() {
  if (animFrameId) {
    cancelAnimationFrame(animFrameId);
    animFrameId = null;
  }
  if (waveformCanvas) {
    const ctx = waveformCanvas.getContext('2d');
    ctx.clearRect(0, 0, waveformCanvas.width, waveformCanvas.height);
  }
}

// ─── Listen Button Wave Animation ───────────────────────────
function startListenWaveAnimation() {
  if (!listenWaveCanvas) return;
  if (listenAnimFrameId) cancelAnimationFrame(listenAnimFrameId);

  const ctx = listenWaveCanvas.getContext('2d');
  const W = listenWaveCanvas.width;
  const H = listenWaveCanvas.height;
  const cx = W / 2;
  const cy = H / 2;
  let phase = 0;

  function draw() {
    ctx.clearRect(0, 0, W, H);
    const rmsFactor = Math.min(currentRms / 10, 1);
    const baseRadius = 8;
    const maxRadius = 18;
    const radius = baseRadius + (maxRadius - baseRadius) * rmsFactor;

    // Microphone icon (simplified)
    ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
    ctx.beginPath();
    ctx.roundRect(cx - 5, cy - 10, 10, 14, 5);
    ctx.fill();

    // Mic stand
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.9)';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(cx, cy + 4);
    ctx.lineTo(cx, cy + 10);
    ctx.moveTo(cx - 5, cy + 10);
    ctx.lineTo(cx + 5, cy + 10);
    ctx.stroke();

    // Sound wave rings
    if (isListening) {
      for (let ring = 0; ring < 3; ring++) {
        const ringPhase = phase + ring * 0.8;
        const ringRadius = radius + ring * 6 + Math.sin(ringPhase) * 3;
        const alpha = 0.3 - ring * 0.08;

        ctx.strokeStyle = `rgba(124, 58, 237, ${alpha})`;
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.arc(cx, cy, ringRadius, -0.8, 0.8);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx, cy, ringRadius, Math.PI - 0.8, Math.PI + 0.8);
        ctx.stroke();
      }
    }

    phase += 0.08;
    listenAnimFrameId = requestAnimationFrame(draw);
  }

  draw();
}

function stopListenWaveAnimation() {
  if (listenAnimFrameId) {
    cancelAnimationFrame(listenAnimFrameId);
    listenAnimFrameId = null;
  }
  if (listenWaveCanvas) {
    const ctx = listenWaveCanvas.getContext('2d');
    ctx.clearRect(0, 0, listenWaveCanvas.width, listenWaveCanvas.height);
  }
}

// ─── Audio Ducking (direct controls) ────────────────────────
async function duckAudio() {
  try {
    setStatus('listening', 'Ducking...');
    setPeekState('active', 'Ducking audio...');

    const result = await FridayCore.duckAudio();

    if (result.success) {
      isAudioDucked = true;
      setStatus('active', 'Audio Ducked');
      setPeekState('active', 'Audio ducked');
    } else {
      setStatus('idle', 'Duck failed');
      setPeekState('visible', 'Duck failed');
    }
  } catch (err) {
    console.warn('FridayCore.duckAudio() failed:', err);
    isAudioDucked = true;
    setStatus('active', 'Audio Ducked (demo)');
    setPeekState('active', 'Demo: Audio ducked');
  }
}

async function resumeAudio() {
  try {
    setStatus('listening', 'Resuming...');
    setPeekState('active', 'Resuming audio...');

    const result = await FridayCore.resumeAudio();

    if (result.success) {
      isAudioDucked = false;
      setStatus('idle', 'Idle', 'Friday is standing by. Say the wake word to activate.');
      setPeekState('visible', 'Waiting...');
    } else {
      setStatus('idle', 'Resume failed');
      setPeekState('visible', 'Resume failed');
    }
  } catch (err) {
    console.warn('FridayCore.resumeAudio() failed:', err);
    isAudioDucked = false;
    setStatus('idle', 'Idle', 'Friday is standing by.');
    setPeekState('visible', 'Demo: Audio resumed');
  }
}

// ─── Speech Recognition (with Shush Protocol) ───────────────

async function startListening() {
  if (isListening) return;

  try {
    setListeningState(true);
    if (transcriptionText) {
      transcriptionText.textContent = '';
      transcriptionText.classList.add('partial');
    }

    // FridaySpeech.startListening() automatically ducks audio (Shush Protocol)
    await FridaySpeech.startListening({ language: 'en-US' });

  } catch (err) {
    console.warn('startListening failed:', err);
    setListeningState(false);
    setStatus('idle', 'Error', 'Speech recognition unavailable. Try again.');

    // Demo fallback: simulate listening
    simulateListening();
  }
}

async function stopListening() {
  if (!isListening) return;

  try {
    await FridaySpeech.stopListening();
  } catch (err) {
    console.warn('stopListening failed:', err);
    setListeningState(false);
    setStatus('idle', 'Idle', 'Friday is standing by.');
    FridaySpeech.onRecognitionError();
  }
}

function toggleListening() {
  if (isListening) {
    stopListening();
  } else {
    startListening();
  }
}

// ─── Capacitor Event Listeners ──────────────────────────────

// These are fired by the native plugin via notifyListeners()

// Live partial transcription
window.addEventListener('onPartialResult', (event) => {
  // Capacitor wraps the data differently depending on version
  const data = event.detail?.data || event.detail;
  if (!data) return;

  const text = data.text || '';
  console.log('[Partial]', text);

  if (transcriptionText && text) {
    transcriptionText.textContent = text;
    transcriptionText.classList.add('partial');
    transcriptionText.classList.remove('final');
  }

  // Update Peek overlay
  setPeekState('listening transcribing', text || 'Listening...');
});

// Final transcription result
window.addEventListener('onFinalResult', (event) => {
  const data = event.detail?.data || event.detail;
  if (!data) return;

  const text = data.text || '';
  console.log('[Final]', text);

  if (transcriptionText) {
    transcriptionText.textContent = text;
    transcriptionText.classList.remove('partial');
  }
  if (transcriptionStatus) {
    transcriptionStatus.textContent = 'Complete';
    transcriptionStatus.className = 'transcription-status final';
  }

  setListeningState(false);
  setStatus('active', 'Recognized', `"${text}"`);
  setPeekState('visible', text || 'Done');

  // Shush Protocol: resume audio after recognition completes
  FridaySpeech.onRecognitionComplete();
});

// Recognition error
window.addEventListener('onError', (event) => {
  const data = event.detail?.data || event.detail;
  if (!data) return;

  console.warn('[Speech Error]', data.message);

  if (transcriptionText) {
    transcriptionText.textContent = `Error: ${data.message}`;
    transcriptionText.classList.remove('partial');
  }
  if (transcriptionStatus) {
    transcriptionStatus.textContent = 'Error';
    transcriptionStatus.className = 'transcription-status error';
  }

  setListeningState(false);
  setStatus('idle', 'Error', data.message || 'Recognition failed');
  setPeekState('visible', 'Error');

  // Shush Protocol: resume audio after error
  FridaySpeech.onRecognitionError();
});

// Listening started
window.addEventListener('onListeningStart', (event) => {
  console.log('[Speech] Listening started');
  setListeningState(true);
});

// Listening ended
window.addEventListener('onListeningEnd', (event) => {
  console.log('[Speech] Listening ended');
  // Don't set isListening=false here — onFinalResult/onError handles that
  // (onListeningEnd fires before onFinalResult)
});

// RMS changed (volume level from mic)
window.addEventListener('onRmsChanged', (event) => {
  const data = event.detail?.data || event.detail;
  if (data && typeof data.rms === 'number') {
    currentRms = data.rms;
  }
});

// ─── Demo Fallback (browser / no native plugin) ─────────────

function simulateListening() {
  setListeningState(true);

  const demoTexts = [
    'Hey Friday...',
    'Hey Friday... open Spotify',
    'Hey Friday... open Spotify and play morning playlist',
  ];

  let step = 0;
  const interval = setInterval(() => {
    if (step < demoTexts.length) {
      const text = demoTexts[step];
      if (transcriptionText) {
        transcriptionText.textContent = text;
        transcriptionText.classList.add('partial');
      }
      setPeekState('listening transcribing', text);
      step++;
    } else {
      clearInterval(interval);
      const finalText = demoTexts[demoTexts.length - 1];
      if (transcriptionText) {
        transcriptionText.textContent = finalText;
        transcriptionText.classList.remove('partial');
      }
      if (transcriptionStatus) {
        transcriptionStatus.textContent = 'Complete';
        transcriptionStatus.className = 'transcription-status final';
      }
      setListeningState(false);
      setStatus('active', 'Recognized', `"${finalText}"`);
      setPeekState('visible', finalText);
    }
  }, 800);
}

// ─── Event Listeners ────────────────────────────────────────
btnTestDuck?.addEventListener('click', duckAudio);
btnTestResume?.addEventListener('click', resumeAudio);
btnTogglePeek?.addEventListener('click', togglePeek);
btnSettings?.addEventListener('click', () => {
  window.location.href = '/src/pages/settings.html';
});
btnListen?.addEventListener('click', toggleListening);

// ─── Initialize ─────────────────────────────────────────────
setStatus('idle', 'Idle', 'Friday is standing by. Say the wake word to activate.');

// Draw idle mic icon on listen canvas
if (listenWaveCanvas) {
  const ctx = listenWaveCanvas.getContext('2d');
  const cx = listenWaveCanvas.width / 2;
  const cy = listenWaveCanvas.height / 2;
  ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
  ctx.beginPath();
  ctx.roundRect(cx - 5, cy - 10, 10, 14, 5);
  ctx.fill();
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.9)';
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(cx, cy + 4);
  ctx.lineTo(cx, cy + 10);
  ctx.moveTo(cx - 5, cy + 10);
  ctx.lineTo(cx + 5, cy + 10);
  ctx.stroke();
}

// Auto-show peek after a short delay
setTimeout(() => {
  if (!peekVisible) {
    togglePeek();
  }
}, 1500);
