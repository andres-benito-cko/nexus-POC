/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      colors: {
        navy: {
          900: '#FFFFFF',
          800: '#F8F8FA',
          700: '#F2F2F5',
          600: '#EBEBEE',
          500: '#E4E4E7',
        },
        accent: {
          DEFAULT: '#186AFF',
          light: '#4d8fff',
          dark: '#0f55cc',
          glow: 'rgba(24,106,255,0.10)',
        },
      },
    },
  },
  plugins: [],
}
