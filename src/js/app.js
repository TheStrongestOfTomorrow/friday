/**
 * Friday — Main App Controller
 *
 * Handles:
 *   - Quick action buttons (duck/resume audio, toggle peek)
 *   - Peek overlay waveform animation
 *   - Navigation
 *   - Status updates
 */

import FridayCore from './friday-core.js';

// ─── DOM Elements ───────────────────────────────────────────
const statusDot = document.getElementById('status-dot');
const statusLabel = document.getElementById('status-label');
const peekOverlay = document.getElementById('peek-overlay');
const peekText = document.getElementById('peek-text');
const waveformCanvas = document.getElementById('waveform-canvas');
const btnTestDuck = document.getElementById('btn-test-duck');
const btnTestResume = document.getElementById('btn-test-resume');
const btnTogglePeek = document.getElementById('btn-toggle-peek');
const btnSettings = document.getElementById('btn-settings');

// ─── State ──────────────────────────────────────────────────
let peekVisible = false;
let isAudioDucked = false;
let animFrameId = null;

// ─── Status Management ──────────────────────────────────────
function setStatus(state, label) {
  statusDot.className = `status-dot ${state}`;
  statusLabel.textContent = label;
}

function setPeekState(state, text) {
  peekOverlay.className = `peek-overlay ${state}`;
  peekText.textContent = text;
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

// ─── Waveform Animation ─────────────────────────────────────
function startWaveform() {
  if (!waveformCanvas) return;
  const ctx = waveformCanvas.getContext('2d');
  const W = waveformCanvas.width;
  const H = waveformCanvas.height;
  const bars = 8;
  const barWidth = 3;
  const gap = (W - bars * barWidth) / (bars + 1);
  let phase = 0;

  function draw() {
    ctx.clearRect(0, 0, W, H);
    const isActive = peekOverlay.classList.contains('active');

    for (let i = 0; i < bars; i++) {
      const amplitude = isActive
        ? 0.4 + 0.6 * Math.abs(Math.sin(phase + i * 0.8))
        : 0.15 + 0.1 * Math.sin(phase * 0.5 + i * 0.6);
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

    phase += isActive ? 0.15 : 0.03;
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

// ─── Audio Ducking ──────────────────────────────────────────
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
    setStatus('idle', 'Plugin unavailable');
    // Demo mode fallback — simulate the action
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
      setStatus('idle', 'Idle');
      setPeekState('visible', 'Waiting...');
    } else {
      setStatus('idle', 'Resume failed');
      setPeekState('visible', 'Resume failed');
    }
  } catch (err) {
    console.warn('FridayCore.resumeAudio() failed:', err);
    setStatus('idle', 'Plugin unavailable');
    // Demo mode fallback
    isAudioDucked = false;
    setStatus('idle', 'Idle (demo)');
    setPeekState('visible', 'Demo: Audio resumed');
  }
}

// ─── Event Listeners ────────────────────────────────────────
btnTestDuck?.addEventListener('click', duckAudio);
btnTestResume?.addEventListener('click', resumeAudio);
btnTogglePeek?.addEventListener('click', togglePeek);
btnSettings?.addEventListener('click', () => {
  window.location.href = '/src/pages/settings.html';
});

// ─── Initialize ─────────────────────────────────────────────
setStatus('idle', 'Idle');

// Auto-show peek after a short delay for demo
setTimeout(() => {
  if (!peekVisible) {
    togglePeek();
  }
}, 1500);
