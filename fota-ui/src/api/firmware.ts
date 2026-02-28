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
  fileUrl?: string
  fileSize?: number
  md5?: string
  sha256?: string
  packageStatus?: string
  packageUploadedAt?: string
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
  uploadSessionId?: string
  fileUrl?: string
  fileSize?: number
  md5?: string
  sha256?: string
  tags?: string
  meta?: string
}

export interface UploadFirmwareResponse {
  sessionId: string
  status: string
  productId: number
  fileName: string
  fileSize: number
  md5: string
  sha256: string
  mime: string
}

export interface UploadSessionDetail {
  sessionId: string
  status: string
  productId: number
  version?: string
  fileName: string
  fileSize: number
  md5: string
  sha256: string
  mime: string
  objectKey?: string
  createdAt: string
  updatedAt: string
  lastError?: string
}

export const uploadFirmwarePackage = (
  file: File,
  productId: number,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal
) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('productId', String(productId))

  return post<UploadFirmwareResponse>('/admin/firmware-uploads', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    signal,  // 传递AbortSignal以支持取消上传
    onUploadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        onProgress(percent)
      }
    },
  })
}

export const getUploadSession = (sessionId: string) => {
  return get<UploadSessionDetail>(`/admin/firmware-uploads/${sessionId}`)
}

export const cancelUploadSession = (sessionId: string) => {
  return del<void>(`/admin/firmware-uploads/${sessionId}`)
}

export const attachPackageToVersion = (versionId: number, uploadSessionId: string) => {
  return post<FirmwareVersionItem>(`/admin/firmware-versions/${versionId}/attach-package`, {
    uploadSessionId,
  })
}

export const pageFirmwareVersions = (params: Record<string, unknown>) => {
  return get<PageResult<FirmwareVersionItem>>('/admin/firmware-versions', { params })
}

export const getFirmwareVersionsByProduct = (productId: number, readyOnly = false) => {
  return get<FirmwareVersionItem[]>(`/admin/firmware-versions/by-product/${productId}`, {
    params: { readyOnly },
  })
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
