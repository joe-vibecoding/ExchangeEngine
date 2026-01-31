/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            fontFamily: {
                mono: ['JetBrains Mono', 'Roboto Mono', 'monospace'],
            },
            colors: {
                // "Dark Terminal" Palette
                slate: {
                    800: '#1e293b',
                    900: '#0f172a', // Deep background
                    950: '#020617', // Blacker background
                },
                // HFT Accents
                buy: {
                    DEFAULT: '#10b981', // emerald-500
                    bg: 'rgba(16, 185, 129, 0.1)',
                },
                sell: {
                    DEFAULT: '#ef4444', // red-500
                    bg: 'rgba(239, 68, 68, 0.1)',
                }
            },
        },
    },
    plugins: [],
}
