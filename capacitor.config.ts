import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.friday.assistant',
  appName: 'Friday',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
  },
  android: {
    allowMixedContent: false,
    buildOptions: {
      signingType: 'apksigner',
    },
  },
  plugins: {
    // FridayCore plugin will be registered here natively
  },
};

export default config;
