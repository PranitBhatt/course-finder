import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, /api/* is forwarded to the Spring Boot backend, so the browser sees one origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
