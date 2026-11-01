import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The service allows http://localhost:* and http://127.0.0.1:* origins (alert.api.allowed-origin-patterns).
export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
  test: { environment: 'node' },
});
