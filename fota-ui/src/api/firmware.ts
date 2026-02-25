import { del, get, post, put } from '@/api/request'

export interface PageResult<T> {
  records: T[]
  page: number
  size: number
  total: number
  pages: number
}

export interface FirmwareVersionItem {
  id: number
  productId: number
  productName?: string
  version: string
  fileUrl: string
  fileSize: number
  md5: string
  sha256: string
  tags?: string
  meta?: string
  createdAt: string
  createdBy: number
  updatedAt: string
  updatedBy: number
}

export interface FirmwareVersionPayload {
  productId: number
  version: string
  fileUrl: string
  fileSize: number
  md5: string
  sha256: string
  tags?: string
  meta?: string
}

export const pageFirmwareVersions = (params: Record<string, unknown>) => {
  return get<PageResult<FirmwareVersionItem>>('/admin/firmware-versions', { params })
}

export const getFirmwareVersionsByProduct = (productId: number) => {
  return get<FirmwareVersionItem[]>(`/admin/firmware-versions/by-product/${productId}`)
}

export const getFirmwareVersionById = (id: number) => {
  return get<FirmwareVersionItem>(`/admin/firmware-versions/${id}`)
}

export const createFirmwareVersion = (payload: FirmwareVersionPayload) => {
  return post<FirmwareVersionItem>('/admin/firmware-versions', payload)
}

export const updateFirmwareVersion = (id: number, payload: FirmwareVersionPayload) => {
  return put<FirmwareVersionItem>(`/admin/firmware-versions/${id}`, payload)
}

export const deleteFirmwareVersion = (id: number) => {
  return del<void>(`/admin/firmware-versions/${id}`)
}
