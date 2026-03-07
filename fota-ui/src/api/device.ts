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
  versionParts?: DeviceVersionParts
  initialVersionParts?: DeviceVersionParts
  status: DeviceStatus
  firstSeenAt?: string
  lastSeenAt?: string
  tags?: Record<string, string>
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
  versionParts?: DeviceVersionParts
  status: DeviceStatus
  tags?: Record<string, string>
}

export interface DeviceVersionPart {
  versionId: number
  version?: string
  internalVersion?: string
  updatedAt?: string
}

export interface DeviceVersionParts {
  parts?: Record<string, DeviceVersionPart>
  primaryPart?: string
}

export interface DeviceImportParams {
  file?: File
  imeis?: string[]
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

export interface DeviceImportExecuteParams {
  productId: number
  batchName?: string
  sessionId?: string
  imeis?: string[]
  sourceFile?: string
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
 * 批量导入设备（文件方式）
 */
export const importDevices = (params: DeviceImportParams) => {
  if (!params.file) {
    throw new Error('文件不能为空')
  }
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

/**
 * 执行设备导入
 */
export const executeImportDevices = (params: DeviceImportExecuteParams) => {
  return post<DeviceImportResult>('/admin/devices/import/execute', params)
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
