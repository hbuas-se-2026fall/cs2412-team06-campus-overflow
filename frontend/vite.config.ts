import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// 开发期把 /api 代理到后端，避免跨域，也让会话 Cookie 与 CSRF 双提交在同源下正常工作
// 代理目标通过 VITE_PROXY_TARGET 环境变量注入，默认 localhost:8080
// 演示环境在 .env.local 里写 VITE_PROXY_TARGET=http://10.171.130.29:8080
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
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
