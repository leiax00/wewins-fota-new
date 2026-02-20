import { defineStore } from 'pinia'
import { post, get } from '@/api/request'
import router from '@/router'
import { useAppStore } from '@/stores/app'
import { getToken, setToken, clearToken } from '@/utils/auth'

export interface UserInfo {
  id: number
  username: string
  displayName: string
  email: string
  phone: string
  status: string
  roles: string[]
  permissions: string[]
}

interface LoginParams {
  username: string
  password: string
}

interface LoginResult {
  token: string
  user: UserInfo
}

interface ClearAuthOptions {
  redirect?: boolean
  redirectPath?: string
}

const enableFrontendPermissionCheck = import.meta.env.VITE_ENABLE_FRONTEND_PERMISSION === 'true'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    userInfo: null as UserInfo | null,
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.displayName || state.userInfo?.username || '',
    permissions: (state) => state.userInfo?.permissions || [],
  },

  actions: {
    async login(params: LoginParams, remember = false) {
      const result = await post<LoginResult>('/sys/auth/login', params)
      this.token = result.token
      this.userInfo = result.user
      setToken(result.token, remember)
      return result
    },

    async fetchUserInfo() {
      const user = await get<UserInfo>('/sys/auth/current')
      this.userInfo = user
      return user
    },

    async logout() {
      try {
        await post('/sys/auth/logout')
      } catch {
        // ignore
      } finally {
        this.clearAuth({ redirect: true })
      }
    },

    clearAuth(options: ClearAuthOptions = {}) {
      const { redirect = true, redirectPath = '' } = options
      this.token = ''
      this.userInfo = null
      clearToken()
      useAppStore().clearTabState()

      if (!redirect) return

      const currentRoute = router.currentRoute.value
      const query = redirectPath ? { redirect: redirectPath } : undefined

      if (currentRoute.path === '/login') return
      router.push({ path: '/login', query })
    },

    hasPermission(permission: string): boolean {
      if (!enableFrontendPermissionCheck) {
        return true
      }
      return this.permissions.includes(permission) || this.permissions.includes('*')
    },
  },
})
