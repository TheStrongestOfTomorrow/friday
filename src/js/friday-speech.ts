/**
 * Friday — FridaySpeech Capacitor Plugin Bridge
 *
 * Provides JavaScript access to Android's native SpeechRecognizer:
 *   - startListening() — begins STT with live partial results
 *   - stopListening()  — ends STT session
 *   - isListening()    — check current state
 *   - isAvailable()    — check device capability
 *
 * Events streamed to JS:
 *   - "onPartialResult"   — live transcription as user speaks
 *   - "onFinalResult"     — final recognized text
 *   - "onError"           — recognition error
 *   - "onListeningStart"  — recognizer is ready
 *   - "onListeningEnd"    — recognizer stopped
 *   - "onRmsChanged"      — real-time volume level
 *
 * SHUSH PROTOCOL:
 *   startListening() automatically calls FridayCore.duckAudio()
 *   When recognition ends (onFinalResult/onError), it calls FridayCore.resumeAudio()
 */

import { registerPlugin } from '@capacitor/core';
import FridayCore from './friday-core.js';

export interface FridaySpeechPlugin {
  startListening(options?: { language?: string }): Promise<{ text: string; success: boolean }>;
  stopListening(): Promise<{ success: boolean }>;
  isListening(): Promise<{ isListening: boolean }>;
  isAvailable(): Promise<{ available: boolean }>;
}

// Register the native plugin
const FridaySpeechNative = registerPlugin<FridaySpeechPlugin>('FridaySpeech');

// ─── Shush Protocol Wrapper ──────────────────────────────────

/**
 * FridaySpeech wraps the native plugin and adds the "Shush Protocol":
 *
 * 1. When startListening() is called:
 *    → Automatically ducks background audio (FridayCore.duckAudio())
 *    → Then starts native speech recognition
 *
 * 2. When recognition finishes (final result or error):
 *    → Automatically resumes background audio (FridayCore.resumeAudio())
 *
 * This ensures background media (Spotify, YouTube, etc.) is always
 * paused/ducked while Friday is listening for a command.
 */
const FridaySpeech = {
  _shushActive: false,

  /**
   * Start listening with the Shush Protocol.
   * Ducks audio first, then starts speech recognition.
   */
  async startListening(options = {}) {
    try {
      // Step 1: Duck background audio (Shush Protocol)
      await this._duck();
      this._shushActive = true;

      // Step 2: Start native speech recognition
      const result = await FridaySpeechNative.startListening(options);
      return result;
    } catch (err) {
      // If anything fails, make sure we resume audio
      console.warn('FridaySpeech.startListening() failed:', err);
      await this._resume();
      throw err;
    }
  },

  /**
   * Stop listening. Audio will be resumed by the event handlers
   * when onFinalResult or onError fires. But we also call
   * stopListening on the native side to trigger those events.
   */
  async stopListening() {
    try {
      return await FridaySpeechNative.stopListening();
    } catch (err) {
      console.warn('FridaySpeech.stopListening() failed:', err);
      // Force resume even if stop fails
      await this._resume();
      throw err;
    }
  },

  /**
   * Check if currently listening.
   */
  async isListening() {
    return await FridaySpeechNative.isListening();
  },

  /**
   * Check if speech recognition is available.
   */
  async isAvailable() {
    return await FridaySpeechNative.isAvailable();
  },

  /**
   * Resume audio manually (e.g., after an error that didn't
   * trigger the automatic resume).
   */
  async forceResume() {
    await this._resume();
  },

  // ─── Internal Shush Methods ─────────────────────────────────

  async _duck() {
    try {
      await FridayCore.duckAudio();
    } catch (err) {
      console.warn('Shush: duckAudio failed (non-critical):', err);
    }
  },

  async _resume() {
    if (!this._shushActive) return;
    this._shushActive = false;
    try {
      await FridayCore.resumeAudio();
    } catch (err) {
      console.warn('Shush: resumeAudio failed (non-critical):', err);
    }
  },

  /**
   * Called by the app controller when onFinalResult is received.
   * Triggers the Shush Protocol resume.
   */
  onRecognitionComplete() {
    this._resume();
  },

  /**
   * Called by the app controller when onError is received.
   * Triggers the Shush Protocol resume.
   */
  onRecognitionError() {
    this._resume();
  },
};

export default FridaySpeech;
