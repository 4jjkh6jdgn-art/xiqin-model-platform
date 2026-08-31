import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

// 支持通过构建时环境变量指定后端地址（Render等PaaS平台部署用）
// VITE_API_BASE_URL 可以是完整URL（https://xxx.onrender.com/api）或主机名（xxx.onrender.com）
const rawBase = import.meta.env.VITE_API_BASE_URL as string | undefined
let apiBaseUrl = '/api'
if (rawBase && rawBase.trim()) {
  const trimmed = rawBase.trim().replace(/\/+$/, '')
  if (trimmed.startsWith('http')) {
    // 完整URL：确保以 /api 结尾（后端 context-path 为 /api）
    apiBaseUrl = trimmed.endsWith('/api') ? trimmed : `${trimmed}/api`
  } else {
    apiBaseUrl = `https://${trimmed}/api`
  }
}

const api = axios.create({
  baseURL: apiBaseUrl,
  timeout: 60000,
})

// Request interceptor - add JWT
api.interceptors.request.use(config => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
}, error => Promise.reject(error))

// Response interceptor - handle errors
api.interceptors.response.use(
  response => {
    // Binary download/preview responses are not wrapped in the application's
    // { code, message, data } JSON envelope.
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response
    }
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || 'Request failed')
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  error => {
    if (error.response?.status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      window.location.href = '/login'
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(error.response?.data?.message || error.message || 'Network error')
    }
    return Promise.reject(error)
  }
)

export default api
