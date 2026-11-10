import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'dc_token'

const service = axios.create({
  baseURL: '/api',
  timeout: 60000
})

service.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const body = response.data
    if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'code')) {
      if (body.code === 0) {
        return body.data
      }
      const message = body.message || '请求失败'
      if (!response.config?.silent) {
        ElMessage.error(message)
      }
      return Promise.reject(new Error(message))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络异常，请稍后重试'
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('dc_user')
      const authPage = window.location.pathname.startsWith('/login') || window.location.pathname.startsWith('/register')
      if (!authPage) {
        window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
      }
      ElMessage.warning('登录状态已失效，请重新登录')
    } else {
      if (!error.config?.silent) {
        ElMessage.error(message)
      }
    }
    return Promise.reject(error)
  }
)

export { TOKEN_KEY }
export default service
