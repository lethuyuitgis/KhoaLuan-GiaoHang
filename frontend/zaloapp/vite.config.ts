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
  build: mode === 'zalo'
    ? {
        // Zalo Mini App build: Zalo không dùng index.html, chỉ nạp asset khai báo
        // trong app-config.json như script CỔ ĐIỂN → gộp thành 1 bundle IIFE duy
        // nhất (không ESM, không code-split) thì Zalo mới chạy được.
        rollupOptions: {
          output: {
            format: 'iife',
            inlineDynamicImports: true,
            entryFileNames: 'assets/app.js',
            chunkFileNames: 'assets/app.js',
            assetFileNames: 'assets/app.[ext]',
          },
        },
      }
    : {
        // Web thường: tách vendor để chunk initial < 500 KB.
        rollupOptions: {
          output: {
            manualChunks: {
              'react-vendor': ['react', 'react-dom', 'react-router-dom'],
              'query-vendor': ['@tanstack/react-query'],
              'map-vendor':   ['leaflet', 'react-leaflet'],
              'i18n-vendor':  ['i18next', 'react-i18next', 'i18next-browser-languagedetector'],
            },
          },
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
