import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, /api is proxied to Spring Boot (or the mock server) on :8080.
// In production set VITE_API_URL to the deployed backend, e.g. https://ghoomo-api.onrender.com
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
