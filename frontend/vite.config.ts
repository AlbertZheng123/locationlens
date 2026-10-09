import { defineConfig } from 'vitest/config';

export default defineConfig({
  build: {
    lib: { entry: 'src/index.ts', formats: ['es'], fileName: 'locationlens-reviews' },
    rollupOptions: { external: ['react', 'react/jsx-runtime'] },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
  },
});
