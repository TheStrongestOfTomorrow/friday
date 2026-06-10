/**
 * Friday — FridayCore Capacitor Plugin Bridge
 *
 * Provides JavaScript access to native Android functionality:
 *   - Audio ducking (pause/resume background media)
 *   - Future: Accessibility taps, Shizuku shell, VoiceInteraction
 */

import { registerPlugin } from '@capacitor/core';

/**
 * TypeScript interface for the FridayCore plugin.
 * This must match the Kotlin plugin's exposed methods.
 */
export interface FridayCorePlugin {
  /**
   * Request AUDIOFOCUS_GAIN_TRANSIENT to duck/pause background media.
   * Returns { success: boolean } when the focus has been acquired.
   */
  duckAudio(): Promise<{ success: boolean }>;

  /**
   * Abandon audio focus, allowing background media to resume.
   * Returns { success: boolean } when the focus has been released.
   */
  resumeAudio(): Promise<{ success: boolean }>;

  /**
   * Check current audio focus state.
   * Returns { state: string } where state is one of:
   *   'gained' | 'lost' | 'lost_transient' | 'lost_transient_can_duck'
   */
  getAudioFocusState(): Promise<{ state: string }>;
}

/**
 * Register the FridayCore plugin with Capacitor.
 * The plugin name MUST match the Kotlin class's @CapacitorPlugin annotation.
 */
const FridayCore = registerPlugin<FridayCorePlugin>('FridayCore');

export default FridayCore;
