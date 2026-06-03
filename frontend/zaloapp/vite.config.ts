import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  // In dev: served from /. In prod build: served behind nginx at /zaloapp/.
  // Note: production deploy via zmp-cli wraps this into a .zmp package — the
  // `base` setting only matters for static SPA preview before integrating with
  // the real Zalo Mini App runtime.
  base: mode === 'production' ? '/zaloapp/' : '/',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5182,
    host: true,
    allowedHosts: true as unknown as string[],
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
}));
