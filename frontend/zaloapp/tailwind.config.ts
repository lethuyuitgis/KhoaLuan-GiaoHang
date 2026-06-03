import type { Config } from 'tailwindcss';

/**
 * TCH-inspired warm coffee palette — mirrored from miniapp so the shop's
 * branding stays consistent across both Telegram and Zalo entrypoints.
 * The `zalo` color is kept for any Zalo-specific UI (e.g. provider banners)
 * but customer-facing CTAs use the brand brown.
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        zalo: {
          DEFAULT: '#0068FF',
          dark: '#0050C8',
          light: '#3D8BFF',
        },
        brand: {
          50:  '#FFF8F1',
          100: '#F5E6D3',
          200: '#E8CDA9',
          300: '#D4A77E',
          400: '#C19A6B',
          500: '#A57850',
          600: '#8B5E3C',
          700: '#5C3317',
          800: '#3D2817',
          900: '#2A1B10',
        },
        cream: {
          50:  '#FFFCF8',
          100: '#FFF8F1',
          200: '#FAEFE0',
        },
      },
      borderRadius: {
        '4xl': '32px',
      },
      boxShadow: {
        warm:      '0 4px 12px -2px rgba(140, 90, 50, 0.08)',
        'warm-lg': '0 12px 32px -8px rgba(140, 90, 50, 0.15)',
        'warm-up': '0 -8px 24px -4px rgba(140, 90, 50, 0.10)',
      },
      fontFamily: {
        sans: [
          '"Inter"',
          '-apple-system',
          'BlinkMacSystemFont',
          '"Segoe UI"',
          'Roboto',
          'sans-serif',
        ],
      },
    },
  },
  plugins: [],
} satisfies Config;
