import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // Pure-TS helpers — no DOM needed. Keeps the run fast.
    environment: 'node',
    globals: false,
    include: ['src/**/*.test.ts'],
  },
});
