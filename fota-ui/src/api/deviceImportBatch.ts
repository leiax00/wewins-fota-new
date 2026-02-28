import { get } from '@/api/request'

export interface PageResult<T> {
  records: T[]
  page: number
  size: number
  total: number
  pages: number
}

export type BatchStatus = 'IMPORTING' | 'SUCCESS' | 'FAILED' | 'PARTIAL'

export interface DeviceImportBatchItem {
  id: number
  batchName: string
  productId?: number
  productName?: string
  status: BatchStatus
  totalCount: number
  successCount: number
  failedCount: number
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  createdAt: string
  createdBy: number
  updatedAt: string
  updatedBy: number
}

export interface DeviceImportBatchPageParams {
  batchName?: string
  productId?: number
  status?: BatchStatus
  page?: number
  size?: number
}

/**
 * 分页查询设备导入批次列表
 */
export const pageBatches = (params: DeviceImportBatchPageParams) => {
  return get<PageResult<DeviceImportBatchItem>>('/admin/device-import-batches', { params })
}

/**
 * 获取批次详情
 */
export const getBatchById = (id: number) => {
  return get<DeviceImportBatchItem>(`/admin/device-import-batches/${id}`)
}

/**
 * 获取批次下的设备列表
 */
export const getBatchDevices = (batchId: number, params: {
  page?: number
  size?: number
}) => {
  return get<PageResult<DeviceItem>>(`/admin/device-import-batches/${batchId}/devices`, { params })
}

/**
 * 设备项类型（从device.ts复用）
 */
export interface DeviceItem {
  id: number
  imei: string
  productId: number
  productName?: string
  currentVersionId?: number
  versionName?: string
  status: 'ONLINE' | 'OFFLINE' | 'LOST'
  lastSeenAt?: string
  tags?: string
  importBatchId?: number
  createdAt: string
  createdBy: number
  updatedAt: string
  updatedBy: number
}
