import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
const host = process.env.TAURI_DEV_HOST

export default defineConfig({
  plugins: [vue()],
  // Tauri 要求固定端口
  clearScreen: false,
  envPrefix: ['VITE_', 'TAURI_'],
  server: {
    port: 5173,
    strictPort: true,
    host: host || false,
    hmr: host
      ? {
          protocol: 'ws',
          host,
          port: 5174,
        }
      : undefined,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  },
  build: {
    // Tauri 使用 Chromium (Windows) 和 WebKit (macOS/Linux)
    target: process.env.TAURI_PLATFORM === 'windows' ? 'chrome105' : 'es2020',
    minify: !process.env.TAURI_DEBUG ? 'esbuild' : false,
    sourcemap: !!process.env.TAURI_DEBUG,
    // 代码分割优化
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules/element-plus')) return 'element-plus'
          if (id.includes('node_modules/echarts')) return 'echarts'
          if (id.includes('node_modules/mermaid')) return 'mermaid'
          if (id.includes('node_modules/katex')) return 'katex'
          if (id.includes('node_modules/marked')) return 'marked'
        },
      },
    },
    // chunk 大小警告阈值
    chunkSizeWarningLimit: 1000,
  },
})
