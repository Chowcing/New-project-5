import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const deploymentVersion =
    env.EXPENSE_DEPLOYMENT_VERSION || env.VITE_EXPENSE_DEPLOYMENT_VERSION || 'local-dev'

  return {
    plugins: [vue()],
    define: {
      'import.meta.env.VITE_EXPENSE_DEPLOYMENT_VERSION': JSON.stringify(deploymentVersion)
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        }
      }
    }
  }
})
