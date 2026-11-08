/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        meu: {
          50: '#f0f5fa',
          100: '#e1ecf5',
          200: '#c3d9eb',
          300: '#95bedc',
          400: '#5f9ec8',
          500: '#3882b4',
          600: '#286895',
          700: '#215378',
          800: '#1e4765',
          900: '#0d2840', // Mersin Uni Deep Navy
          950: '#071828',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
