import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: ['host.docker.internal', 'localhost'],
    proxy: {
      '/api/generate': {
        target: process.env.VITE_GENERATOR_URL || 'http://localhost:8084',
        changeOrigin: true,
      },
      '/api': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8083',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      '/ws': {
        target: process.env.VITE_WS_URL || 'ws://localhost:8083',
        changeOrigin: true,
        ws: true,
      },
      '/simulate': {
        target: process.env.VITE_SIMULATOR_URL || 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
