import type { Config } from 'tailwindcss';

/**
 * TCH-inspired warm coffee palette.
 * Numeric scale follows Tailwind conventions so utility chaining stays predictable.
 *
 * brand-50/100: cream / chip default backgrounds.
 * brand-600/700: primary CTAs and dark display text.
 * brand-800/900: H1 and near-black accents.
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Telegram theme variables — kept for backwards compatibility with
        // any component still relying on tg-* tokens (search input, etc.).
        tg: {
          bg: 'var(--tg-theme-bg-color, #FFF8F1)',
          text: 'var(--tg-theme-text-color, #3D2817)',
          hint: 'var(--tg-theme-hint-color, #9B7B5F)',
          link: 'var(--tg-theme-link-color, #5C3317)',
          button: 'var(--tg-theme-button-color, #5C3317)',
          buttonText: 'var(--tg-theme-button-text-color, #ffffff)',
          secondaryBg: 'var(--tg-theme-secondary-bg-color, #F5E6D3)',
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
