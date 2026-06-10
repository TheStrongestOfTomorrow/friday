export interface FridayCorePlugin {
  duckAudio(): Promise<{ success: boolean }>;
  resumeAudio(): Promise<{ success: boolean }>;
  getAudioFocusState(): Promise<{ state: string }>;
}
