import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 8081 is the origin the API's CORS allow-list expects (app.cors.allowed-origins).
export default defineConfig({
  plugins: [react()],
  server: { port: 8081, strictPort: true },
  preview: { port: 8081, strictPort: true },
});
