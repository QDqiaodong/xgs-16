import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      host: '127.0.0.1',
      port: 3016,
      strictPort: true,
      open: false,
      proxy: {
        '/api': {
          target: 'http://127.0.0.1:8016',
          changeOrigin: true
        }
      }
    },
    preview: {
      host: '127.0.0.1',
      port: 3016,
      strictPort: true
    },
    css: {
      preprocessorOptions: {
        scss: {
          api: 'modern-compiler'
        }
      }
    },
    build: {
      outDir: 'dist',
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          manualChunks: {
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
            'echarts': ['echarts', 'vue-echarts'],
            'vue-vendor': ['vue', 'vue-router', 'pinia', 'axios']
          }
        }
      }
    }
  }
})
