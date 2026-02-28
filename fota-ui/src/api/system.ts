import { del, get, post, put } from '@/api/request'
import type { MenuDTO } from '@/stores/user'

export interface PageResult<T> {
  records: T[]
  page: number
  size: number
  total: number
  pages: number
}

export interface UserItem {
  id: number
  username: string
  displayName: string
  email: string
  phone: string
  status: string
  createdAt: string
}

export interface UserPayload {
  username: string
  displayName: string
  email: string
  phone: string
  status: string
  passwordHash?: string
}

export interface RoleItem {
  id: number
  code: string
  name: string
  description: string
  status: string
  createdAt: string
}

export interface RolePayload {
  code: string
  name: string
  description: string
  status: string
}

export interface PermissionItem {
  id: number
  code: string
  name: string
  type: string
  path: string
  method: string
  parentId: number | null
  status: string
  createdAt: string
  // 菜单路由字段
  routePath?: string | null
  routeName?: string | null
  componentKey?: string | null
  redirectPath?: string | null
  // 菜单显示字段
  i18nKey?: string | null
  icon?: string | null
  menuSort?: number | null
  menuVisible?: boolean | null
  breadcrumbVisible?: boolean | null
  tabVisible?: boolean | null
  tabClosable?: boolean | null
  affixTab?: boolean | null
  keepAlive?: boolean | null
  alwaysShow?: boolean | null
  // 菜单高级字段
  activeMenu?: string | null
  externalLink?: string | null
  openMode?: string | null
}

export interface PermissionPayload {
  code: string
  name: string
  type: string
  path: string
  method: string
  parentId?: number | null
  status: string
  // 菜单路由字段
  routePath?: string | null
  routeName?: string | null
  componentKey?: string | null
  redirectPath?: string | null
  // 菜单显示字段
  i18nKey?: string | null
  icon?: string | null
  menuSort?: number | null
  menuVisible?: boolean | null
  breadcrumbVisible?: boolean | null
  tabVisible?: boolean | null
  tabClosable?: boolean | null
  affixTab?: boolean | null
  keepAlive?: boolean | null
  alwaysShow?: boolean | null
  // 菜单高级字段
  activeMenu?: string | null
  externalLink?: string | null
  openMode?: string | null
}

export interface PermissionTreeNode {
  permission: PermissionItem
  children: PermissionTreeNode[]
}

export interface DictTypeItem {
  id: number
  code: string
  name: string
  i18nKey: string
  status: string
  description: string
  createdAt: string
}

export interface DictTypePayload {
  code: string
  name: string
  i18nKey: string
  status: string
  description: string
}

export interface DictItem {
  id: number
  dictTypeId: number
  label: string
  value: string
  i18nKey: string
  sortOrder: number
  status: string
  extra?: unknown | null
  createdAt: string
}

export interface DictItemPayload {
  dictTypeId: number
  label: string
  value: string
  i18nKey: string
  sortOrder: number
  status: string
  extra?: unknown | null
}

export const getUserMenu = () => {
  return get<MenuDTO[]>('/sys/auth/user-menu')
}

export const pageUsers = (params: Record<string, unknown>) => {
  return get<PageResult<UserItem>>('/sys/users', { params })
}

export const createUser = (payload: UserPayload) => {
  return post<UserItem>('/sys/users', payload)
}

export const updateUser = (id: number, payload: UserPayload) => {
  return put<UserItem>(`/sys/users/${id}`, payload)
}

export const deleteUser = (id: number) => {
  return del<void>(`/sys/users/${id}`)
}

export const listUserRoles = (id: number) => {
  return get<RoleItem[]>(`/sys/users/${id}/roles`)
}

export const assignUserRoles = (id: number, roleIds: number[]) => {
  return post<void>(`/sys/users/${id}/roles`, roleIds)
}

export const pageRoles = (params: Record<string, unknown>) => {
  return get<PageResult<RoleItem>>('/sys/roles', { params })
}

export const createRole = (payload: RolePayload) => {
  return post<RoleItem>('/sys/roles', payload)
}

export const updateRole = (id: number, payload: RolePayload) => {
  return put<RoleItem>(`/sys/roles/${id}`, payload)
}

export const deleteRole = (id: number) => {
  return del<void>(`/sys/roles/${id}`)
}

export const listRolePermissions = (id: number) => {
  return get<PermissionItem[]>(`/sys/roles/${id}/permissions`)
}

export const assignRolePermissions = (id: number, permissionIds: number[]) => {
  return post<void>(`/sys/roles/${id}/permissions`, permissionIds)
}

export const pagePermissions = (params: Record<string, unknown>) => {
  return get<PageResult<PermissionItem>>('/sys/permissions', { params })
}

export const createPermission = (payload: PermissionPayload) => {
  return post<PermissionItem>('/sys/permissions', payload)
}

export const updatePermission = (id: number, payload: PermissionPayload) => {
  return put<PermissionItem>(`/sys/permissions/${id}`, payload)
}

export const deletePermission = (id: number) => {
  return del<void>(`/sys/permissions/${id}`)
}

export const listPermissionTree = () => {
  return get<PermissionTreeNode[]>('/sys/permissions/tree')
}

export const pageDictTypes = (params: Record<string, unknown>) => {
  return get<PageResult<DictTypeItem>>('/sys/dict-types', { params })
}

export const getDictTypeById = (id: number) => {
  return get<DictTypeItem>(`/sys/dict-types/${id}`)
}

export const createDictType = (payload: DictTypePayload) => {
  return post<DictTypeItem>('/sys/dict-types', payload)
}

export const updateDictType = (id: number, payload: DictTypePayload) => {
  return put<DictTypeItem>(`/sys/dict-types/${id}`, payload)
}

export const deleteDictType = (id: number) => {
  return del<void>(`/sys/dict-types/${id}`)
}

export const pageDictItems = (params: Record<string, unknown>) => {
  return get<PageResult<DictItem>>('/sys/dict-items', { params })
}

export const createDictItem = (payload: DictItemPayload) => {
  return post<DictItem>('/sys/dict-items', payload)
}

export const updateDictItem = (id: number, payload: DictItemPayload) => {
  return put<DictItem>(`/sys/dict-items/${id}`, payload)
}

export const deleteDictItem = (id: number) => {
  return del<void>(`/sys/dict-items/${id}`)
}
