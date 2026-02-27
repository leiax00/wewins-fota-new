import { del, get, post, put } from '@/api/request'

export type DeviceStatus = 'ONLINE' | 'OFFLINE' | 'LOST'

export type BatchOperationType =
  | 'DELETE_BY_BATCH'
  | 'UPDATE_TAG_BY_BATCH'
  | 'UPDATE_TAG_BY_IMEI'
  | 'UPDATE_BATCH_BY_IMEI'

export interface PageResult<T> {
  records: T[]
  page: number
  size: number
  total: number
  pages: number
}

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
  importBatchName?: string
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

export interface DeviceImportParams {
  file: File
  productId: number
  batchName?: string
}

export interface DeviceImportResult {
  batchId: number
  batchName: string
  status: 'IMPORTING' | 'SUCCESS' | 'FAILED' | 'PARTIAL'
  totalCount: number
  successCount: number
  failedCount: number
  errorMessage?: string
}

export const pageDevices = (params: Record<string, unknown>) => {
  return get<PageResult<DeviceItem>>('/admin/devices', { params })
}

export const getDeviceById = (id: number) => {
  return get<DeviceItem>(`/admin/devices/${id}`)
}

export const createDevice = (payload: DevicePayload) => {
  return post<DeviceItem>('/admin/devices', payload)
}

export const updateDevice = (id: number, payload: DevicePayload) => {
  return put<DeviceItem>(`/admin/devices/${id}`, payload)
}

export const deleteDevice = (id: number) => {
  return del<void>(`/admin/devices/${id}`)
}

/**
 * 批量导入设备
 */
export const importDevices = (params: DeviceImportParams) => {
  const formData = new FormData()
  formData.append('file', params.file)
  formData.append('productId', params.productId.toString())
  if (params.batchName) {
    formData.append('batchName', params.batchName)
  }

  return post<DeviceImportResult>('/admin/devices/import', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// ==================== 批量操作相关 ====================

export interface BatchOperationRequest {
  operationType: BatchOperationType
  batchId?: number
  imeis?: string[]
  productId?: number
  imeiKeyword?: string
  status?: DeviceStatus
  importBatchId?: number
  tags?: string
  newBatchId?: number
}

export interface BatchOperationResult {
  totalCount: number
  successCount: number
  failedCount: number
  errors?: string[]
}

/**
 * 预估批量操作影响的设备数
 */
export const estimateBatchOperation = (params: BatchOperationRequest) => {
  return post<number>('/admin/devices/batch/estimate', params)
}

/**
 * 执行批量操作
 */
export const executeBatchOperation = (params: BatchOperationRequest) => {
  return post<BatchOperationResult>('/admin/devices/batch/execute', params)
}
