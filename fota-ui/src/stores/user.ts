import { defineStore } from 'pinia'
import { post, get } from '@/api/request'
import router from '@/router'
import { useAppStore } from '@/stores/app'
import { getToken, setToken, clearToken } from '@/utils/auth'
import type { Router } from 'vue-router'

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

export interface RouteMetaDTO {
  i18nKey?: string
  icon?: string
  hidden?: boolean
  keepAlive?: boolean
  affix?: boolean
  alwaysShow?: boolean
  breadcrumbHidden?: boolean
  tabHidden?: boolean
  tabClosable?: boolean
  activeMenu?: string
  permission?: string
  externalLink?: { url: string; openMode?: string }
}

export interface MenuDTO {
  path: string
  name: string
  componentKey?: string
  redirect?: string
  meta: RouteMetaDTO
  sort?: number
  children?: MenuDTO[]
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
    menus: [] as MenuDTO[],
    dynamicRoutesInited: false,
    dynamicRouteNames: [] as string[], // 记录已注入的动态路由名称
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.displayName || state.userInfo?.username || '',
    permissions: (state) => state.userInfo?.permissions || [],
    menuTree: (state) => state.menus,
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

    async fetchUserMenu() {
      const { getUserMenu } = await import('@/api/system')
      const menus = await getUserMenu()
      this.menus = menus || []
      return this.menus
    },

    async ensureDynamicRoutes(router: Router) {
      if (this.dynamicRoutesInited) return
      if (!this.menus.length) {
        await this.fetchUserMenu()
      }

      // 导入菜单转换工具
      const { convertMenusToRoutes } = await import('@/router/menu-converter')
      const dynamicRoutes = convertMenusToRoutes(this.menus)

      // 注入动态路由并记录名称
      for (const route of dynamicRoutes) {
        if (route.name && !router.hasRoute(route.name)) {
          router.addRoute(route)
          this.dynamicRouteNames.push(String(route.name))
        }
      }

      this.dynamicRoutesInited = true
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
      this.menus = []
      this.dynamicRoutesInited = false

      // 移除所有动态路由
      for (const routeName of this.dynamicRouteNames) {
        if (router.hasRoute(routeName)) {
          router.removeRoute(routeName)
        }
      }
      this.dynamicRouteNames = []

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
