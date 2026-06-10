import { defineConfig } from 'vite';

export default defineConfig({
  root: '.',
  base: './',
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    rollupOptions: {
      input: {
        main: './index.html',
        settings: './src/pages/settings.html',
      },
    },
  },
  server: {
    port: 5173,
    host: true,
  },
});
