import { del, get, post, put } from '@/api/request'
import i18n from '@/locales'
import { getToken } from '@/utils/auth'

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
  internalVersion?: string
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
  internalVersion?: string
  packageStatus?: string
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
    timeout: 3600000,  // 固件上传接口超时时间设置为1小时（60 * 60 * 1000毫秒）
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

/**
 * 任务进度事件
 */
export interface TaskProgressEvent {
  taskId: string
  stage: 'INIT' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  percent: number
  message: string
}

/**
 * 任务状态
 */
export interface TaskStatus {
  id: string
  stage: 'INIT' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  percent: number
  message: string
  errorMsg?: string
  createdAt: string
  updatedAt: string
}

/**
 * 创建版本响应（发布模式）
 */
export interface PublishFirmwareVersionResponse {
  taskId: number
  versionId: number
}

/**
 * 发布固件版本（异步任务模式）
 */
export const publishFirmwareVersion = (payload: FirmwareVersionPayload) => {
  return post<PublishFirmwareVersionResponse>('/admin/firmware-versions/publish', payload, { timeout: 60 * 60 * 1000 })
}

export const updatePublishedFirmwareVersion = (id: number, payload: FirmwareVersionPayload) => {
  return put<PublishFirmwareVersionResponse>(`/admin/firmware-versions/${id}/publish`, payload, { timeout: 60 * 60 * 1000 })
}

/**
 * 获取任务状态（用于断线重连后恢复状态）
 */
export const getTaskStatus = (taskId: number) => {
  return get<TaskStatus>(`/tasks/${taskId}/status`)
}

/**
 * 创建任务进度 SSE 连接
 * @returns 返回 SSE 连接管理对象
 */
export function createTaskProgressStream() {
  let abortController: AbortController | null = null
  let reader: ReadableStreamDefaultReader<Uint8Array> | null = null

  const disconnect = () => {
    if (reader) {
      reader.cancel().catch(() => {})
      reader = null
    }
    if (abortController) {
      abortController.abort()
      abortController = null
    }
  }

  const isConnected = () => abortController !== null && !abortController.signal.aborted

  const connect = (
    taskId: number,
    handlers: {
      onmessage?: (event: TaskProgressEvent) => void
      onerror?: (error: Error) => void
      onopen?: () => void
      onclose?: () => void
    }
  ) => {
    disconnect()

    const token = getToken()
    const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
    const url = `${apiBaseUrl}/tasks/${taskId}/subscribe`

    abortController = new AbortController()

    fetch(url, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
      },
      signal: abortController.signal,
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`${i18n.global.t('firmware.sseConnectFailed')}: ${response.status} ${response.statusText}`)
        }

        handlers.onopen?.()

        reader = response.body?.getReader() || null
        if (!reader) {
          throw new Error(i18n.global.t('firmware.sseConnectFailed'))
        }
        const activeReader = reader

        const decoder = new TextDecoder()
        let buffer = ''
        let currentData = ''

        const read = async () => {
          try {
            while (true) {
              const { done, value } = await activeReader.read()
              if (done) {
                // 正常断开连接
                handlers.onclose?.()
                break
              }

              buffer += decoder.decode(value, { stream: true })

              // 处理 \n 和 \r\n 分隔符
              const lines = buffer.split(/\n|\r\n|\r/)
              buffer = lines.pop() || ''

              for (const line of lines) {
                // 去掉 \r 字符
                const trimmedLine = line.replace(/\r$/, '')
                if (trimmedLine === '') {
                  if (currentData) {
                    try {
                      const dataStr = currentData.replace(/^data:\s*/, '')
                      if (dataStr && dataStr !== '[DONE]') {
                        const event = JSON.parse(dataStr) as TaskProgressEvent
                        handlers.onmessage?.(event)
                      }
                    } catch {
                      // 忽略 JSON 解析错误
                    }
                    currentData = ''
                  }
                } else if (trimmedLine.startsWith('data:')) {
                  const data = trimmedLine.slice(5).trim()
                  if (currentData) {
                    currentData += '\n' + data
                  } else {
                    currentData = data
                  }
                } else if (trimmedLine.startsWith(':')) {
                  // 注释行，忽略
                  continue
                }
              }
            }
          } catch (e) {
            if (!abortController?.signal.aborted) {
              handlers.onerror?.(e as Error)
            }
          }
        }

        read()
      })
      .catch((error) => {
        if (!abortController?.signal.aborted) {
          handlers.onerror?.(error)
        }
      })

    return disconnect
  }

  return {
    connect,
    disconnect,
    isConnected,
  }
}

/**
 * CDN 预热结果
 */
export interface CdnWarmResponse {
  versionId: number
  triggered: boolean
  message: string
  messageKey?: string
  strategy?: string
  pop?: string
}

/**
 * 触发 CDN 预热（通过 Cloudflare Workers）
 * @param versionId 固件版本 ID
 */
export const warmCdn = (versionId: number) => {
  return get<CdnWarmResponse>(`/admin/firmware-versions/${versionId}/warm`, { timeout: 5 * 60 * 1000 })
}
