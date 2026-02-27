import { del, get, post, put } from '@/api/request'

export interface PageResult<T> {
  records: T[]
  page: number
  size: number
  total: number
  pages: number
}

export type PolicyStatus = 'DRAFT' | 'TESTING' | 'VERIFIED' | 'ACTIVE' | 'PAUSED' | 'EXPIRED'
export type TriggerMode = 'AUTO' | 'MANUAL' | 'BOTH'
export type TargetMode = 'ALL' | 'DEVICE_IDS' | 'DEVICE_BATCHES' | 'DEVICE_TAGS'
export type TimeWindowType = 'UNLIMITED' | 'RANGE' | 'DAILY'

export interface TimeWindowDTO {
  type: TimeWindowType
  startAt: string  // ISO8601 UTC 或空字符串（UNLIMITED 类型）
  endAt: string    // ISO8601 UTC 或空字符串（UNLIMITED 类型）
}

export interface UpgradePolicyItem {
  id: number
  productId: number
  firmwareVersionId: number
  name: string
  grayRate: number
  priority: number
  triggerMode: TriggerMode
  timeWindow?: TimeWindowDTO
  sourceVersions: number[]  // 后端返回版本 ID 数组
  targetMode: TargetMode
  targetDeviceIds?: string[]
  targetDeviceBatchIds?: string[]
  targetDeviceTags?: Record<string, unknown>
  status: PolicyStatus
  remark?: string
  createdAt: string
  createdBy: number
  createdByName?: string
  updatedAt: string
  updatedBy: number
  updatedByName?: string
  // 扩展字段（用于列表展示）
  productName?: string
  firmwareVersion?: string
  // 源版本ID到版本号的映射
  sourceVersionNames?: Record<number, string>
}

export interface UpgradePolicyPayload {
  productId: number
  firmwareVersionId: number
  name: string
  grayRate: number
  priority: number
  triggerMode: TriggerMode
  timeWindow: TimeWindowDTO
  sourceVersions: number[]
  targetMode: TargetMode
  targetDeviceIds?: string[]
  targetDeviceBatchIds?: string[]
  targetDeviceTags?: Record<string, unknown>
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

/**
 * 更新策略状态（允许任意状态切换）
 */
export const updatePolicyStatus = (id: number, status: PolicyStatus) => {
  return put<UpgradePolicyItem>(`/admin/policies/${id}/status`, { status })
}

// 便捷的状态切换方法
export const startTestPolicy = (id: number) => {
  return updatePolicyStatus(id, 'TESTING')
}

export const verifyPolicy = (id: number) => {
  return updatePolicyStatus(id, 'VERIFIED')
}

export const releasePolicy = (id: number) => {
  return updatePolicyStatus(id, 'ACTIVE')
}

export const pausePolicy = (id: number) => {
  return updatePolicyStatus(id, 'PAUSED')
}

export const resumePolicy = (id: number) => {
  return updatePolicyStatus(id, 'ACTIVE')
}
