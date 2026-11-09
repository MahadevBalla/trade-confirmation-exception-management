import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    tailwindcss(),
  ],
  server: {
    proxy: {
      '/api/trades': { target: 'http://localhost:8081', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
      '/api/audit': { target: 'http://localhost:8081', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
      '/api/enrichment': { target: 'http://localhost:8082', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
      '/api/validation': { target: 'http://localhost:8083', changeOrigin: true, rewrite: (path) => path.replace(/^\/api\/validation/, '') },
      '/api/exceptions': { target: 'http://localhost:8084', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
      '/api/workitems': { target: 'http://localhost:8085', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
      '/api/dlq': { target: 'http://localhost:8085', changeOrigin: true, rewrite: (path) => path.replace(/^\/api/, '') },
    },
  },
})
