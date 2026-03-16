export type ComponentKey =
  | 'dashboard/index'
  | 'monitor/index'
  | 'monitor/load'
  | 'monitor/cache'
  | 'monitor/log'
  | 'monitor/operation-log'
  | 'product/index'
  | 'firmware/index'
  | 'policy/index'
  | 'device/index'
  | 'device/detail'
  | 'system/user/index'
  | 'system/role/index'
  | 'system/permission/index'
  | 'system/dict/index'
  | 'system/dict-items/index'

export type ComponentLoader = () => Promise<unknown>

export const componentMap: Record<ComponentKey, ComponentLoader> = {
  'dashboard/index': () => import('@/views/dashboard/DashboardView.vue'),
  'monitor/index': () => import('@/views/monitor/MonitorView.vue'),
  'monitor/load': () => import('@/views/monitor/LoadMonitorView.vue'),
  'monitor/cache': () => import('@/views/monitor/CacheMonitorView.vue'),
  'monitor/log': () => import('@/views/monitor/LogMonitorView.vue'),
  'monitor/operation-log': () => import('@/views/monitor/OperationLogView.vue'),
  'product/index': () => import('@/views/product/ProductListView.vue'),
  'firmware/index': () => import('@/views/firmware/FirmwareListView.vue'),
  'policy/index': () => import('@/views/policy/PolicyListView.vue'),
  'device/index': () => import('@/views/device/DeviceListView.vue'),
  'device/detail': () => import('@/views/device/DeviceDetailView.vue'),
  'system/user/index': () => import('@/views/system/UserListView.vue'),
  'system/role/index': () => import('@/views/system/RoleListView.vue'),
  'system/permission/index': () => import('@/views/system/PermissionView.vue'),
  'system/dict/index': () => import('@/views/system/DictView.vue'),
  'system/dict-items/index': () => import('@/views/system/DictItemListView.vue'),
}

export function isValidComponentKey(key: string): key is ComponentKey {
  return Object.prototype.hasOwnProperty.call(componentMap, key)
}

export function isLayout(key: string): boolean {
  return  key.toLowerCase() === 'layout'
}
