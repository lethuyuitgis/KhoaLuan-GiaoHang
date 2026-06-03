import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Zalo brand palette — used by SplashPage and primary CTAs.
        zalo: {
          DEFAULT: '#0068FF',
          dark: '#0050C8',
          light: '#3D8BFF',
        },
      },
    },
  },
  plugins: [],
} satisfies Config;
