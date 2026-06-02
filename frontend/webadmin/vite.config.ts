import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  // Dev: '/'. Prod: behind nginx at '/admin/'.
  base: mode === 'production' ? '/admin/' : '/',
  // sockjs-client (P9 admin WebSocket) references Node's `global` — Vite leaves it
  // undefined in the browser bundle, which throws on module load and blanks the page.
  // `define` covers app-source rewriting; `optimizeDeps.esbuildOptions.define` covers
  // the esbuild pre-bundle pass that Vite runs over CJS deps like sockjs-client.
  define: { global: 'globalThis' },
  optimizeDeps: {
    esbuildOptions: { define: { global: 'globalThis' } },
  },
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws':  { target: 'http://localhost:8080', changeOrigin: true, ws: true },
    },
  },
}));
