import { del, get, post, put } from '@/api/request'

export interface PageResult<T> {
  records: T[]
  page: number
  size: number
  total: number
  pages: number
}

export type DeviceStatus = 'ONLINE' | 'OFFLINE' | 'LOST'

export interface DeviceItem {
  id: number
  imei: string
  productId: number
  productName?: string
  currentVersionId?: number
  versionName?: string
  status: DeviceStatus
  lastSeenAt?: string
  tags?: string
  importBatchId?: number
  createdAt: string
  createdBy: number
  updatedAt: string
  updatedBy: number
}

export interface DevicePayload {
  imei: string
  productId: number
  currentVersionId?: number
  status: DeviceStatus
  tags?: string
}

export const pageDevices = (params: Record<string, unknown>) => {
  return get<PageResult<DeviceItem>>('/api/admin/devices', { params })
}

export const getDeviceById = (id: number) => {
  return get<DeviceItem>(`/api/admin/devices/${id}`)
}

export const createDevice = (payload: DevicePayload) => {
  return post<DeviceItem>('/api/admin/devices', payload)
}

export const updateDevice = (id: number, payload: DevicePayload) => {
  return put<DeviceItem>(`/api/admin/devices/${id}`, payload)
}

export const deleteDevice = (id: number) => {
  return del<void>(`/api/admin/devices/${id}`)
}
