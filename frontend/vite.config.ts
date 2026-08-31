import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const sourcePath = decodeURIComponent(new URL('./src', import.meta.url).pathname)
  .replace(/^\/([A-Za-z]:\/)/, '$1')

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': sourcePath
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks: {
          'three': ['three'],
          'element-plus': ['element-plus'],
          'vendor': ['vue', 'vue-router', 'pinia', 'axios']
        }
      }
    }
  }
})
