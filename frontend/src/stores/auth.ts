import { defineStore } from 'pinia'
import api from '@/api'

interface UserInfo {
  id: number
  username: string
  email: string
  phone: string
  avatar: string
  roleName: string
  roleCode: string
  roleId: number
  status: number
  permissions: string[]
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('xiqin_token') || '',
    user: JSON.parse(localStorage.getItem('xiqin_user') || 'null') as UserInfo | null,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.user?.roleCode === 'admin',
    isLeader: (state) => state.user?.roleCode === 'leader' || state.user?.roleCode === 'admin',
    hasPermission: (state) => (perm: string) => state.user?.roleCode === 'admin' || state.user?.permissions?.includes(perm) || false,
  },
  actions: {
    async login(username: string, password: string) {
      const res = await api.post('/auth/login', { username, password })
      this.token = res.data.token
      this.user = {
        id: res.data.userId,
        username: res.data.username,
        email: res.data.email,
        phone: '',
        avatar: res.data.avatar,
        roleName: res.data.roleName,
        roleCode: res.data.roleCode,
        roleId: 0,
        status: 1,
        permissions: res.data.permissions || []
      }
      localStorage.setItem('xiqin_token', this.token)
      localStorage.setItem('xiqin_user', JSON.stringify(this.user))
    },
    async fetchCurrentUser() {
      const res = await api.get('/auth/me')
      this.user = res.data
      localStorage.setItem('xiqin_user', JSON.stringify(this.user))
    },
    async register(data: { username: string; password: string; email?: string; phone?: string; invitationCode?: string }) {
      const res = await api.post('/auth/register', data)
      return res.data
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('xiqin_token')
      localStorage.removeItem('xiqin_user')
    }
  }
})
