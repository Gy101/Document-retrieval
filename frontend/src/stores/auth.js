import { defineStore } from 'pinia'
import { loginApi, meApi, logoutApi, registerApi } from '@/api/auth'
import { TOKEN_KEY } from '@/api/request'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('dc_user') || 'null')
  } catch (e) {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser()
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    displayName: (state) => state.user?.displayName || state.user?.username || '用户',
    isAdmin: (state) => state.user?.role === 'ADMIN'
  },
  actions: {
    persistSession(data) {
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem('dc_user', JSON.stringify(data.user))
    },
    async login(payload) {
      const data = await loginApi(payload)
      this.persistSession(data)
      return data
    },
    async register(payload) {
      const data = await registerApi(payload)
      this.persistSession(data)
      return data
    },
    async fetchMe() {
      const user = await meApi()
      this.user = user
      localStorage.setItem('dc_user', JSON.stringify(user))
      return user
    },
    setUser(user) {
      this.user = user
      localStorage.setItem('dc_user', JSON.stringify(user))
    },
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 即使服务端退出失败，本地也必须清理
      }
      this.clear()
    },
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('dc_user')
    }
  }
})
