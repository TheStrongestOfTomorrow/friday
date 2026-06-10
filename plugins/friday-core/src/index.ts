import { registerPlugin } from '@capacitor/core';

export interface FridayCorePlugin {
  duckAudio(): Promise<{ success: boolean }>;
  resumeAudio(): Promise<{ success: boolean }>;
  getAudioFocusState(): Promise<{ state: string }>;
}

const FridayCore = registerPlugin<FridayCorePlugin>('FridayCore');

export default FridayCore;
