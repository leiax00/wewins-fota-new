import { del, get, post, put } from '@/api/request'

export interface PageResult<T> {
  records: T[]
  page: number
  size: number
  total: number
  pages: number
}

export type PolicyStatus = 'ACTIVE' | 'PAUSED' | 'EXPIRED'

export interface UpgradePolicyItem {
  id: number
  productId: number
  firmwareVersionId: number
  name: string
  grayRate: number
  priority: number
  planTime?: string
  status: PolicyStatus
  remark?: string
  createdAt: string
  createdBy: number
  updatedAt: string
  updatedBy: number
}

export interface UpgradePolicyPayload {
  productId: number
  firmwareVersionId: number
  name: string
  grayRate: number
  priority: number
  planTime?: string
  status: PolicyStatus
  remark?: string
}

export const pagePolicies = (params: Record<string, unknown>) => {
  return get<PageResult<UpgradePolicyItem>>('/admin/policies', { params })
}

export const getPolicyById = (id: number) => {
  return get<UpgradePolicyItem>(`/admin/policies/${id}`)
}

export const createPolicy = (payload: UpgradePolicyPayload) => {
  return post<UpgradePolicyItem>('/admin/policies', payload)
}

export const updatePolicy = (id: number, payload: UpgradePolicyPayload) => {
  return put<UpgradePolicyItem>(`/admin/policies/${id}`, payload)
}

export const deletePolicy = (id: number) => {
  return del<void>(`/admin/policies/${id}`)
}
