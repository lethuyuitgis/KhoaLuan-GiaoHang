import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  // In dev: served from /. In prod build: served behind nginx at /miniapp/.
  base: mode === 'production' ? '/miniapp/' : '/',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    // Split vendor bundles so the initial chunk stays under 500 KB —
    // same pattern as webadmin/vite.config.ts.
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'query-vendor': ['@tanstack/react-query'],
          'chart-vendor': ['recharts'],
          'map-vendor':   ['leaflet', 'react-leaflet'],
          'ws-vendor':    ['@stomp/stompjs', 'sockjs-client'],
          'i18n-vendor':  ['i18next', 'react-i18next', 'i18next-browser-languagedetector'],
        },
      },
    },
  },
  server: {
    port: 5173,
    host: true,
    // Dev only — accept any external Host header so Mini App can be served
    // through any HTTPS tunnel when demoing via Telegram.
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
