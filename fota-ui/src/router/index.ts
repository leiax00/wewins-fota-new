import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

interface BreadcrumbMetaItem {
  titleKey?: string
  title?: string
  path?: string
}

declare module 'vue-router' {
  interface RouteMeta {
    titleKey?: string
    title?: string
    requiresAuth?: boolean
    hidden?: boolean
    icon?: string
    permission?: string | string[]
    affix?: boolean
    tabClosable?: boolean
    tabHidden?: boolean
    breadcrumbHidden?: boolean
    breadcrumb?: BreadcrumbMetaItem[]
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { titleKey: 'login.login', requiresAuth: false },
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: {
      titleKey: 'menu.dashboard',
      icon: 'Odometer',
      permission: 'dashboard:view',
      affix: true,
      tabClosable: false,
    },
  },
  {
    path: '/product',
    name: 'Product',
    component: () => import('@/views/product/ProductListView.vue'),
    meta: { titleKey: 'menu.product', icon: 'Box', permission: 'product:read' },
  },
  {
    path: '/firmware',
    name: 'Firmware',
    component: () => import('@/views/firmware/FirmwareListView.vue'),
    meta: { titleKey: 'menu.firmware', icon: 'Cpu', permission: 'firmware:read' },
  },
  {
    path: '/policy',
    name: 'Policy',
    component: () => import('@/views/policy/PolicyListView.vue'),
    meta: { titleKey: 'menu.policy', icon: 'Document', permission: 'policy:read' },
  },
  {
    path: '/device',
    name: 'Device',
    component: () => import('@/views/device/DeviceListView.vue'),
    meta: { titleKey: 'menu.device', icon: 'Iphone', permission: 'device:read' },
  },
  {
    path: '/device/:id',
    name: 'DeviceDetail',
    component: () => import('@/views/device/DeviceDetailView.vue'),
    meta: {
      titleKey: 'device.detail',
      hidden: true,
      permission: 'device:detail',
      breadcrumb: [{ titleKey: 'menu.device', path: '/device' }],
    },
  },
  {
    path: '/system/user',
    name: 'SystemUser',
    component: () => import('@/views/system/UserListView.vue'),
    meta: {
      titleKey: 'menu.user',
      icon: 'User',
      permission: 'sys:user:read',
      breadcrumb: [{ titleKey: 'menu.system' }],
    },
  },
  {
    path: '/system/role',
    name: 'SystemRole',
    component: () => import('@/views/system/RoleListView.vue'),
    meta: {
      titleKey: 'menu.role',
      icon: 'UserFilled',
      permission: 'sys:role:read',
      breadcrumb: [{ titleKey: 'menu.system' }],
    },
  },
  {
    path: '/system/permission',
    name: 'SystemPermission',
    component: () => import('@/views/system/PermissionView.vue'),
    meta: {
      titleKey: 'menu.permission',
      icon: 'Lock',
      permission: 'sys:perm:read',
      breadcrumb: [{ titleKey: 'menu.system' }],
    },
  },
  {
    path: '/system/dict',
    name: 'SystemDict',
    component: () => import('@/views/system/DictView.vue'),
    meta: {
      titleKey: 'menu.dict',
      icon: 'Collection',
      permission: ['sys:dict_type:read', 'sys:dict_item:read'],
      breadcrumb: [{ titleKey: 'menu.system' }],
    },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '404', hidden: true, tabHidden: true, breadcrumbHidden: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const resolveFirstAccessiblePath = (userStore: ReturnType<typeof useUserStore>): string => {
  const candidates: Array<{ path: string; permission?: string | string[] }> = [
    { path: '/dashboard', permission: 'dashboard:view' },
    { path: '/product', permission: 'product:read' },
    { path: '/firmware', permission: 'firmware:read' },
    { path: '/policy', permission: 'policy:read' },
    { path: '/device', permission: 'device:read' },
    { path: '/system/user', permission: 'sys:user:read' },
    { path: '/system/role', permission: 'sys:role:read' },
    { path: '/system/permission', permission: 'sys:perm:read' },
    { path: '/system/dict', permission: ['sys:dict_type:read', 'sys:dict_item:read'] },
  ]

  for (const candidate of candidates) {
    if (!candidate.permission) return candidate.path
    const requiredList = Array.isArray(candidate.permission)
      ? candidate.permission
      : [candidate.permission]
    const hasAnyPermission = requiredList.some((permission) =>
      userStore.hasPermission(permission)
    )
    if (hasAnyPermission) return candidate.path
  }

  return '/login'
}

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const token = userStore.token || ''
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

      const fallbackPath = resolveFirstAccessiblePath(userStore)
      if (fallbackPath !== '/login' && fallbackPath !== to.path) {
        return fallbackPath
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

  const requiredPermission = to.meta.permission as string | string[] | undefined
  if (requiredPermission) {
    const requiredList = Array.isArray(requiredPermission) ? requiredPermission : [requiredPermission]
    const hasAnyPermission = requiredList.some((permission) => userStore.hasPermission(permission))
    if (!hasAnyPermission) {
      const fallbackPath = resolveFirstAccessiblePath(userStore)
      if (fallbackPath === to.path) return '/login'
      return fallbackPath
    }
  }

  if (isLoginRoute) {
    return '/dashboard'
  }

  return true
})

export default router
