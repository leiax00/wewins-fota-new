import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { convertMenusToRoutes } from '@/router/menu-converter'
import { useUserStore } from '@/stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    // 国际化
    i18nKey?: string
    titleKey?: string  // 别名，兼容静态路由
    title?: string

    // 权限控制
    permission?: string | string[]
    requiresAuth?: boolean

    // 显示控制
    hidden?: boolean
    icon?: string

    // 页面缓存
    keepAlive?: boolean
    affix?: boolean

    // 菜单行为
    alwaysShow?: boolean
    activeMenu?: string

    // 标签页控制
    tabClosable?: boolean
    tabHidden?: boolean

    // 面包屑控制
    breadcrumbHidden?: boolean

    // 排序
    sort?: number
  }
}

// 基础路由（登录、403、404）
const baseRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { titleKey: 'login.login', requiresAuth: false },
  },
  {
    path: '/policy/create',
    name: 'policy-create',
    component: () => import('@/views/policy/PolicyFormView.vue'),
    meta: {
      i18nKey: 'policy.createTitle',
      permission: 'fota:policy:create',
      activeMenu: '/policy',
      hidden: true,
      tabHidden: true,
      breadcrumbHidden: false
    },
  },
  {
    path: '/policy/edit/:id(\\d+)',
    name: 'policy-edit',
    component: () => import('@/views/policy/PolicyFormView.vue'),
    meta: {
      i18nKey: 'policy.editTitle',
      permission: 'fota:policy:update',
      activeMenu: '/policy',
      hidden: true,
      tabHidden: true,
      breadcrumbHidden: false
    },
  },
  {
    path: '/forbidden',
    name: 'forbidden',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: {
      title: '403',
      hidden: true,
      tabHidden: true,
      breadcrumbHidden: true,
    },
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '404', hidden: true, tabHidden: true, breadcrumbHidden: true },
  },
]

const routes: RouteRecordRaw[] = [...baseRoutes]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const dynamicMenuEnabled = import.meta.env.VITE_DYNAMIC_MENU === 'true'
let dynamicRoutesInjected = false

const getFirstMenuPath = (menus: Array<{ path?: string; children?: Array<any> }>): string | null => {
  for (const menu of menus || []) {
    if (menu.path) return menu.path
    const child = getFirstMenuPath(menu.children || [])
    if (child) return child
  }
  return null
}

const ensureDynamicRoutesInjected = async (userStore: ReturnType<typeof useUserStore>) => {
  if (!dynamicMenuEnabled || dynamicRoutesInjected) return

  await userStore.ensureDynamicRoutes(router)
  const dynamicRoutes = convertMenusToRoutes(userStore.menuTree)

  for (const route of dynamicRoutes) {
    if (route.name && router.hasRoute(route.name)) {
      continue
    }
    router.addRoute(route)
  }

  dynamicRoutesInjected = true
}

const hasRoutePermission = (required: string | string[] | undefined, userStore: ReturnType<typeof useUserStore>) => {
  if (!required) return true
  const requiredList = Array.isArray(required) ? required : [required]
  return requiredList.some((permission) => userStore.hasPermission(permission))
}

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const token = userStore.token
  const isLoginRoute = to.path === '/login'
  const isPublicRoute = to.meta.requiresAuth === false

  if (isPublicRoute) {
    if (token && isLoginRoute) {
      if (!userStore.userInfo) {
        try {
          await userStore.fetchUserInfo()
        } catch {
          userStore.clearAuth({ redirect: false })
          return true
        }
      }

      await ensureDynamicRoutesInjected(userStore)

      const fallbackPath = getFirstMenuPath(userStore.menuTree)
      if (fallbackPath && fallbackPath !== to.path) {
        return { path: fallbackPath }
      }

      return true
    }

    return true
  }

  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      userStore.clearAuth({ redirect: false })
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  if (dynamicMenuEnabled && !dynamicRoutesInjected) {
    await ensureDynamicRoutesInjected(userStore)
    return to.fullPath
  }

  if (isLoginRoute) {
    const fallbackPath = getFirstMenuPath(userStore.menuTree)
    return fallbackPath || '/dashboard'
  }

  const requiredPermission = to.meta.permission as string | string[] | undefined
  if (!hasRoutePermission(requiredPermission, userStore)) {
    return '/forbidden'
  }

  return true
})

router.afterEach((to) => {
  if (to.path === '/login') {
    dynamicRoutesInjected = false
  }
})

export default router
