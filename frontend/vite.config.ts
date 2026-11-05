import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// 开发期把 /api 代理到后端服务器，避免跨域，也让会话 Cookie 与 CSRF 双提交在同源下正常工作
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://10.171.130.29:8080',
        changeOrigin: true,
      },
    },
  },
})
